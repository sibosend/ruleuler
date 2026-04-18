import React, { useState, useEffect, useMemo } from 'react';
import { Card, Select, Radio, Row, Col, Spin, DatePicker, Statistic, Table, Tag, Space } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, WarningOutlined } from '@ant-design/icons';
import { Line } from '@ant-design/charts';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import {
  fetchDailyTrend, fetchIntradayTrend, fetchRealtimeVariablesWithComparison,
  fetchRealtimeEnumDrift, type IntradayTrendPoint,
} from '../../api/monitoring';
import { transformToChartData, type ChartDataPoint } from './components/intradayChartUtils';

interface DateVariable {
  var_category: string;
  var_name: string;
  var_type: string;
  sample_count: number;
  missing_rate: number | null;
  mean: number | null;
  min_val_num: number | null;
  max_val_num: number | null;
  error_rate: number | null;
  alert_flags: string | null;
  dod_mean_pct: number | null;
  wow_mean_pct: number | null;
}

const ExecutionTrendPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string>();
  const [packageId, setPackageId] = useState<string>();
  const [days, setDays] = useState<7 | 14 | 30>(14);

  const [dailyTrend, setDailyTrend] = useState<any[]>([]);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [intradayTrend, setIntradayTrend] = useState<IntradayTrendPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [intradayLoading, setIntradayLoading] = useState(false);

  // 日期详情数据
  const [dateVariables, setDateVariables] = useState<DateVariable[]>([]);
  const [dateDriftMap, setDateDriftMap] = useState<Record<string, {
    enum_drift: boolean; top_value_changed: boolean; has_baseline?: boolean;
    currentTopValue?: string; baselineTopValue?: string;
  }>>({});
  const [dateDetailLoading, setDateDetailLoading] = useState(false);

  useEffect(() => {
    loadProjects().then((res) => {
      const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
      setProjects(list);
      if (list.length > 0) setProject(list[0]);
    });
  }, []);

  useEffect(() => {
    if (!project) return;
    listPackages(project).then((res: any) => {
      const list = res?.data?.data || res || [];
      setPackages(list);
      if (list.length > 0) setPackageId(list[0].id);
    });
  }, [project]);

  useEffect(() => {
    if (!project || !packageId) return;
    setLoading(true);
    fetchDailyTrend({ project, packageId, days })
      .then(setDailyTrend)
      .finally(() => setLoading(false));
  }, [project, packageId, days]);

  // 选日期后加载所有相关数据
  useEffect(() => {
    if (!project || !packageId || !selectedDate) return;

    setIntradayLoading(true);
    setDateDetailLoading(true);

    // 并行加载分时走势 + 变量分布 + 枚举漂移
    Promise.all([
      fetchIntradayTrend({ project, packageId, date: selectedDate })
        .then(setIntradayTrend)
        .catch(() => setIntradayTrend([])),
      fetchRealtimeVariablesWithComparison({ project, packageId, date: selectedDate })
        .then(d => setDateVariables(Array.isArray(d) ? d : []))
        .catch(() => setDateVariables([])),
      fetchRealtimeEnumDrift({ project, packageId, date: selectedDate })
        .then(d => {
          const m: typeof dateDriftMap = {};
          if (Array.isArray(d)) {
            for (const item of d) {
              m[`${item.varCategory}:${item.varName}`] = {
                enum_drift: item.enumDrift,
                top_value_changed: item.topValueChanged,
                has_baseline: item.hasBaseline !== false,
                currentTopValue: item.currentTopValue,
                baselineTopValue: item.baselineTopValue,
              };
            }
          }
          setDateDriftMap(m);
        })
        .catch(() => setDateDriftMap({})),
    ]).finally(() => {
      setIntradayLoading(false);
      setDateDetailLoading(false);
    });
  }, [project, packageId, selectedDate]);

  // 概览统计
  const daySummary = useMemo(() => {
    const targetPoints = intradayTrend.filter(d => d.day_type === 'target');
    if (targetPoints.length === 0) return null;

    const totalExec = targetPoints.reduce((s, d) => s + d.sample_count, 0);
    const totalError = targetPoints.reduce((s, d) => s + d.error_count, 0);
    const totalMissing = targetPoints.reduce((s, d) => s + d.missing_count, 0);

    let peakWindow = '';
    let peakCount = 0;
    for (const d of targetPoints) {
      if (d.sample_count > peakCount) {
        peakCount = d.sample_count;
        peakWindow = d.window_start;
      }
    }

    const prevPoints = intradayTrend.filter(d => d.day_type === 'previous');
    const prevTotalExec = prevPoints.reduce((s, d) => s + d.sample_count, 0);
    const dodChange = prevTotalExec > 0 ? ((totalExec - prevTotalExec) / prevTotalExec * 100) : null;

    return { totalExec, errorRate: totalExec > 0 ? (totalError / totalExec * 100) : 0, anomalyRate: totalExec > 0 ? (totalMissing / totalExec * 100) : 0, peakWindow, dodChange };
  }, [intradayTrend]);

  const execVolumeData = useMemo(
    () => dailyTrend.flatMap(d => [
      { date: d.stat_date, value: d.total_executions, type: t('monitoring.totalExecVolume') },
      { date: d.stat_date, value: d.missing_executions, type: t('monitoring.missingVolume') },
      { date: d.stat_date, value: d.error_executions, type: t('monitoring.errorVolume') },
    ]),
    [dailyTrend, i18n.language]
  );

  const rateTrendData = useMemo(
    () => dailyTrend.flatMap(d => [
      { date: d.stat_date, value: +(d.anomaly_rate * 100).toFixed(2), type: t('monitoring.anomalyRatePercent') },
      { date: d.stat_date, value: +(d.error_rate * 100).toFixed(2), type: t('monitoring.errorRatePercent') },
    ]),
    [dailyTrend, i18n.language]
  );

  const intradayChartData = useMemo(() => transformToChartData(intradayTrend), [intradayTrend]);
  const intradayExecData = useMemo(() => intradayChartData.filter(d => d.metric === t('monitoring.execVolume')), [intradayChartData, i18n.language]);
  const intradayRateData = useMemo(() => intradayChartData.filter(d => d.metric === t('monitoring.outlierRate') || d.metric === t('monitoring.errorRate')), [intradayChartData, i18n.language]);

  const commonConfig = {
    xField: 'date', yField: 'value', colorField: 'type', height: 250,
    legend: { position: 'top' as const }, smooth: false,
  };

  const intradayConfig = {
    xField: 'time', yField: 'value', colorField: 'type', height: 200,
    legend: { position: 'top' as const },
    lineStyle: (d: ChartDataPoint) => ({ lineDash: d.type === 'previous' ? [4, 4] : undefined }),
    smooth: false,
  };

  // 告警回放：从 driftMap 中筛选有告警的条目
  const alertReplay = useMemo(() => {
    const alerts: { varCategory: string; varName: string; type: string; detail: string }[] = [];
    for (const [key, drift] of Object.entries(dateDriftMap)) {
      const parts = key.split(':');
      const vc = parts[0] ?? '';
      const vn = parts.slice(1).join(':');
      if (drift.top_value_changed && drift.has_baseline) {
        alerts.push({ varCategory: vc, varName: vn, type: t('monitoring.topValueChange'), detail: `${drift.baselineTopValue} → ${drift.currentTopValue}` });
      }
      if (drift.enum_drift && drift.has_baseline) {
        alerts.push({ varCategory: vc, varName: vn, type: t('monitoring.distributionDrift'), detail: t('monitoring.freqDistChanged') });
      }
    }
    return alerts;
  }, [dateDriftMap, i18n.language]);

  const varColumns = useMemo(() => [
    { title: t('monitoring.category'), dataIndex: 'var_category', key: 'var_category', width: 100 },
    { title: t('monitoring.varName'), dataIndex: 'var_name', key: 'var_name', width: 140 },
    { title: t('monitoring.type'), dataIndex: 'var_type', key: 'var_type', width: 80 },
    { title: t('monitoring.requestCount'), dataIndex: 'sample_count', key: 'sample_count', width: 80 },
    { title: t('monitoring.missingRate'), dataIndex: 'missing_rate', key: 'missing_rate', width: 80,
      render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-' },
    { title: t('monitoring.mean'), dataIndex: 'mean', key: 'mean', width: 100,
      render: (v: number | null) => v != null ? v.toFixed(2) : '-' },
    { title: 'Min/Max', key: 'minmax', width: 120,
      render: (_: any, r: DateVariable) => (r.min_val_num != null && r.max_val_num != null)
        ? `${r.min_val_num} / ${r.max_val_num}` : '-' },
    { title: t('monitoring.errorRate'), dataIndex: 'error_rate', key: 'error_rate', width: 80,
      render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-' },
    { title: t('monitoring.dodMean'), dataIndex: 'dod_mean_pct', key: 'dod_mean_pct', width: 90,
      render: (v: number | null) => {
        if (v == null) return '-';
        const color = v > 0 ? '#cf1322' : v < 0 ? '#3f8600' : undefined;
        return <span style={{ color }}>{v >= 0 ? '+' : ''}{v.toFixed(1)}%</span>;
      }},
    { title: t('monitoring.alert'), dataIndex: 'alert_flags', key: 'alert_flags', width: 160,
      render: (v: string | null, r: DateVariable) => {
        const driftKey = `${r.var_category}:${r.var_name}`;
        const drift = dateDriftMap[driftKey];
        return (
          <Space size={4} wrap>
            {v && <Tag icon={<WarningOutlined />} color="error">{v}</Tag>}
            {drift && !drift.has_baseline && <Tag color="default">{t('monitoring.noBaseline')}</Tag>}
            {drift?.enum_drift && <Tag color="warning">{t('monitoring.distributionDrift')}</Tag>}
            {drift?.top_value_changed && <Tag color="error">{t('monitoring.topValueChange')}</Tag>}
            {!v && (!drift || (!drift.enum_drift && !drift.top_value_changed && drift.has_baseline !== false)) && '-'}
          </Space>
        );
      }},
  ], [dateDriftMap, i18n.language]);

  const alertColumns = useMemo(() => [
    { title: t('monitoring.category'), dataIndex: 'varCategory', width: 100 },
    { title: t('monitoring.varName'), dataIndex: 'varName', width: 140 },
    { title: t('monitoring.alertType'), dataIndex: 'type', width: 100,
      render: (v: string) => <Tag color={v === t('monitoring.topValueChange') ? 'error' : 'warning'}>{v}</Tag> },
    { title: t('monitoring.detail'), dataIndex: 'detail', width: 200 },
  ], [i18n.language]);

  return (
    <div style={{ padding: 24 }}>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16} align="middle">
          <Col>
            <Select style={{ width: 180 }} placeholder={t('common.selectProject')} value={project} onChange={setProject}
              options={projects.map(p => ({ label: p, value: p }))} />
          </Col>
          <Col>
            <Select style={{ width: 220 }} placeholder={t('common.selectPackage')} value={packageId} onChange={setPackageId}
              options={packages.map(p => ({ label: `${p.name} (${p.id})`, value: p.id }))} />
          </Col>
          <Col>
            <Radio.Group value={days} onChange={e => setDays(e.target.value)}>
              <Radio.Button value={7}>{t('monitoring.last7d')}</Radio.Button>
              <Radio.Button value={14}>{t('monitoring.last14d')}</Radio.Button>
              <Radio.Button value={30}>{t('monitoring.last30d')}</Radio.Button>
            </Radio.Group>
          </Col>
          <Col>
            <DatePicker
              placeholder={t('monitoring.selectDateDetail')}
              value={selectedDate ? dayjs(selectedDate) : null}
              onChange={(_, ds) => setSelectedDate(typeof ds === 'string' ? ds : null)}
              allowClear
            />
          </Col>
        </Row>
      </Card>

      <Spin spinning={loading}>
        <Row gutter={16}>
          <Col span={14}>
            <Card size="small" title={t('monitoring.dailyExecVolume')}>
              <Line
                data={execVolumeData}
                {...commonConfig}
                onReady={(chart) => {
                  chart.on('element:click', (e: any) => {
                    const data = e.data?.data;
                    if (data?.date) setSelectedDate(data.date);
                  });
                }}
              />
            </Card>
          </Col>
          <Col span={10}>
            <Card size="small" title={t('monitoring.dailyAnomalyRate')}>
              <Line data={rateTrendData} {...commonConfig} />
            </Card>
          </Col>
        </Row>
      </Spin>

      {selectedDate && (
        <Spin spinning={intradayLoading || dateDetailLoading}>
          {daySummary && (
            <Row gutter={16} style={{ marginTop: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title={t('monitoring.totalExec')}
                    value={daySummary.totalExec}
                    suffix={daySummary.dodChange != null ? (
                      <span style={{ fontSize: 14, color: daySummary.dodChange >= 0 ? '#cf1322' : '#3f8600' }}>
                        {daySummary.dodChange >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
                        {' ' + Math.abs(daySummary.dodChange).toFixed(1)}%
                      </span>
                    ) : null}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title={t('monitoring.errorRate')} value={daySummary.errorRate} precision={2} suffix="%" />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title={t('monitoring.anomalyMissingRate')} value={daySummary.anomalyRate} precision={2} suffix="%" />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic title={t('monitoring.peakWindow')} value={daySummary.peakWindow || '-'} />
                </Card>
              </Col>
            </Row>
          )}

          <Card size="small" title={`${selectedDate} ${t('monitoring.intradayTrend')}`} style={{ marginTop: 16 }}>
            <Row gutter={16}>
              <Col span={12}>
                <Card size="small" title={t('monitoring.execVolume')}>
                  <Line data={intradayExecData} {...intradayConfig} />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small" title={t('monitoring.anomalyErrorRate')}>
                  <Line data={intradayRateData} {...intradayConfig} colorField="metric" />
                </Card>
              </Col>
            </Row>
          </Card>

          {/* 变量分布表 */}
          {dateVariables.length > 0 && (
            <Card size="small" title={`${selectedDate} ${t('monitoring.varDistribution')}`} style={{ marginTop: 16 }}>
              <Table<DateVariable>
                columns={varColumns}
                dataSource={dateVariables}
                rowKey={r => `${r.var_category}:${r.var_name}`}
                size="small"
                pagination={false}
              />
            </Card>
          )}

          {/* 告警回放 */}
          {alertReplay.length > 0 && (
            <Card size="small" title={`${selectedDate} ${t('monitoring.alertReplay')}`} style={{ marginTop: 16 }}>
              <Table
                columns={alertColumns}
                dataSource={alertReplay}
                rowKey={r => `${r.varCategory}:${r.varName}:${r.type}`}
                size="small"
                pagination={false}
              />
            </Card>
          )}
        </Spin>
      )}
    </div>
  );
};

export default ExecutionTrendPage;

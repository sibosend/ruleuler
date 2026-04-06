import React, { useState, useEffect, useMemo } from 'react';
import { Card, Select, Radio, Row, Col, Spin } from 'antd';
import { Line } from '@ant-design/charts';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import { fetchDailyTrend, fetchIntradayTrend, type IntradayTrendPoint } from '../../api/monitoring';
import { transformToChartData, type ChartDataPoint } from './components/intradayChartUtils';

const ExecutionTrendPage: React.FC = () => {
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

  useEffect(() => {
    if (!project || !packageId || !selectedDate) return;
    setIntradayLoading(true);
    fetchIntradayTrend({ project, packageId, date: selectedDate })
      .then(setIntradayTrend)
      .finally(() => setIntradayLoading(false));
  }, [project, packageId, selectedDate]);

  const execVolumeData = useMemo(
    () => dailyTrend.flatMap(d => [
      { date: d.stat_date, value: d.total_executions, type: '总执行量' },
      { date: d.stat_date, value: d.missing_executions, type: '缺失量' },
      { date: d.stat_date, value: d.error_executions, type: '错误量' },
    ]),
    [dailyTrend]
  );

  const rateTrendData = useMemo(
    () => dailyTrend.flatMap(d => [
      { date: d.stat_date, value: +(d.anomaly_rate * 100).toFixed(2), type: '异常率(%)' },
      { date: d.stat_date, value: +(d.error_rate * 100).toFixed(2), type: '错误率(%)' },
    ]),
    [dailyTrend]
  );

  const intradayChartData = useMemo(
    () => transformToChartData(intradayTrend),
    [intradayTrend]
  );

  const intradayExecData = useMemo(
    () => intradayChartData.filter(d => d.metric === '执行量'),
    [intradayChartData]
  );

  const intradayRateData = useMemo(
    () => intradayChartData.filter(d => d.metric === '异常率' || d.metric === '错误率'),
    [intradayChartData]
  );

  const commonConfig = {
    xField: 'date',
    yField: 'value',
    colorField: 'type',
    height: 250,
    legend: { position: 'top' as const },
    smooth: false,
  };

  const intradayConfig = {
    xField: 'time',
    yField: 'value',
    colorField: 'type',
    height: 200,
    legend: { position: 'top' as const },
    lineStyle: (d: ChartDataPoint) => ({
      lineDash: d.type === 'previous' ? [4, 4] : undefined,
    }),
    smooth: false,
  };

  return (
    <div style={{ padding: 24 }}>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16} align="middle">
          <Col>
            <Select style={{ width: 180 }} placeholder="选择项目" value={project} onChange={setProject}
              options={projects.map(p => ({ label: p, value: p }))} />
          </Col>
          <Col>
            <Select style={{ width: 220 }} placeholder="选择知识包" value={packageId} onChange={setPackageId}
              options={packages.map(p => ({ label: `${p.name} (${p.id})`, value: p.id }))} />
          </Col>
          <Col>
            <Radio.Group value={days} onChange={e => setDays(e.target.value)}>
              <Radio.Button value={7}>近7天</Radio.Button>
              <Radio.Button value={14}>近14天</Radio.Button>
              <Radio.Button value={30}>近30天</Radio.Button>
            </Radio.Group>
          </Col>
        </Row>
      </Card>

      <Spin spinning={loading}>
        <Row gutter={16}>
          <Col span={14}>
            <Card size="small" title="日执行量走势">
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
            <Card size="small" title="日异常率/错误率走势">
              <Line data={rateTrendData} {...commonConfig} />
            </Card>
          </Col>
        </Row>
      </Spin>

      {selectedDate && (
        <Spin spinning={intradayLoading}>
          <Card size="small" title={`${selectedDate} 分时走势`} style={{ marginTop: 16 }}>
            <Row gutter={16}>
              <Col span={12}>
                <Card size="small" title="执行量">
                  <Line data={intradayExecData} {...intradayConfig} />
                </Card>
              </Col>
              <Col span={12}>
                <Card size="small" title="异常率/错误率">
                  <Line data={intradayRateData} {...intradayConfig} colorField="metric" />
                </Card>
              </Col>
            </Row>
          </Card>
        </Spin>
      )}
    </div>
  );
};

export default ExecutionTrendPage;
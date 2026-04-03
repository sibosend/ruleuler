import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Table, Select, Radio, Switch, DatePicker, Card, Row, Col, message, Space, Tag,
} from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import { Line } from '@ant-design/charts';
import dayjs, { type Dayjs } from 'dayjs';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import { fetchVariables, fetchTrend } from '../../api/monitoring';

const { RangePicker } = DatePicker;

interface Variable {
  var_category: string;
  var_name: string;
  var_type: string;
  sample_count: number;
  missing_rate: number | null;
  mean: number | null;
  std: number | null;
  alert_flags: string | null;
  days_since_last: number;
}

interface TrendPoint {
  stat_date: string;
  missing_rate: number | null;
  mean: number | null;
  std: number | null;
  skewness: number | null;
  outlier_rate: number | null;
  sample_count: number | null;
  alert_flags: string | null;
}

const METRIC_OPTIONS = [
  { label: '缺失率', value: 'missing_rate' },
  { label: '均值', value: 'mean' },
  { label: '标准差', value: 'std' },
  { label: '偏度', value: 'skewness' },
  { label: '异常率', value: 'outlier_rate' },
  { label: '样本数', value: 'sample_count' },
];

const defaultRange: [Dayjs, Dayjs] = [dayjs().subtract(30, 'day'), dayjs()];

const MonitoringPage: React.FC = () => {
  // --- filter state ---
  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string>();
  const [packageId, setPackageId] = useState<string>();
  const [ioType, setIoType] = useState<string>('input');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(defaultRange);
  const [showAll, setShowAll] = useState(false);

  // --- data state ---
  const [variables, setVariables] = useState<Variable[]>([]);
  const [varLoading, setVarLoading] = useState(false);
  const [selectedVar, setSelectedVar] = useState<Variable | null>(null);
  const [trendData, setTrendData] = useState<TrendPoint[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);
  const [trendMetric, setTrendMetric] = useState('missing_rate');

  const fetched = useRef(false);

  // --- load projects once ---
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    loadProjects()
      .then((res) => {
        const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
        setProjects(list);
      })
      .catch(() => message.error('加载项目列表失败'));
  }, []);

  // --- load packages when project changes ---
  const prevProject = useRef(project);
  useEffect(() => {
    if (prevProject.current === project) return;
    prevProject.current = project;
    setPackageId(undefined);
    setPackages([]);
    if (!project) return;
    listPackages(project)
      .then((res) => {
        const list = res.data?.data ?? [];
        setPackages(Array.isArray(list) ? list : []);
      })
      .catch(() => { /* silent */ });
  }, [project]);

  // --- load variables ---
  const loadVariables = useCallback(async () => {
    if (!project || !packageId) return;
    setVarLoading(true);
    try {
      const data = await fetchVariables({ project, packageId, ioType, showAll });
      setVariables(Array.isArray(data) ? data : []);
    } catch {
      message.error('加载变量列表失败');
    } finally {
      setVarLoading(false);
    }
  }, [project, packageId, ioType, showAll]);

  const prevFilterKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${ioType}|${showAll}`;
    if (prevFilterKey.current === key) return;
    prevFilterKey.current = key;
    setSelectedVar(null);
    setTrendData([]);
    loadVariables();
  }, [project, packageId, ioType, showAll, loadVariables]);

  // --- load trend when variable selected ---
  const loadTrend = useCallback(async (v: Variable) => {
    if (!project || !packageId) return;
    setTrendLoading(true);
    try {
      const data = await fetchTrend({
        project,
        packageId,
        varCategory: v.var_category,
        varName: v.var_name,
        ioType,
        startDate: dateRange[0].format('YYYY-MM-DD'),
        endDate: dateRange[1].format('YYYY-MM-DD'),
      });
      setTrendData(Array.isArray(data) ? data : []);
    } catch {
      message.error('加载趋势数据失败');
    } finally {
      setTrendLoading(false);
    }
  }, [project, packageId, ioType, dateRange]);

  const handleSelectVar = useCallback((v: Variable) => {
    setSelectedVar(v);
    loadTrend(v);
  }, [loadTrend]);

  // --- trend chart config ---
  const chartData = useMemo(() => {
    return trendData.map((p) => ({
      date: p.stat_date,
      value: p[trendMetric as keyof TrendPoint] as number ?? 0,
      alert: !!p.alert_flags,
    }));
  }, [trendData, trendMetric]);

  const alertDates = useMemo(() => {
    return new Set(trendData.filter((p) => p.alert_flags).map((p) => p.stat_date));
  }, [trendData]);

  const chartConfig = useMemo(() => ({
    data: chartData,
    xField: 'date',
    yField: 'value',
    height: 300,
    point: {
      shapeField: 'circle',
      sizeField: 4,
      style: (datum: { date: string }) => ({
        fill: alertDates.has(datum.date) ? '#ff4d4f' : '#1677ff',
        stroke: alertDates.has(datum.date) ? '#ff4d4f' : '#1677ff',
      }),
    },
    line: { style: { stroke: '#1677ff', lineWidth: 2 } },
    axis: {
      x: { title: '日期' },
      y: { title: METRIC_OPTIONS.find((m) => m.value === trendMetric)?.label ?? '' },
    },
  }), [chartData, alertDates, trendMetric]);

  // --- date range constraint: max 3 months ---
  const disabledDate = useCallback((current: Dayjs) => {
    return current.isAfter(dayjs()) || current.isBefore(dayjs().subtract(3, 'month'));
  }, []);

  // --- table columns ---
  const columns = useMemo(() => [
    { title: '类别', dataIndex: 'var_category', key: 'var_category', width: 140 },
    { title: '变量名', dataIndex: 'var_name', key: 'var_name', width: 180 },
    { title: '数据类型', dataIndex: 'var_type', key: 'var_type', width: 100 },
    {
      title: '样本数', dataIndex: 'sample_count', key: 'sample_count', width: 90,
      sorter: (a: Variable, b: Variable) => a.sample_count - b.sample_count,
    },
    {
      title: '缺失率', dataIndex: 'missing_rate', key: 'missing_rate', width: 90,
      render: (v: number | null) => v != null ? `${(v * 100).toFixed(1)}%` : '-',
      sorter: (a: Variable, b: Variable) => (a.missing_rate ?? 0) - (b.missing_rate ?? 0),
    },
    {
      title: '均值', dataIndex: 'mean', key: 'mean', width: 100,
      render: (v: number | null) => v != null ? v.toFixed(2) : '-',
    },
    {
      title: '标准差', dataIndex: 'std', key: 'std', width: 100,
      render: (v: number | null) => v != null ? v.toFixed(2) : '-',
    },
    {
      title: '告警', dataIndex: 'alert_flags', key: 'alert_flags', width: 120,
      render: (v: string | null) =>
        v ? <Tag icon={<WarningOutlined />} color="error">{v}</Tag> : '-',
    },
    {
      title: '最后更新', dataIndex: 'days_since_last', key: 'days_since_last', width: 100,
      render: (v: number) => (
        <span style={{ color: v > 7 ? '#999' : undefined }}>
          {v === 0 ? '今天' : `${v}天前`}
        </span>
      ),
      sorter: (a: Variable, b: Variable) => a.days_since_last - b.days_since_last,
    },
  ], []);

  return (
    <div>
      {/* 筛选器 */}
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 180 }}
          placeholder="选择项目"
          allowClear
          value={project}
          onChange={setProject}
          options={projects.map((p) => ({ label: p, value: p }))}
        />
        <Select
          style={{ width: 220 }}
          placeholder="选择知识包"
          allowClear
          value={packageId}
          onChange={setPackageId}
          disabled={!project}
          options={packages.map((p) => ({ label: `${p.name} (${p.id})`, value: p.id }))}
        />
        <Radio.Group value={ioType} onChange={(e) => setIoType(e.target.value)}>
          <Radio.Button value="input">输入</Radio.Button>
          <Radio.Button value="output">输出</Radio.Button>
        </Radio.Group>
        <RangePicker
          value={dateRange}
          onChange={(v) => {
            if (v && v[0] && v[1]) setDateRange([v[0], v[1]]);
          }}
          disabledDate={disabledDate}
        />
        <Space>
          <span>显示全部</span>
          <Switch checked={showAll} onChange={setShowAll} size="small" />
        </Space>
      </Space>

      <Row gutter={12}>
        {/* 变量列表 */}
        <Col span={selectedVar ? 14 : 24}>
          <Card size="small" title="变量列表">
            <Table
              columns={columns}
              dataSource={variables}
              rowKey={(r) => `${r.var_category}:${r.var_name}`}
              loading={varLoading}
              size="small"
              pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 个变量` }}
              rowClassName={(r) => (r.days_since_last > 7 ? 'inactive-var-row' : '')}
              onRow={(r) => ({
                onClick: () => handleSelectVar(r),
                style: { cursor: 'pointer' },
              })}
              rowSelection={{
                type: 'radio',
                selectedRowKeys: selectedVar
                  ? [`${selectedVar.var_category}:${selectedVar.var_name}`]
                  : [],
                onChange: (_, rows) => { if (rows[0]) handleSelectVar(rows[0]); },
              }}
            />
          </Card>
        </Col>

        {/* 趋势图 */}
        {selectedVar && (
          <Col span={10}>
            <Card
              size="small"
              title={`趋势：${selectedVar.var_category}.${selectedVar.var_name}`}
              extra={
                <Select
                  size="small"
                  value={trendMetric}
                  onChange={setTrendMetric}
                  style={{ width: 100 }}
                  options={METRIC_OPTIONS}
                />
              }
              loading={trendLoading}
            >
              {chartData.length > 0 ? (
                <Line {...chartConfig} />
              ) : (
                <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                  暂无趋势数据
                </div>
              )}
            </Card>
          </Col>
        )}
      </Row>

      {/* 下线变量灰色样式 */}
      <style>{`
        .inactive-var-row td { color: #999 !important; background: #fafafa !important; }
      `}</style>
    </div>
  );
};

export default MonitoringPage;

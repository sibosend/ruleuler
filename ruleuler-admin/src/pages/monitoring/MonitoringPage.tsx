import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Table, Select, Radio, Switch, Card, Row, Col, message, Space, Tag, Statistic, Tooltip,
  Drawer, Tabs, Button, Descriptions,
} from 'antd';
import { WarningOutlined, ArrowUpOutlined, ArrowDownOutlined, DownOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import {
  fetchRealtimeVariables, fetchRealtimeDashboard, fetchRealtimeMissingRateTrend,
  fetchRealtimeVariablesWithComparison, fetchRealtimePsi, fetchRealtimeEnumDrift,
  fetchAnomalyRecords, fetchVersionCompare, fetchDailyTrend,
} from '../../api/monitoring';
import { Line } from '@ant-design/charts';

interface Variable {
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
  dod_missing_rate_pct: number | null;
  wow_mean_pct: number | null;
  wow_missing_rate_pct: number | null;
  yesterday_mean: number | null;
  last_week_mean: number | null;
  psi?: number | null;
}

interface AnomalyRecord {
  execution_id: string;
  created_at: string;
  val_num: number | null;
  val_str: string | null;
  anomaly_type: string;
}

const MonitoringPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // --- filter state ---
  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string | undefined>(() => searchParams.get('project') || undefined);
  const [packageId, setPackageId] = useState<string | undefined>(() => searchParams.get('packageId') || undefined);
  const [ioType, setIoType] = useState<string>('input');

  // --- realtime state ---
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());
  const [sortBy, setSortBy] = useState<string>('anomaly');

  // --- data state ---
  const [dashboard, setDashboard] = useState<any>(null);
  const [variables, setVariables] = useState<Variable[]>([]);
  const [varLoading, setVarLoading] = useState(false);
  const [dailyTrend, setDailyTrend] = useState<any[]>([]);

  // --- drawer state ---
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedVar, setSelectedVar] = useState<Variable | null>(null);
  const [trendData, setTrendData] = useState<any[]>([]);
  const [psiCache, setPsiCache] = useState<Record<string, number | null>>({});
  const [enumDriftMap, setEnumDriftMap] = useState<Record<string, { enum_drift: boolean; top_value_changed: boolean }>>({});

  // --- anomaly records state ---
  const [anomalyRecords, setAnomalyRecords] = useState<AnomalyRecord[]>([]);
  const [anomalyTotal, setAnomalyTotal] = useState(0);
  const [anomalyPage, setAnomalyPage] = useState(1);
  const [anomalyLoading, setAnomalyLoading] = useState(false);

  // --- version compare state ---
  const [versionA, setVersionA] = useState<string>();
  const [versionB, setVersionB] = useState<string>();
  const [versionCompareData, setVersionCompareData] = useState<any[]>([]);
  const [versionLoading, setVersionLoading] = useState(false);

  // --- variable folding ---
  const [showAllVars, setShowAllVars] = useState(false);

  const fetched = useRef(false);

  // --- load projects once ---
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    loadProjects()
      .then((res) => {
        const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
        setProjects(list);
        // URL没指定project时默认选第一个
        if (!project && list.length > 0) setProject(list[0]);
      })
      .catch(() => message.error('加载项目列表失败'));
  }, []);

  // --- load packages when project changes ---
  const prevProject = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (prevProject.current === project) return;
    prevProject.current = project;
    setPackages([]);
    if (!project) return;
    listPackages(project)
      .then((res) => {
        const list = res.data?.data ?? [];
        const pkgList = Array.isArray(list) ? list : [];
        setPackages(pkgList);
        if (pkgList.length > 0) {
          const urlPkgId = searchParams.get('packageId');
          const found = urlPkgId && pkgList.some(p => p.id === urlPkgId);
          setPackageId(found ? urlPkgId : pkgList[0].id);
        } else {
          setPackageId(undefined);
        }
      })
      .catch(() => { /* silent */ });
  }, [project]);

  // --- sync project/packageId to URL ---
  useEffect(() => {
    const params = new URLSearchParams();
    if (project) params.set('project', project);
    if (packageId) params.set('packageId', packageId);
    setSearchParams(params, { replace: true });
  }, [project, packageId, setSearchParams]);

  // --- load real-time data ---
  const loadData = useCallback(async (silent = false) => {
    if (!project || !packageId) return;
    if (!silent) setVarLoading(true);
    try {
      const [dashRet, varsRet, driftRet, trendRet] = await Promise.all([
        fetchRealtimeDashboard({ project, packageId }),
        fetchRealtimeVariablesWithComparison({ project, packageId, ioType, sortBy })
          .catch(() => fetchRealtimeVariables({ project, packageId, ioType, sortBy })),
        fetchRealtimeEnumDrift({ project, packageId, ioType }).catch(() => []),
        fetchDailyTrend({ project, packageId, days: 14 }).catch(() => []),
      ]);
      setDashboard(dashRet);
      setVariables(Array.isArray(varsRet) ? varsRet : []);
      setDailyTrend(Array.isArray(trendRet) ? trendRet : []);
      setLastRefresh(new Date());

      const driftMap: Record<string, { enum_drift: boolean; top_value_changed: boolean }> = {};
      if (Array.isArray(driftRet)) {
        for (const d of driftRet) {
          driftMap[`${d.varCategory}:${d.varName}`] = {
            enum_drift: d.enumDrift,
            top_value_changed: d.topValueChanged,
          };
        }
      }
      setEnumDriftMap(driftMap);

      if (selectedVar) {
        const trData = await fetchRealtimeMissingRateTrend({
          project, packageId, varCategory: selectedVar.var_category, varName: selectedVar.var_name, ioType,
        });
        setTrendData(trData || []);
      }
    } catch {
      if (!silent) message.error('加载实时数据失败');
    } finally {
      if (!silent) setVarLoading(false);
    }
  }, [project, packageId, ioType, selectedVar, sortBy]);

  // Initial load when filters change
  const prevFilterKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${ioType}`;
    if (prevFilterKey.current === key) return;
    prevFilterKey.current = key;
    setSelectedVar(null);
    setDrawerOpen(false);
    setTrendData([]);
    setPsiCache({});
    setEnumDriftMap({});
    setShowAllVars(false);
    loadData();
  }, [project, packageId, ioType, loadData]);

  // Auto-refresh interval
  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(() => { loadData(true); }, 60000);
    return () => clearInterval(timer);
  }, [autoRefresh, loadData]);

  // --- fetch anomaly records ---
  const loadAnomalyRecords = useCallback(async (v: Variable, page: number) => {
    if (!project || !packageId) return;
    setAnomalyLoading(true);
    try {
      const data = await fetchAnomalyRecords({
        project, packageId, varCategory: v.var_category, varName: v.var_name, ioType,
        anomalyType: 'missing', page, pageSize: 20,
      });
      setAnomalyRecords(data?.records || []);
      setAnomalyTotal(data?.total || 0);
    } catch { /* silent */ }
    finally { setAnomalyLoading(false); }
  }, [project, packageId, ioType]);

  // --- handle variable selection (open drawer) ---
  const handleSelectVar = useCallback(async (v: Variable) => {
    setSelectedVar(v);
    setDrawerOpen(true);
    setAnomalyPage(1);
    setVersionA(undefined);
    setVersionB(undefined);
    setVersionCompareData([]);

    if (!project || !packageId) return;

    // Fetch trend + anomaly records in parallel
    try {
      const [trData] = await Promise.all([
        fetchRealtimeMissingRateTrend({
          project, packageId, varCategory: v.var_category, varName: v.var_name, ioType,
        }),
        loadAnomalyRecords(v, 1),
      ]);
      setTrendData(Array.isArray(trData) ? trData : []);
    } catch {
      message.error('加载趋势失败');
    }

    // Fetch PSI (numeric only)
    const psiKey = `${v.var_category}:${v.var_name}`;
    if (psiCache[psiKey] !== undefined) return;
    const categoricalTypes = ['String', 'Char', 'Enum'];
    if (categoricalTypes.includes(v.var_type)) {
      setPsiCache(prev => ({ ...prev, [psiKey]: null }));
      return;
    }
    try {
      const psiData = await fetchRealtimePsi({
        project, packageId, varCategory: v.var_category, varName: v.var_name, ioType,
      });
      setPsiCache(prev => ({ ...prev, [psiKey]: psiData?.psi ?? null }));
    } catch { /* silent */ }
  }, [project, packageId, ioType, psiCache, loadAnomalyRecords]);

  // --- handle version compare ---
  const handleVersionCompare = useCallback(async () => {
    if (!project || !packageId || !versionA || !versionB) return;
    setVersionLoading(true);
    try {
      const data = await fetchVersionCompare({ project, packageId, versionA, versionB });
      setVersionCompareData(Array.isArray(data) ? data : []);
    } catch {
      message.error('版本对比失败');
    } finally {
      setVersionLoading(false);
    }
  }, [project, packageId, versionA, versionB]);

  // --- close drawer ---
  const handleCloseDrawer = useCallback(() => {
    setDrawerOpen(false);
    setSelectedVar(null);
    setTrendData([]);
    setAnomalyRecords([]);
    setAnomalyTotal(0);
    setVersionCompareData([]);
  }, []);

  // --- trend chart config ---
  const chartData = useMemo(() => {
    return trendData.map((p) => ({
      time: p.window_start,
      missing_rate: p.missing_rate,
      spike: p.spike,
    }));
  }, [trendData]);

  const spikeTimes = useMemo(() => {
    return new Set(trendData.filter((p) => p.spike).map((p) => p.window_start));
  }, [trendData]);

  const chartConfig = useMemo(() => ({
    data: chartData,
    xField: 'time',
    yField: 'missing_rate',
    height: 260,
    point: {
      shapeField: 'circle',
      sizeField: 4,
      style: (datum: { time: string }) => ({
        fill: spikeTimes.has(datum.time) ? '#ff4d4f' : '#1677ff',
        stroke: spikeTimes.has(datum.time) ? '#ff4d4f' : '#1677ff',
      }),
    },
    line: { style: { stroke: '#1677ff', lineWidth: 2 } },
    axis: { x: { title: '时间 (5m窗口)' }, y: { title: '缺失率' } },
  }), [chartData, spikeTimes]);

  // --- variable folding: show first 20 anomaly vars, rest collapsed ---

  // --- daily trend chart data ---
  const execVolumeData = useMemo(() => {
    return dailyTrend.flatMap(d => [
      { date: d.stat_date, value: d.total_executions, type: '总执行量' },
      { date: d.stat_date, value: d.missing_executions, type: '缺失量' },
      { date: d.stat_date, value: d.error_executions, type: '错误量' },
    ]);
  }, [dailyTrend]);

  const rateTrendData = useMemo(() => {
    return dailyTrend.flatMap(d => [
      { date: d.stat_date, value: +(d.anomaly_rate * 100).toFixed(2), type: '异常率(%)' },
      { date: d.stat_date, value: +(d.error_rate * 100).toFixed(2), type: '错误率(%)' },
    ]);
  }, [dailyTrend]);
  const displayedVariables = useMemo(() => {
    if (showAllVars) return variables;
    return variables.slice(0, 20);
  }, [variables, showAllVars]);

  // --- helpers ---
  const renderDodInfo = (todayVal: number, yesterdayVal: number, isPercent = false) => {
    if (yesterdayVal === 0) return <span style={{ color: '#999' }}>昨日: {yesterdayVal}</span>;
    const diff = todayVal - yesterdayVal;
    const dod = yesterdayVal > 0 ? (diff / yesterdayVal) * 100 : 0;

    if (isPercent) {
      const diffPoints = (todayVal - yesterdayVal) * 100;
      const isBad = diffPoints > 0;
      return (
        <Space>
          <span>昨日: {(yesterdayVal * 100).toFixed(2)}%</span>
          <span style={{ color: isBad ? '#cf1322' : '#3f8600' }}>
            {diffPoints > 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(diffPoints).toFixed(2)}%
          </span>
        </Space>
      );
    }

    return (
      <Space>
        <span>昨日: {yesterdayVal}</span>
        <span style={{ color: diff > 0 ? '#3f8600' : '#cf1322' }}>
          {diff > 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(dod).toFixed(2)}%
        </span>
      </Space>
    );
  };

  const renderDodArrow = (
    dodPct: number | null, wowPct: number | null,
    baselineLabel: string, baselineValue: number | null, isBadWhenUp: boolean,
  ) => {
    if (dodPct == null) return null;
    const isUp = dodPct > 0;
    const color = isBadWhenUp ? (isUp ? '#cf1322' : '#3f8600') : '#888';
    const arrow = isUp ? <ArrowUpOutlined /> : <ArrowDownOutlined />;
    const tooltipContent = (
      <div>
        <div>WoW: {wowPct != null ? `${wowPct > 0 ? '+' : ''}${wowPct.toFixed(1)}%` : '-'}</div>
        <div>{baselineLabel}: {baselineValue != null ? baselineValue.toFixed(2) : '-'}</div>
      </div>
    );
    return (
      <Tooltip title={tooltipContent}>
        <span style={{ color, marginLeft: 4, cursor: 'pointer', whiteSpace: 'nowrap' }}>
          ({arrow} {Math.abs(dodPct).toFixed(1)}%)
        </span>
      </Tooltip>
    );
  };

  // --- table columns ---
  const columns = useMemo(() => [
    { title: '类别', dataIndex: 'var_category', key: 'var_category', width: 140 },
    { title: '变量名', dataIndex: 'var_name', key: 'var_name', width: 180 },
    { title: '类型', dataIndex: 'var_type', key: 'var_type', width: 80 },
    {
      title: '请求量', dataIndex: 'sample_count', key: 'sample_count', width: 90,
      sorter: true,
      sortOrder: sortBy === 'sample_count' ? 'descend' as const : undefined,
    },
    {
      title: '缺失率', dataIndex: 'missing_rate', key: 'missing_rate', width: 130,
      sorter: true,
      sortOrder: sortBy === 'missing_rate' ? 'descend' as const : undefined,
      render: (_: number | null, r: Variable) => (
        <span>
          <span style={{ color: r.missing_rate != null && r.missing_rate > 0.05 ? '#ff4d4f' : 'inherit' }}>
            {r.missing_rate != null ? `${(r.missing_rate * 100).toFixed(1)}%` : '-'}
          </span>
          {renderDodArrow(r.dod_missing_rate_pct, r.wow_missing_rate_pct, '上周缺失率', null, true)}
        </span>
      ),
    },
    {
      title: '均值', dataIndex: 'mean', key: 'mean', width: 140,
      render: (_: number | null, r: Variable) => (
        <span>
          {r.mean != null ? r.mean.toFixed(2) : '-'}
          {renderDodArrow(r.dod_mean_pct, r.wow_mean_pct, '昨日均值', r.yesterday_mean, false)}
        </span>
      ),
    },
    {
      title: 'Min / Max', key: 'minMax', width: 120,
      render: (r: Variable) => {
        if (r.min_val_num == null && r.max_val_num == null) return '-';
        return `${r.min_val_num ?? '-'} / ${r.max_val_num ?? '-'}`;
      },
    },
    {
      title: 'PSI', dataIndex: 'psi', key: 'psi', width: 80,
      render: (_: any, r: Variable) => {
        const categoricalTypes = ['String', 'Char', 'Enum'];
        if (categoricalTypes.includes(r.var_type)) return <span style={{ color: '#999' }}>-</span>;
        const key = `${r.var_category}:${r.var_name}`;
        const psiVal = psiCache[key];
        if (psiVal === undefined) return <span style={{ color: '#999' }}>-</span>;
        if (psiVal === null) return <span style={{ color: '#999' }}>N/A</span>;
        let color = 'inherit';
        if (psiVal > 0.2) color = '#ff4d4f';
        else if (psiVal > 0.1) color = '#faad14';
        return <span style={{ color, fontWeight: psiVal > 0.1 ? 'bold' : 'normal' }}>{psiVal.toFixed(3)}</span>;
      },
    },
    {
      title: '错误率', dataIndex: 'error_rate', key: 'error_rate', width: 90,
      sorter: true,
      sortOrder: sortBy === 'error_rate' ? 'descend' as const : undefined,
      render: (v: number | null) => (
        <span style={{ color: v && v > 0.01 ? '#ff4d4f' : 'inherit' }}>
          {v != null ? `${(v * 100).toFixed(1)}%` : '-'}
        </span>
      ),
    },
    {
      title: '告警', dataIndex: 'alert_flags', key: 'alert_flags', width: 160,
      sorter: true,
      sortOrder: sortBy === 'anomaly' ? 'descend' as const : undefined,
      render: (v: string | null, r: Variable) => {
        const driftKey = `${r.var_category}:${r.var_name}`;
        const drift = enumDriftMap[driftKey];
        return (
          <Space size={4} wrap>
            {v && <Tag icon={<WarningOutlined />} color="error">{v}</Tag>}
            {drift?.enum_drift && <Tag color="warning">分布偏移</Tag>}
            {drift?.top_value_changed && <Tag color="error">主值变更</Tag>}
            {!v && !drift?.enum_drift && !drift?.top_value_changed && '-'}
          </Space>
        );
      },
    },
  ], [psiCache, enumDriftMap, sortBy]);

  // --- anomaly records columns ---
  const anomalyColumns = useMemo(() => [
    {
      title: '执行ID', dataIndex: 'execution_id', key: 'execution_id', width: 200,
      render: (id: string) => (
        <a onClick={() => navigate(`/monitoring/executions/${id}`)}>{id}</a>
      ),
    },
    { title: '执行时间', dataIndex: 'created_at', key: 'created_at', width: 180 },
    {
      title: '变量值', key: 'value', width: 120,
      render: (_: any, r: AnomalyRecord) => r.val_num != null ? r.val_num : (r.val_str ?? '-'),
    },
    {
      title: '异常类型', dataIndex: 'anomaly_type', key: 'anomaly_type', width: 100,
      render: (v: string) => <Tag color="error">{v}</Tag>,
    },
  ], [navigate]);

  // --- version compare columns ---
  const versionCompareColumns = useMemo(() => [
    { title: '类别', dataIndex: 'varCategory', key: 'varCategory', width: 120 },
    { title: '变量名', dataIndex: 'varName', key: 'varName', width: 150 },
    {
      title: `版本A`, key: 'periodA', width: 180,
      render: (_: any, r: any) => r.periodA ? (
        <div>
          <div>缺失率: {r.periodA.missing_rate != null ? `${(r.periodA.missing_rate * 100).toFixed(1)}%` : '-'}</div>
          <div>均值: {r.periodA.mean != null ? r.periodA.mean.toFixed(2) : '-'}</div>
        </div>
      ) : '-',
    },
    {
      title: `版本B`, key: 'periodB', width: 180,
      render: (_: any, r: any) => r.periodB ? (
        <div>
          <div>缺失率: {r.periodB.missing_rate != null ? `${(r.periodB.missing_rate * 100).toFixed(1)}%` : '-'}</div>
          <div>均值: {r.periodB.mean != null ? r.periodB.mean.toFixed(2) : '-'}</div>
        </div>
      ) : '-',
    },
  ], []);

  // --- PSI info for selected var ---
  const selectedPsiVal = useMemo(() => {
    if (!selectedVar) return undefined;
    const key = `${selectedVar.var_category}:${selectedVar.var_name}`;
    return psiCache[key];
  }, [selectedVar, psiCache]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* 顶部工具栏 */}
      <Card size="small" bodyStyle={{ padding: '12px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space wrap>
            <Select style={{ width: 180 }} placeholder="选择项目" value={project} onChange={setProject}
              options={projects.map((p) => ({ label: p, value: p }))} allowClear />
            <Select style={{ width: 220 }} placeholder="选择知识包" value={packageId} onChange={setPackageId} disabled={!project}
              options={packages.map((p) => ({ label: `${p.name} (${p.id})`, value: p.id }))} allowClear />
            <Radio.Group value={ioType} onChange={(e) => setIoType(e.target.value)}>
              <Radio.Button value="input">输入</Radio.Button>
              <Radio.Button value="output">输出</Radio.Button>
            </Radio.Group>
          </Space>
          <Space>
            <span style={{ color: '#999', fontSize: 13 }}>最新更新: {lastRefresh.toLocaleTimeString()}</span>
            <span style={{ fontSize: 13 }}>自动刷新</span>
            <Switch checked={autoRefresh} onChange={setAutoRefresh} size="small" />
          </Space>
        </div>
      </Card>

      {/* 大盘卡片 */}
      <Row gutter={16}>
        <Col span={8}>
          <Card size="small">
            <Statistic title="当日总执行量" value={dashboard?.today?.total_executions || 0} precision={0} />
            <div style={{ marginTop: 8, fontSize: 13 }}>
              {renderDodInfo(dashboard?.today?.total_executions || 0, dashboard?.yesterday?.total_executions || 0, false)}
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic title="异常率" value={(dashboard?.today?.anomaly_rate || 0) * 100} precision={2} suffix="%" />
            <div style={{ marginTop: 8, fontSize: 13 }}>
              {renderDodInfo(dashboard?.today?.anomaly_rate || 0, dashboard?.yesterday?.anomaly_rate || 0, true)}
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic title="错误率" value={(dashboard?.today?.error_rate || 0) * 100} precision={2} suffix="%" />
            <div style={{ marginTop: 8, fontSize: 13 }}>
              {renderDodInfo(dashboard?.today?.error_rate || 0, dashboard?.yesterday?.error_rate || 0, true)}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 近14日走势图 */}
      {dailyTrend.length > 0 && (
        <Row gutter={16}>
          <Col span={14}>
            <Card size="small" title="近14日执行量走势">
              <Line data={execVolumeData} xField="date" yField="value" colorField="type" height={220} />
            </Card>
          </Col>
          <Col span={10}>
            <Card size="small" title="近14日异常率/错误率走势">
              <Line data={rateTrendData} xField="date" yField="value" colorField="type" height={220} />
            </Card>
          </Col>
        </Row>
      )}

      {/* 中间漂移表 — 抽屉打开时压缩为 60% */}
      <div style={{ width: drawerOpen ? '60%' : '100%', transition: 'width 0.3s ease' }}>
        <Card size="small" title="实时变量分布 (自动排序：异常优先)">
          <Table
            columns={columns}
            dataSource={displayedVariables}
            rowKey={(r) => `${r.var_category}:${r.var_name}`}
            loading={varLoading}
            size="small"
            pagination={false}
            onRow={(r) => ({
              onClick: () => handleSelectVar(r),
              style: { cursor: 'pointer', background: r.alert_flags ? '#fff1f0' : 'inherit' },
            })}
            onChange={(_, __, sorter: any) => {
              if (sorter && sorter.field) {
                if (sorter.field === 'alert_flags') setSortBy('anomaly');
                else setSortBy(sorter.field);
              } else {
                setSortBy('sample_count');
              }
            }}
            rowSelection={{
              type: 'radio',
              selectedRowKeys: selectedVar
                ? [`${selectedVar.var_category}:${selectedVar.var_name}`]
                : [],
              onChange: (_, rows) => { if (rows[0]) handleSelectVar(rows[0]); },
            }}
          />
          {!showAllVars && variables.length > 20 && (
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Button type="link" icon={<DownOutlined />} onClick={() => setShowAllVars(true)}>
                展开全部 ({variables.length} 个变量)
              </Button>
            </div>
          )}
          {showAllVars && variables.length > 20 && (
            <div style={{ textAlign: 'center', marginTop: 8 }}>
              <Button type="link" onClick={() => setShowAllVars(false)}>
                收起 (仅显示前 20 个)
              </Button>
            </div>
          )}
        </Card>
      </div>

      {/* 右侧下钻抽屉 */}
      <Drawer
        title={selectedVar ? `${selectedVar.var_category}.${selectedVar.var_name}` : '变量详情'}
        placement="right"
        width="40%"
        open={drawerOpen}
        onClose={handleCloseDrawer}
        destroyOnClose={false}
      >
        {selectedVar && (
          <Tabs
            defaultActiveKey="trend"
            items={[
              {
                key: 'trend',
                label: '趋势',
                children: (
                  <div>
                    {/* 变量基本信息 */}
                    <Descriptions size="small" column={2} style={{ marginBottom: 16 }}>
                      <Descriptions.Item label="类型">{selectedVar.var_type}</Descriptions.Item>
                      <Descriptions.Item label="请求量">{selectedVar.sample_count}</Descriptions.Item>
                      <Descriptions.Item label="缺失率">
                        {selectedVar.missing_rate != null ? `${(selectedVar.missing_rate * 100).toFixed(1)}%` : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="均值">
                        {selectedVar.mean != null ? selectedVar.mean.toFixed(2) : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="Min / Max">
                        {selectedVar.min_val_num ?? '-'} / {selectedVar.max_val_num ?? '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="PSI">
                        {selectedPsiVal != null ? (
                          <span style={{
                            color: selectedPsiVal > 0.2 ? '#ff4d4f' : selectedPsiVal > 0.1 ? '#faad14' : 'inherit',
                            fontWeight: selectedPsiVal > 0.1 ? 'bold' : 'normal',
                          }}>
                            {selectedPsiVal.toFixed(3)}
                          </span>
                        ) : '-'}
                      </Descriptions.Item>
                    </Descriptions>

                    {/* 趋势曲线 */}
                    {chartData.length > 0 ? (
                      <Line {...chartConfig} />
                    ) : (
                      <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>暂无趋势数据</div>
                    )}
                  </div>
                ),
              },
              {
                key: 'anomaly',
                label: '异常记录',
                children: (
                  <Table
                    columns={anomalyColumns}
                    dataSource={anomalyRecords}
                    rowKey="execution_id"
                    size="small"
                    loading={anomalyLoading}
                    pagination={{
                      current: anomalyPage,
                      pageSize: 20,
                      total: anomalyTotal,
                      showTotal: (t) => `共 ${t} 条`,
                      onChange: (page) => {
                        setAnomalyPage(page);
                        if (selectedVar) loadAnomalyRecords(selectedVar, page);
                      },
                    }}
                  />
                ),
              },
              {
                key: 'version',
                label: '版本对比',
                children: (
                  <div>
                    <Space style={{ marginBottom: 16 }} wrap>
                      <Select
                        style={{ width: 160 }}
                        placeholder="版本A"
                        value={versionA}
                        onChange={setVersionA}
                        allowClear
                        options={packages.map((p) => ({ label: p.name, value: p.id }))}
                      />
                      <Select
                        style={{ width: 160 }}
                        placeholder="版本B"
                        value={versionB}
                        onChange={setVersionB}
                        allowClear
                        options={packages.map((p) => ({ label: p.name, value: p.id }))}
                      />
                      <Button
                        type="primary"
                        onClick={handleVersionCompare}
                        disabled={!versionA || !versionB}
                        loading={versionLoading}
                      >
                        对比
                      </Button>
                    </Space>
                    {versionCompareData.length > 0 && (
                      <Table
                        columns={versionCompareColumns}
                        dataSource={versionCompareData}
                        rowKey={(r) => `${r.varCategory}:${r.varName}`}
                        size="small"
                        pagination={{ pageSize: 10 }}
                      />
                    )}
                    {versionCompareData.length === 0 && !versionLoading && (
                      <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
                        选择两个版本后点击"对比"查看结果
                      </div>
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
      </Drawer>
    </div>
  );
};

export default MonitoringPage;

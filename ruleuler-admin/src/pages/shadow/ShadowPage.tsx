import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Table, Select, DatePicker, Card, Row, Col, Statistic, Tag, Space, Input, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import request from '../../api/request';

const { RangePicker } = DatePicker;

interface ShadowStats {
  totalExecutions: number;
  hitCount: number;
  avgExecMs: number;
  errorCount: number;
}

interface ShadowLog {
  execution_id: string;
  project: string;
  package_id: string;
  flow_id: string;
  rule_name: string;
  input_snapshot: string;
  output_snapshot: string;
  exec_ms: number;
  error_msg: string | null;
  created_at: string;
}

const defaultRange: [Dayjs, Dayjs] = [dayjs().subtract(1, 'day'), dayjs()];

const ShadowPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();

  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string | undefined>(() => searchParams.get('project') || undefined);
  const [packageId, setPackageId] = useState<string | undefined>(() => searchParams.get('packageId') || undefined);
  const [ruleName, setRuleName] = useState<string>('');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(defaultRange);

  const [stats, setStats] = useState<ShadowStats | null>(null);
  const [logs, setLogs] = useState<ShadowLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams();
    if (project) params.set('project', project);
    if (packageId) params.set('packageId', packageId);
    setSearchParams(params, { replace: true });
  }, [project, packageId, setSearchParams]);

  const fetched = useRef(false);
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    loadProjects()
      .then((res) => {
        const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
        setProjects(list);
        if (!project && list.length > 0) setProject(list[0]);
      })
      .catch(() => message.error(t('monitoring.loadProjectsFailed')));
  }, []);

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

  const fetchStats = useCallback(async () => {
    if (!project) return;
    try {
      const params: Record<string, string> = {
        project,
        startTime: dateRange[0].format('YYYY-MM-DD'),
        endTime: dateRange[1].format('YYYY-MM-DD'),
      };
      if (packageId) params.packageId = packageId;
      if (ruleName) params.ruleName = ruleName;
      const res = await request.get('/api/shadow/stats', { params });
      setStats(res.data?.data ?? null);
    } catch { /* silent */ }
  }, [project, packageId, ruleName, dateRange]);

  const fetchLogs = useCallback(async () => {
    if (!project) return;
    setLoading(true);
    try {
      const params: Record<string, string | number> = {
        project,
        startTime: dateRange[0].format('YYYY-MM-DD'),
        endTime: dateRange[1].format('YYYY-MM-DD'),
        page,
        size: pageSize,
      };
      if (packageId) params.packageId = packageId;
      if (ruleName) params.ruleName = ruleName;
      const res = await request.get('/api/shadow/logs', { params });
      const result = res.data?.data;
      setLogs(result?.records ?? []);
      setTotal(result?.total ?? 0);
    } catch {
      message.error(t('monitoring.loadExecutionsFailed'));
    } finally {
      setLoading(false);
    }
  }, [project, packageId, ruleName, dateRange, page, pageSize]);

  const prevKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${ruleName}|${dateRange[0].valueOf()}|${dateRange[1].valueOf()}|${page}|${pageSize}`;
    if (prevKey.current === key) return;
    prevKey.current = key;
    fetchStats();
    fetchLogs();
  }, [project, packageId, ruleName, dateRange, page, pageSize, fetchStats, fetchLogs]);

  const columns: ColumnsType<ShadowLog> = useMemo(() => [
    {
      title: 'Execution ID', dataIndex: 'execution_id', key: 'execution_id', width: 120,
      render: (v: string) => <span title={v}>{v.slice(0, 8)}...</span>,
    },
    { title: t('monitoring.project'), dataIndex: 'project', key: 'project', width: 120 },
    { title: t('monitoring.package'), dataIndex: 'package_id', key: 'package_id', width: 150 },
    {
      title: t('shadow.ruleName'), dataIndex: 'rule_name', key: 'rule_name', width: 180,
    },
    {
      title: t('shadow.execMs'), dataIndex: 'exec_ms', key: 'exec_ms', width: 100,
      sorter: (a, b) => a.exec_ms - b.exec_ms,
    },
    {
      title: t('shadow.error'), dataIndex: 'error_msg', key: 'error_msg', width: 150,
      render: (v: string | null) => v ? <Tag color="red">{v}</Tag> : <Tag color="green">OK</Tag>,
    },
    {
      title: t('shadow.createdAt'), dataIndex: 'created_at', key: 'created_at', width: 180,
    },
  ], [i18n.language]);

  const expandedRowRender = (record: ShadowLog) => (
    <Row gutter={16}>
      <Col span={12}>
        <h4>Input Snapshot</h4>
        <pre style={{ maxHeight: 300, overflow: 'auto', background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12 }}>
          {(() => { try { return JSON.stringify(JSON.parse(record.input_snapshot), null, 2); } catch { return record.input_snapshot; } })()}
        </pre>
      </Col>
      <Col span={12}>
        <h4>Output Snapshot</h4>
        <pre style={{ maxHeight: 300, overflow: 'auto', background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12 }}>
          {(() => { try { return JSON.stringify(JSON.parse(record.output_snapshot), null, 2); } catch { return record.output_snapshot; } })()}
        </pre>
      </Col>
    </Row>
  );

  return (
    <div style={{ padding: 24 }}>
      {/* 筛选区 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          value={project}
          onChange={setProject}
          style={{ width: 200 }}
          placeholder={t('monitoring.selectProject')}
          options={projects.map(p => ({ label: p, value: p }))}
        />
        <Select
          value={packageId}
          onChange={setPackageId}
          style={{ width: 240 }}
          placeholder={t('monitoring.selectPackage')}
          options={packages.map(p => ({ label: p.id, value: p.id }))}
        />
        <Input
          value={ruleName}
          onChange={e => setRuleName(e.target.value)}
          placeholder={t('shadow.ruleNameFilter')}
          style={{ width: 200 }}
          allowClear
        />
        <RangePicker value={dateRange} onChange={(v) => { if (v && v[0] && v[1]) setDateRange([v[0], v[1]]); }} />
      </Space>

      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card><Statistic title={t('shadow.totalExecutions')} value={stats?.totalExecutions ?? 0} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('shadow.hitCount')} value={stats?.hitCount ?? 0} valueStyle={{ color: '#1890ff' }} /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('shadow.avgExecMs')} value={stats?.avgExecMs ?? 0} precision={1} suffix="ms" /></Card>
        </Col>
        <Col span={6}>
          <Card><Statistic title={t('shadow.errorCount')} value={stats?.errorCount ?? 0} valueStyle={stats?.errorCount ? { color: '#cf1322' } : undefined} /></Card>
        </Col>
      </Row>

      {/* 明细表格 */}
      <Table
        rowKey="execution_id"
        columns={columns}
        dataSource={logs}
        loading={loading}
        expandable={{ expandedRowRender }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />
    </div>
  );
};

export default ShadowPage;

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Table, Button, Select, DatePicker, InputNumber, Tag, Progress, Input,
  Card, Statistic, Row, Col, Drawer, Descriptions, Modal, Space, message,
} from 'antd';
import {
  PlayCircleOutlined, ExportOutlined, EyeOutlined,
  CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useParams, useSearchParams } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  createReplayTask, listReplayTasks,
  getReplaySessions, getReplayReport, exportReplayTask,
} from '@/api/replay';
import { listPackages } from '@/api/autotest';
import type { ReplayTaskVO, ReplaySessionVO, ReplayReportVO, FieldDiffVO } from '@/api/replay';

const { RangePicker } = DatePicker;

const statusColors: Record<string, string> = {
  pending: 'default',
  running: 'processing',
  completed: 'success',
  failed: 'error',
};

const ReplayPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const project = name || '';
  const [searchParams] = useSearchParams();
  const preselectedPkg = searchParams.get('packageId') || '';

  const [tasks, setTasks] = useState<ReplayTaskVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [packages, setPackages] = useState<Array<{ id: string; name: string }>>([]);

  // 创建表单
  const [selectedPkg, setSelectedPkg] = useState<string>(preselectedPkg);
  const [timeRange, setTimeRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().subtract(7, 'day'), dayjs(),
  ]);
  const [sampleStrategy, setSampleStrategy] = useState<string>('all');
  const [sampleSize, setSampleSize] = useState<number>(10000);
  const [missingStrategy, setMissingStrategy] = useState<string>('segment');

  // 报告
  const [reportTaskId, setReportTaskId] = useState<number | null>(null);
  const [report, setReport] = useState<ReplayReportVO | null>(null);

  // Session 明细
  const [sessionDrawerOpen, setSessionDrawerOpen] = useState(false);
  const [sessions, setSessions] = useState<ReplaySessionVO[]>([]);
  const [sessionTotal, setSessionTotal] = useState(0);
  const [sessionPage, setSessionPage] = useState(1);
  const [currentSessionTaskId, setCurrentSessionTaskId] = useState<number | null>(null);

  // Diff 详情
  const [diffDrawerOpen, setDiffDrawerOpen] = useState(false);
  const [diffFields, setDiffFields] = useState<FieldDiffVO[]>([]);
  const [diffSession, setDiffSession] = useState<ReplaySessionVO | null>(null);

  // 导出
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportTaskId, setExportTaskId] = useState<number | null>(null);

  // 轮询
  const pollingRef = useRef<ReturnType<typeof setInterval>>();

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listReplayTasks({ project, packageId: preselectedPkg || undefined, page, pageSize: 20 });
      setTasks(res.data?.data?.items || []);
      setTotal(res.data?.data?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [project, page]);

  const loadPackages = useCallback(async () => {
    try {
      const res = await listPackages(project);
      setPackages(res.data?.data || []);
    } catch { /* ignore */ }
  }, [project]);

  useEffect(() => { loadTasks(); loadPackages(); }, [loadTasks, loadPackages]);

  // 轮询运行中任务
  useEffect(() => {
    const running = tasks.find((t) => t.status === 'running');
    if (!running) {
      if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = undefined; }
      return;
    }
    if (pollingRef.current) return;
    pollingRef.current = setInterval(() => { loadTasks(); }, 3000);
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, [tasks, loadTasks]);

  const handleCreate = async () => {
    if (!selectedPkg) { message.warning('请选择知识包'); return; }
    try {
      await createReplayTask({
        project,
        packageId: selectedPkg,
        startTime: timeRange[0]?.valueOf(),
        endTime: timeRange[1]?.valueOf(),
        sampleStrategy,
        sampleSize,
        missingVarStrategy: missingStrategy,
      });
      message.success('回放任务已创建');
      setPage(1);
      loadTasks();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '创建失败');
    }
  };

  const handleViewReport = async (taskId: number) => {
    try {
      const res = await getReplayReport(taskId);
      setReport(res.data?.data);
      setReportTaskId(taskId);
    } catch (e: any) {
      message.error(e?.response?.data?.message || '获取报告失败');
    }
  };

  const handleViewSessions = async (taskId: number, p = 1) => {
    setCurrentSessionTaskId(taskId);
    setSessionPage(p);
    try {
      const res = await getReplaySessions(taskId, { page: p, pageSize: 20 });
      setSessions(res.data?.data?.items || []);
      setSessionTotal(res.data?.data?.total || 0);
      setSessionDrawerOpen(true);
    } catch { /* ignore */ }
  };

  const handleViewDiff = (session: ReplaySessionVO) => {
    setDiffSession(session);
    try {
      const dr = JSON.parse(session.diffResult || '{}');
      setDiffFields(dr.fields || []);
    } catch {
      setDiffFields([]);
    }
    setDiffDrawerOpen(true);
  };

  const handleExport = async (scope: string) => {
    if (!exportTaskId) return;
    try {
      const res = await exportReplayTask(exportTaskId, scope);
      message.success(`已导出，packId: ${res.data?.data?.packId}`);
    } catch (e: any) {
      message.error(e?.response?.data?.message || '导出失败');
    }
    setExportModalOpen(false);
  };

  const taskColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '知识包', dataIndex: 'packageId', key: 'packageId' },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={statusColors[s]}>{s}</Tag>,
    },
    { title: '请求总数', dataIndex: 'totalCount', key: 'totalCount', width: 90 },
    { title: '已执行', dataIndex: 'executedCount', key: 'executedCount', width: 80 },
    {
      title: '一致率', key: 'matchRate', width: 100,
      render: (_: unknown, r: ReplayTaskVO) => {
        const rate = r.executedCount > 0 ? (r.matchCount / r.executedCount * 100).toFixed(1) : '-';
        return r.status === 'running'
          ? <Progress percent={Math.round(r.executedCount / Math.max(r.totalCount, 1) * 100)} size="small" />
          : `${rate}%`;
      },
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170,
      render: (t: number) => dayjs(t).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: '操作', key: 'actions', width: 240,
      render: (_: unknown, r: ReplayTaskVO) => (
        <Space size="small">
          <Button size="small" icon={<EyeOutlined />} onClick={() => handleViewReport(r.id)}>报告</Button>
          <Button size="small" onClick={() => handleViewSessions(r.id)}>明细</Button>
          {r.status === 'completed' && (
            <Button size="small" icon={<ExportOutlined />}
              onClick={() => { setExportTaskId(r.id); setExportModalOpen(true); }}>导出</Button>
          )}
        </Space>
      ),
    },
  ];

  const sessionColumns = [
    { title: 'Execution ID', dataIndex: 'originalExecutionId', key: 'execId', ellipsis: true },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s: string) => <Tag color={s === 'success' ? 'success' : 'error'}>{s}</Tag>,
    },
    {
      title: '一致', key: 'match', width: 70,
      render: (_: unknown, r: ReplaySessionVO) => {
        try {
          const dr = JSON.parse(r.diffResult || '{}');
          return dr.match ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
        } catch { return '-'; }
      },
    },
    { title: '差异字段数', key: 'diffCount', width: 90,
      render: (_: unknown, r: ReplaySessionVO) => {
        try {
          const dr = JSON.parse(r.diffResult || '{}');
          return (dr.fields || []).filter((f: FieldDiffVO) => f.status !== 'SAME').length;
        } catch { return '-'; }
      },
    },
    { title: '耗时(ms)', dataIndex: 'execMs', key: 'execMs', width: 80 },
    {
      title: '操作', key: 'actions', width: 80,
      render: (_: unknown, r: ReplaySessionVO) => (
        <Button size="small" onClick={() => handleViewDiff(r)} disabled={r.status !== 'success'}>Diff</Button>
      ),
    },
  ];

  return (
    <div>
      {/* 创建任务表单 */}
      <Card title="创建回放任务" size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          {preselectedPkg ? (
            <Input disabled style={{ width: 200 }}
              value={packages.find((p: any) => p.id === preselectedPkg)?.name || preselectedPkg} />
          ) : (
            <Select placeholder="选择知识包" style={{ width: 200 }} value={selectedPkg || undefined}
              onChange={setSelectedPkg} options={packages.map((p: any) => ({ label: p.name || p.id, value: p.id }))} />
          )}
          <RangePicker showTime value={timeRange} onChange={(v) => { if (v && v[0] && v[1]) setTimeRange([v[0], v[1]]); }} />
          <Select value={sampleStrategy} onChange={setSampleStrategy} style={{ width: 120 }}
            options={[{ label: '随机采样', value: 'random' }, { label: '全量', value: 'all' }, { label: '均匀采样', value: 'uniform' }]} />
          <InputNumber min={1} max={10000} value={sampleSize} onChange={(v) => setSampleSize(v || 10000)} style={{ width: 100 }} />
          <Select value={missingStrategy} onChange={setMissingStrategy} style={{ width: 120 }}
            options={[{ label: '区间填充', value: 'segment' }, { label: '不填充', value: 'null' }, { label: '跳过', value: 'skip' }]} />
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleCreate}>创建</Button>
        </Space>
      </Card>

      {/* 任务列表 */}
      <Card title="回放任务" size="small">
        <Table rowKey="id" dataSource={tasks} columns={taskColumns} loading={loading}
          pagination={{ current: page, total, pageSize: 20, onChange: setPage }} size="small" />
      </Card>

      {/* 报告视图 */}
      {report && (
        <Card title={`回放报告 #${reportTaskId}`} size="small" style={{ marginTop: 16 }}
          extra={<Button onClick={() => { setReport(null); setReportTaskId(null); }}>关闭</Button>}>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={4}><Statistic title="总数" value={report.summary.totalCount} /></Col>
            <Col span={4}><Statistic title="一致" value={report.summary.matchCount} valueStyle={{ color: '#3f8600' }} /></Col>
            <Col span={4}><Statistic title="不一致" value={report.summary.mismatchCount} valueStyle={{ color: '#cf1322' }} /></Col>
            <Col span={4}><Statistic title="失败" value={report.summary.errorCount} /></Col>
            <Col span={4}><Statistic title="一致率" value={(report.summary.matchRate * 100).toFixed(1)} suffix="%" /></Col>
            <Col span={4}>
              <Statistic title="不完整" value={report.summary.incompleteCount} />
              {report.summary.matchRate < 0.8 && (
                <div style={{ color: '#faad14', marginTop: 4 }}><ExclamationCircleOutlined /> 一致率低于 80%</div>
              )}
            </Col>
          </Row>
          {report.variableStats.length > 0 && (
            <Table rowKey={(r: any) => r.category + '.' + r.name} dataSource={report.variableStats} size="small"
              pagination={false} title={() => '变量维度差异统计'}
              columns={[
                { title: '类别', dataIndex: 'category' },
                { title: '变量', dataIndex: 'name' },
                { title: '变化次数', dataIndex: 'changeCount' },
                { title: '比较总数', dataIndex: 'totalCompared' },
                { title: '变化率', dataIndex: 'changeRate', render: (v: number) => `${(v * 100).toFixed(1)}%` },
              ]} />
          )}
          {report.timing && (
            <Descriptions title="耗时对比" size="small" column={3} style={{ marginTop: 16 }}>
              <Descriptions.Item label="原始平均">{report.timing.originalAvgMs.toFixed(0)}ms</Descriptions.Item>
              <Descriptions.Item label="回放平均">{report.timing.replayAvgMs.toFixed(0)}ms</Descriptions.Item>
              <Descriptions.Item label="原始P95">{report.timing.originalP95Ms.toFixed(0)}ms</Descriptions.Item>
              <Descriptions.Item label="回放P95">{report.timing.replayP95Ms.toFixed(0)}ms</Descriptions.Item>
            </Descriptions>
          )}
        </Card>
      )}

      {/* Session 明细 */}
      <Drawer title={`会话明细 #${currentSessionTaskId}`} open={sessionDrawerOpen}
        onClose={() => setSessionDrawerOpen(false)} width={800}>
        <Table rowKey="id" dataSource={sessions} columns={sessionColumns} size="small"
          pagination={{ current: sessionPage, total: sessionTotal, pageSize: 20,
            onChange: (p) => currentSessionTaskId && handleViewSessions(currentSessionTaskId, p) }} />
      </Drawer>

      {/* Diff 详情 */}
      <Drawer title="差异详情" open={diffDrawerOpen} onClose={() => setDiffDrawerOpen(false)} width={600}>
        {diffSession && (
          <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Execution ID">{diffSession.originalExecutionId}</Descriptions.Item>
            <Descriptions.Item label="耗时">{diffSession.execMs}ms / 原始 {diffSession.originalExecMs}ms</Descriptions.Item>
          </Descriptions>
        )}
        <Table rowKey={(r: any) => r.category + '.' + r.name} dataSource={diffFields} size="small" pagination={false}
          columns={[
            { title: '类别', dataIndex: 'category' },
            { title: '变量', dataIndex: 'name' },
            { title: '状态', dataIndex: 'status', render: (s: string) => {
              const colors: Record<string, string> = { SAME: 'green', CHANGED: 'red', ADDED: 'blue', REMOVED: 'gray' };
              return <Tag color={colors[s]}>{s}</Tag>;
            }},
            { title: '原始值', dataIndex: 'oldValue', render: (v: unknown) => v != null ? String(v) : '-' },
            { title: '回放值', dataIndex: 'newValue', render: (v: unknown) => v != null ? String(v) : '-' },
          ]} />
      </Drawer>

      {/* 导出弹窗 */}
      <Modal title="导出为测试用例包" open={exportModalOpen} onCancel={() => setExportModalOpen(false)} footer={null}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button block onClick={() => handleExport('all')}>全部导出</Button>
          <Button block onClick={() => handleExport('mismatch')}>仅不一致</Button>
          <Button block onClick={() => handleExport('match')}>仅一致</Button>
        </Space>
      </Modal>
    </div>
  );
};

export default ReplayPage;

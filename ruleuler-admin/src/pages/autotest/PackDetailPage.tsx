import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Table, Tag, Progress, Button, Card, message, Space, Divider,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { getPackDetail, loadTestRuns, getRunStatus, updateBaseline } from '../../api/autotest';

interface CaseItem {
  id: number;
  caseName: string;
  inputData: string;
  expectedType: string;
  flippedCondition: string;
  testPurpose: string;
}

interface RunItem {
  id: number;
  packId: number;
  runType: string;
  status: string;
  passedCases: number;
  totalCases: number;
  executedCases: number;
  startedAt: number;
  baselineRunId: number | null;
}

const fmt = (v: number) => {
  const d = new Date(v);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

const truncJson = (s: string, max = 80) => {
  if (!s) return '-';
  return s.length > max ? s.slice(0, max) + '…' : s;
};

const PackDetailPage: React.FC = () => {
  const { name, packId } = useParams<{ name: string; packId: string }>();
  const navigate = useNavigate();
  const numericPackId = Number(packId);
  const { t, i18n } = useTranslation();

  const [cases, setCases] = useState<CaseItem[]>([]);
  const [runs, setRuns] = useState<RunItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [packName, setPackName] = useState('');
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchData = useCallback(async () => {
    if (!name || !packId) return;
    setLoading(true);
    try {
      const [detailRes, runsRes] = await Promise.all([
        getPackDetail(numericPackId),
        loadTestRuns(name),
      ]);
      const detail = detailRes.data?.data ?? detailRes.data ?? {};
      setCases(detail.cases ?? []);
      setPackName(detail.pack?.packName ?? '');

      const allRuns: RunItem[] = (runsRes.data?.data ?? runsRes.data ?? []);
      setRuns(allRuns.filter((r: RunItem) => r.packId === numericPackId));
    } catch {
      message.error(t('autotest.loadDetailFailed'));
    } finally {
      setLoading(false);
    }
  }, [name, packId, numericPackId, t]);

  const fetched = useRef(false);
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchData();
  }, [fetchData]);

  // 轮询 running 状态
  useEffect(() => {
    const runningIds = runs.filter(r => r.status === 'running').map(r => r.id);
    if (runningIds.length === 0) {
      if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null; }
      return;
    }

    pollingRef.current = setInterval(async () => {
      let anyRunning = false;
      for (const rid of runningIds) {
        try {
          const res = await getRunStatus(rid);
          const st = res.data?.data ?? res.data ?? {};
          setRuns(prev => prev.map(r => r.id === rid ? {
            ...r,
            executedCases: st.executedCases ?? r.executedCases,
            status: st.status ?? r.status,
            passedCases: st.passedCases ?? r.passedCases,
            totalCases: st.totalCases ?? r.totalCases,
          } : r));
          if ((st.status ?? 'running') === 'running') anyRunning = true;
        } catch { /* ignore */ }
      }
      if (!anyRunning) {
        if (pollingRef.current) clearInterval(pollingRef.current);
        pollingRef.current = null;
        fetchData(); // 运行完成，刷新全部数据
      }
    }, 3000);

    return () => { if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null; } };
  }, [runs.map(r => `${r.id}:${r.status}`).join(',')]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSetBaseline = async (runId: number) => {
    try {
      await updateBaseline(runId);
      message.success(t('autotest.baselineSet'));
      fetchData();
    } catch {
      message.error(t('autotest.baselineSetFailed'));
    }
  };

  const caseColumns = useMemo(() => [
    { title: t('autotest.caseName'), dataIndex: 'caseName', key: 'caseName', width: 200 },
    {
      title: t('autotest.inputData'), dataIndex: 'inputData', key: 'inputData',
      render: (v: string) => <span title={v}>{truncJson(v)}</span>,
    },
    {
      title: t('autotest.expectedType'), dataIndex: 'expectedType', key: 'expectedType', width: 100,
      render: (v: string) => <Tag color={v === 'HIT' ? 'green' : 'orange'}>{v}</Tag>,
    },
    { title: t('autotest.flippedCondition'), dataIndex: 'flippedCondition', key: 'flippedCondition', width: 200 },
    { title: t('autotest.testPurpose'), dataIndex: 'testPurpose', key: 'testPurpose', width: 200 },
  ], [t, i18n.language]);

  const runColumns = useMemo(() => [
    { title: 'Run ID', dataIndex: 'id', key: 'id', width: 80,
      render: (v: number, r: RunItem) => (
        <Space size={4}>
          <span>{v}</span>
          {r.baselineRunId == null && r.status === 'completed' && <Tag color="gold">Baseline</Tag>}
        </Space>
      ),
    },
    {
      title: t('autotest.runType'), dataIndex: 'runType', key: 'runType', width: 100,
      render: (v: string) => (
        <Tag color={v === 'regression' ? 'blue' : 'cyan'}>{v === 'regression' ? t('autotest.regression') : t('autotest.smoke')}</Tag>
      ),
    },
    {
      title: t('common.status'), dataIndex: 'status', key: 'status', width: 120,
      render: (v: string) => {
        const m: Record<string, { color: string; text: string }> = {
          running: { color: 'processing', text: t('autotest.running') },
          completed: { color: 'success', text: t('autotest.completed') },
          failed: { color: 'error', text: t('monitoring.failed') },
        };
        const c = m[v] ?? { color: 'default', text: v };
        return <Tag color={c.color}>{c.text}</Tag>;
      },
    },
    {
      title: t('autotest.consistencyRate'), key: 'passRate', width: 120,
      render: (_: unknown, r: RunItem) => {
        if (r.status === 'running') {
          const pct = r.totalCases > 0 ? Math.round(r.executedCases / r.totalCases * 100) : 0;
          return <Progress percent={pct} size="small" status="active" />;
        }
        if (r.totalCases > 0) return `${r.passedCases}/${r.totalCases}`;
        return '-';
      },
    },
    {
      title: t('autotest.startTime'), dataIndex: 'startedAt', key: 'startedAt', width: 180,
      render: (v: number) => v ? fmt(v) : '-',
    },
    {
      title: t('common.operation'), key: 'action', width: 120,
      render: (_: unknown, r: RunItem) => {
        if (r.status !== 'completed' || r.baselineRunId == null) return null;
        return (
          <Button size="small" onClick={(e) => { e.stopPropagation(); handleSetBaseline(r.id); }}>
            {t('autotest.setBaseline')}
          </Button>
        );
      },
    },
  ], [t, i18n.language, handleSetBaseline]);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>{t('common.back')}</Button>
        <span style={{ fontSize: 16, fontWeight: 500 }}>{packName || `用例包 #${packId}`}</span>
      </Space>

      <Card title={t('autotest.caseList')} size="small" style={{ marginBottom: 16 }}>
        <Table columns={caseColumns} dataSource={cases} rowKey="id" loading={loading}
          size="small" pagination={{ pageSize: 10, showTotal: total => t('common.total', { count: total }) }} />
      </Card>

      <Divider />

      <Card title={t('autotest.historyRun')} size="small">
        <Table columns={runColumns} dataSource={runs} rowKey="id" loading={loading}
          size="small" pagination={{ pageSize: 10, showTotal: total => t('common.total', { count: total }) }}
          onRow={(record) => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate(`/projects/${name}/autotest/run/${record.id}`),
          })} />
      </Card>
    </div>
  );
};

export default PackDetailPage;

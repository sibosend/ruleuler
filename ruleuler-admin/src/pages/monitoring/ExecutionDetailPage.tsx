import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Table, Descriptions, Tag, Button, Spin, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { fetchExecutionDetail, fetchExecutionTrace } from '../../api/monitoring';

interface VarDetail {
  var_category: string;
  var_name: string;
  var_type: string;
  val_num: number | null;
  val_str: string | null;
  io_type: string;
}

interface TraceRow {
  seq: number;
  msg_type: string;
  msg_text: string;
  parsed_name: string | null;
  pass_fail: 'PASS' | 'FAIL' | null;
  created_at: string;
}

interface ExecutionInfo {
  execution_id: string;
  project: string;
  package_id: string;
  flow_id: string;
  exec_ms: number;
  created_at: string;
  status: string;
  grayscale_bucket?: string;
  variables: VarDetail[];
}

const MSG_TYPE_COLORS: Record<string, string> = {
  Condition: 'blue',
  RuleMatch: 'green',
  VarAssign: 'orange',
  ScoreCard: 'cyan',
  RuleFlow: 'purple',
  ExecuteBeanMethod: 'magenta',
  ExecuteFunction: 'geekblue',
  ConsoleOutput: 'default',
};

const ExecutionDetailPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ExecutionInfo | null>(null);
  const [traceData, setTraceData] = useState<TraceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const fetched = useRef(false);

  const columns: ColumnsType<VarDetail> = useMemo(() => [
    { title: t('monitoring.category'), dataIndex: 'var_category', key: 'var_category', width: 160 },
    { title: t('monitoring.varName'), dataIndex: 'var_name', key: 'var_name', width: 180 },
    { title: t('monitoring.dataType'), dataIndex: 'var_type', key: 'var_type', width: 100 },
    {
      title: t('monitoring.value'),
      key: 'value',
      width: 200,
      render: (_: unknown, r: VarDetail) => r.val_num != null ? r.val_num : (r.val_str ?? '-'),
    },
    {
      title: t('monitoring.ioType'),
      dataIndex: 'io_type',
      key: 'io_type',
      width: 100,
      render: (v: string) => (
        <Tag color={v === 'input' ? 'blue' : 'green'}>{v === 'input' ? t('monitoring.input') : t('monitoring.output')}</Tag>
      ),
    },
  ], [i18n.language]);

  const traceColumns: ColumnsType<TraceRow> = useMemo(() => [
    { title: '#', dataIndex: 'seq', key: 'seq', width: 50 },
    {
      title: t('monitoring.typeCol'),
      dataIndex: 'msg_type',
      key: 'msg_type',
      width: 130,
      render: (v: string) => <Tag color={MSG_TYPE_COLORS[v] || 'default'}>{v}</Tag>,
    },
    {
      title: t('monitoring.name'),
      dataIndex: 'parsed_name',
      key: 'parsed_name',
      width: 200,
      render: (v: string | null) => v ?? '-',
    },
    {
      title: t('monitoring.result'),
      dataIndex: 'pass_fail',
      key: 'pass_fail',
      width: 80,
      render: (v: string | null) => {
        if (v === 'PASS') return <Tag color="green">{t('monitoring.passResult')}</Tag>;
        if (v === 'FAIL') return <Tag color="red">{t('monitoring.failResult')}</Tag>;
        return '-';
      },
    },
    {
      title: t('monitoring.detail'),
      dataIndex: 'msg_text',
      key: 'msg_text',
      ellipsis: true,
    },
  ], [i18n.language]);

  useEffect(() => {
    if (!id || fetched.current) return;
    fetched.current = true;
    setLoading(true);
    fetchExecutionDetail(id)
      .then((data) => {
        const rows = Array.isArray(data) ? data : [];
        if (rows.length === 0) { setDetail(null); return; }
        const first = rows[0] as Record<string, unknown>;
        setDetail({
          execution_id: first.execution_id as string,
          project: first.project as string,
          package_id: first.package_id as string,
          flow_id: first.flow_id as string,
          exec_ms: first.exec_ms as number,
          created_at: first.created_at as string,
          status: rows.some((r: Record<string, unknown>) => r.var_name === '') ? 'failed' : 'success',
          grayscale_bucket: first.grayscale_bucket as string | undefined,
          variables: rows as unknown as VarDetail[],
        });
        // 并行加载追踪数据
        fetchExecutionTrace(first.execution_id as string)
          .then((trace) => setTraceData(Array.isArray(trace) ? trace : []))
          .catch(() => {/* 追踪数据加载失败不影响主流程 */});
      })
      .catch(() => message.error(t('monitoring.loadExecDetailFailed')))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!detail) return <div style={{ textAlign: 'center', marginTop: 100, color: '#999' }}>{t('monitoring.noData')}</div>;

  const inputVars = (detail.variables ?? []).filter((v) => v.io_type === 'input');
  const outputVars = (detail.variables ?? []).filter((v) => v.io_type === 'output');

  return (
    <div>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/monitoring/executions')}
        style={{ marginBottom: 12, paddingLeft: 0 }}
      >
        {t('monitoring.backToExecList')}
      </Button>

      <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label={t('monitoring.execId')}>{detail.execution_id}</Descriptions.Item>
        <Descriptions.Item label={t('monitoring.project')}>{detail.project}</Descriptions.Item>
        <Descriptions.Item label={t('monitoring.package')}>{detail.package_id}</Descriptions.Item>
        <Descriptions.Item label="Flow">{detail.flow_id}</Descriptions.Item>
        <Descriptions.Item label={t('monitoring.duration')}>{detail.exec_ms} ms</Descriptions.Item>
        <Descriptions.Item label={t('monitoring.status')}>
          <Tag color={detail.status === 'success' ? 'green' : 'red'}>
            {detail.status === 'success' ? t('monitoring.success') : t('monitoring.failed')}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.grayscale')}>
          {detail.grayscale_bucket === 'GRAY'
            ? <Tag color="purple">GRAY</Tag>
            : <Tag color="default">BASE</Tag>}
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.execTime')}>{detail.created_at}</Descriptions.Item>
      </Descriptions>

      <h4 style={{ marginBottom: 8 }}>{t('monitoring.inputVars')} ({inputVars.length})</h4>
      <Table
        columns={columns}
        dataSource={inputVars}
        rowKey={(r) => `${r.var_category}:${r.var_name}`}
        size="small"
        pagination={false}
        style={{ marginBottom: 16 }}
      />

      <h4 style={{ marginBottom: 8 }}>{t('monitoring.outputVars')} ({outputVars.length})</h4>
      <Table
        columns={columns}
        dataSource={outputVars}
        rowKey={(r) => `${r.var_category}:${r.var_name}`}
        size="small"
        pagination={false}
      />

      {traceData.length > 0 && (
        <>
          <h4 style={{ marginBottom: 8, marginTop: 24 }}>{t('monitoring.ruleTrace')} ({traceData.length})</h4>
          <Table
            columns={traceColumns}
            dataSource={traceData}
            rowKey="seq"
            size="small"
            pagination={false}
          />
        </>
      )}
    </div>
  );
};

export default ExecutionDetailPage;

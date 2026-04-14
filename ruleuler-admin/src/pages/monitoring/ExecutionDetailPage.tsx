import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Table, Descriptions, Tag, Button, Spin, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowLeftOutlined } from '@ant-design/icons';
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
  variables: VarDetail[];
}

const columns: ColumnsType<VarDetail> = [
  { title: '类别', dataIndex: 'var_category', key: 'var_category', width: 160 },
  { title: '变量名', dataIndex: 'var_name', key: 'var_name', width: 180 },
  { title: '数据类型', dataIndex: 'var_type', key: 'var_type', width: 100 },
  {
    title: '值',
    key: 'value',
    width: 200,
    render: (_: unknown, r: VarDetail) => r.val_num != null ? r.val_num : (r.val_str ?? '-'),
  },
  {
    title: '输入/输出',
    dataIndex: 'io_type',
    key: 'io_type',
    width: 100,
    render: (v: string) => (
      <Tag color={v === 'input' ? 'blue' : 'green'}>{v === 'input' ? '输入' : '输出'}</Tag>
    ),
  },
];

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

const traceColumns: ColumnsType<TraceRow> = [
  { title: '#', dataIndex: 'seq', key: 'seq', width: 50 },
  {
    title: '类型',
    dataIndex: 'msg_type',
    key: 'msg_type',
    width: 130,
    render: (v: string) => <Tag color={MSG_TYPE_COLORS[v] || 'default'}>{v}</Tag>,
  },
  {
    title: '名称',
    dataIndex: 'parsed_name',
    key: 'parsed_name',
    width: 200,
    render: (v: string | null) => v ?? '-',
  },
  {
    title: '结果',
    dataIndex: 'pass_fail',
    key: 'pass_fail',
    width: 80,
    render: (v: string | null) => {
      if (v === 'PASS') return <Tag color="green">通过</Tag>;
      if (v === 'FAIL') return <Tag color="red">未通过</Tag>;
      return '-';
    },
  },
  {
    title: '详情',
    dataIndex: 'msg_text',
    key: 'msg_text',
    ellipsis: true,
  },
];

const ExecutionDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<ExecutionInfo | null>(null);
  const [traceData, setTraceData] = useState<TraceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const fetched = useRef(false);

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
          variables: rows as unknown as VarDetail[],
        });
        // 并行加载追踪数据
        fetchExecutionTrace(first.execution_id as string)
          .then((trace) => setTraceData(Array.isArray(trace) ? trace : []))
          .catch(() => {/* 追踪数据加载失败不影响主流程 */});
      })
      .catch(() => message.error('加载执行详情失败'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!detail) return <div style={{ textAlign: 'center', marginTop: 100, color: '#999' }}>无数据</div>;

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
        返回执行列表
      </Button>

      <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="执行ID">{detail.execution_id}</Descriptions.Item>
        <Descriptions.Item label="项目">{detail.project}</Descriptions.Item>
        <Descriptions.Item label="知识包">{detail.package_id}</Descriptions.Item>
        <Descriptions.Item label="Flow">{detail.flow_id}</Descriptions.Item>
        <Descriptions.Item label="耗时">{detail.exec_ms} ms</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={detail.status === 'success' ? 'green' : 'red'}>
            {detail.status === 'success' ? '成功' : '失败'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="执行时间">{detail.created_at}</Descriptions.Item>
      </Descriptions>

      <h4 style={{ marginBottom: 8 }}>输入变量 ({inputVars.length})</h4>
      <Table
        columns={columns}
        dataSource={inputVars}
        rowKey={(r) => `${r.var_category}:${r.var_name}`}
        size="small"
        pagination={false}
        style={{ marginBottom: 16 }}
      />

      <h4 style={{ marginBottom: 8 }}>输出变量 ({outputVars.length})</h4>
      <Table
        columns={columns}
        dataSource={outputVars}
        rowKey={(r) => `${r.var_category}:${r.var_name}`}
        size="small"
        pagination={false}
      />

      {traceData.length > 0 && (
        <>
          <h4 style={{ marginBottom: 8, marginTop: 24 }}>规则执行追踪 ({traceData.length})</h4>
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

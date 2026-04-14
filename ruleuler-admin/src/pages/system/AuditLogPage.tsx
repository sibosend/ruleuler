import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Table, Select, Input, DatePicker, Tag, Space, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { fetchAuditLogs, type AuditLogRecord } from '../../api/auditLog';

const { RangePicker } = DatePicker;

const ACTION_OPTIONS = [
  { label: '创建文件', value: 'CREATE' },
  { label: '更新文件', value: 'UPDATE' },
  { label: '删除文件', value: 'DELETE' },
  { label: '重命名', value: 'RENAME' },
  { label: '复制', value: 'COPY' },
  { label: '锁定', value: 'LOCK' },
  { label: '解锁', value: 'UNLOCK' },
  { label: '提交审批', value: 'PUBLISH_SUBMIT' },
  { label: '审批通过', value: 'APPROVE' },
  { label: '审批拒绝', value: 'REJECT' },
  { label: '执行上线', value: 'PUBLISH' },
  { label: '创建用户', value: 'USER_CREATE' },
  { label: '修改用户', value: 'USER_UPDATE' },
  { label: '删除用户', value: 'USER_DELETE' },
  { label: '分配角色', value: 'ROLE_ASSIGN' },
  { label: '创建角色', value: 'ROLE_CREATE' },
  { label: '修改角色', value: 'ROLE_UPDATE' },
  { label: '删除角色', value: 'ROLE_DELETE' },
  { label: '分配权限', value: 'PERM_ASSIGN' },
];

const TARGET_TYPE_OPTIONS = [
  { label: '文件', value: 'FILE' },
  { label: '目录', value: 'DIR' },
  { label: '审批单', value: 'APPROVAL' },
  { label: '用户', value: 'USER' },
  { label: '角色', value: 'ROLE' },
];

const ACTION_COLOR_MAP: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  RENAME: 'orange',
  COPY: 'cyan',
  LOCK: 'purple',
  UNLOCK: 'purple',
  PUBLISH_SUBMIT: 'gold',
  APPROVE: 'green',
  REJECT: 'red',
  PUBLISH: 'geekblue',
  USER_CREATE: 'green',
  USER_UPDATE: 'blue',
  USER_DELETE: 'red',
  ROLE_ASSIGN: 'orange',
  ROLE_CREATE: 'green',
  ROLE_UPDATE: 'blue',
  ROLE_DELETE: 'red',
  PERM_ASSIGN: 'orange',
};

const AuditLogPage: React.FC = () => {
  const [action, setAction] = useState<string | undefined>();
  const [targetType, setTargetType] = useState<string | undefined>();
  const [operator, setOperator] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | undefined>();

  const [data, setData] = useState<AuditLogRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await fetchAuditLogs({
        action,
        targetType,
        operator: operator || undefined,
        startTime: dateRange ? dateRange[0].startOf('day').valueOf() : undefined,
        endTime: dateRange ? dateRange[1].endOf('day').valueOf() : undefined,
        page,
        pageSize,
      });
      setData(Array.isArray(result?.records) ? result.records : []);
      setTotal(result?.total ?? 0);
    } catch {
      message.error('加载审计日志失败');
    } finally {
      setLoading(false);
    }
  }, [action, targetType, operator, dateRange, page, pageSize]);

  const prevKey = useRef('');
  useEffect(() => {
    const key = `${action}|${targetType}|${operator}|${dateRange?.[0].valueOf()}|${dateRange?.[1].valueOf()}|${page}|${pageSize}`;
    if (prevKey.current === key) return;
    prevKey.current = key;
    loadData();
  }, [action, targetType, operator, dateRange, page, pageSize, loadData]);

  const columns: ColumnsType<AuditLogRecord> = useMemo(() => [
    {
      title: '时间', dataIndex: 'created_at', key: 'created_at', width: 180,
      render: (v: number) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
    },
    { title: '操作人', dataIndex: 'operator', key: 'operator', width: 100 },
    {
      title: '操作', dataIndex: 'action', key: 'action', width: 120,
      render: (v: string) => {
        const opt = ACTION_OPTIONS.find(o => o.value === v);
        return <Tag color={ACTION_COLOR_MAP[v] || 'default'}>{opt?.label || v}</Tag>;
      },
    },
    {
      title: '目标类型', dataIndex: 'target_type', key: 'target_type', width: 90,
      render: (v: string) => {
        const opt = TARGET_TYPE_OPTIONS.find(o => o.value === v);
        return opt?.label || v;
      },
    },
    {
      title: '目标路径', dataIndex: 'target_path', key: 'target_path', width: 300,
      ellipsis: true,
    },
    { title: '项目', dataIndex: 'project', key: 'project', width: 120, ellipsis: true },
    {
      title: '详情', dataIndex: 'detail', key: 'detail', width: 200,
      ellipsis: true,
      render: (v: Record<string, unknown>) => v ? JSON.stringify(v) : '-',
    },
  ], []);

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 150 }}
          placeholder="操作类型"
          allowClear
          value={action}
          onChange={(v) => { setAction(v); setPage(1); }}
          options={ACTION_OPTIONS}
        />
        <Select
          style={{ width: 120 }}
          placeholder="目标类型"
          allowClear
          value={targetType}
          onChange={(v) => { setTargetType(v); setPage(1); }}
          options={TARGET_TYPE_OPTIONS}
        />
        <Input
          style={{ width: 140 }}
          placeholder="操作人"
          allowClear
          value={operator}
          onChange={(e) => { setOperator(e.target.value || undefined); setPage(1); }}
        />
        <RangePicker
          value={dateRange}
          onChange={(v) => {
            setDateRange(v && v[0] && v[1] ? [v[0], v[1]] : undefined);
            setPage(1);
          }}
        />
      </Space>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        size="small"
        pagination={{
          current: page,
          pageSize,
          total,
          showTotal: (t) => `共 ${t} 条记录`,
          showSizeChanger: true,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />
    </div>
  );
};

export default AuditLogPage;

import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Table, Select, Input, DatePicker, Tag, Space, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { fetchAuditLogs, type AuditLogRecord } from '../../api/auditLog';
import { useTranslation } from 'react-i18next';

const { RangePicker } = DatePicker;

const ACTION_OPTIONS = [
  { label: 'system.actionCreateFile', value: 'CREATE' },
  { label: 'system.actionUpdateFile', value: 'UPDATE' },
  { label: 'system.actionDeleteFile', value: 'DELETE' },
  { label: 'system.actionRename', value: 'RENAME' },
  { label: 'system.actionCopy', value: 'COPY' },
  { label: 'system.actionLock', value: 'LOCK' },
  { label: 'system.actionUnlock', value: 'UNLOCK' },
  { label: 'system.actionSubmitApproval', value: 'PUBLISH_SUBMIT' },
  { label: 'system.actionApprove', value: 'APPROVE' },
  { label: 'system.actionReject', value: 'REJECT' },
  { label: 'system.actionPublish', value: 'PUBLISH' },
  { label: 'system.actionCreateUser', value: 'USER_CREATE' },
  { label: 'system.actionUpdateUser', value: 'USER_UPDATE' },
  { label: 'system.actionDeleteUser', value: 'USER_DELETE' },
  { label: 'system.actionAssignRole', value: 'ROLE_ASSIGN' },
  { label: 'system.actionCreateRole', value: 'ROLE_CREATE' },
  { label: 'system.actionUpdateRole', value: 'ROLE_UPDATE' },
  { label: 'system.actionDeleteRole', value: 'ROLE_DELETE' },
  { label: 'system.actionAssignPerm', value: 'PERM_ASSIGN' },
];

const TARGET_TYPE_OPTIONS = [
  { label: 'system.targetFile', value: 'FILE' },
  { label: 'system.targetDir', value: 'DIR' },
  { label: 'system.targetApproval', value: 'APPROVAL' },
  { label: 'system.targetUser', value: 'USER' },
  { label: 'system.targetRole', value: 'ROLE' },
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
  const { t, i18n } = useTranslation();
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
      message.error(t('system.loadAuditFailed'));
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
      title: t('system.time'), dataIndex: 'created_at', key: 'created_at', width: 180,
      render: (v: number) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
    },
    { title: t('system.operator'), dataIndex: 'operator', key: 'operator', width: 100 },
    {
      title: t('system.actionType'), dataIndex: 'action', key: 'action', width: 120,
      render: (v: string) => {
        const opt = ACTION_OPTIONS.find(o => o.value === v);
        return <Tag color={ACTION_COLOR_MAP[v] || 'default'}>{opt ? t(opt.label) : v}</Tag>;
      },
    },
    {
      title: t('system.targetType'), dataIndex: 'target_type', key: 'target_type', width: 90,
      render: (v: string) => {
        const opt = TARGET_TYPE_OPTIONS.find(o => o.value === v);
        return opt ? t(opt.label) : v;
      },
    },
    {
      title: t('system.targetPath'), dataIndex: 'target_path', key: 'target_path', width: 300,
      ellipsis: true,
    },
    { title: t('system.project'), dataIndex: 'project', key: 'project', width: 120, ellipsis: true },
    {
      title: t('system.detail'), dataIndex: 'detail', key: 'detail', width: 200,
      ellipsis: true,
      render: (v: Record<string, unknown>) => v ? JSON.stringify(v) : '-',
    },
  ], [i18n.language]);

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 150 }}
          placeholder={t('system.actionType')}
          allowClear
          value={action}
          onChange={(v) => { setAction(v); setPage(1); }}
          options={ACTION_OPTIONS.map(o => ({ ...o, label: t(o.label) }))}
        />
        <Select
          style={{ width: 120 }}
          placeholder={t('system.targetType')}
          allowClear
          value={targetType}
          onChange={(v) => { setTargetType(v); setPage(1); }}
          options={TARGET_TYPE_OPTIONS.map(o => ({ ...o, label: t(o.label) }))}
        />
        <Input
          style={{ width: 140 }}
          placeholder={t('system.operator')}
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
          showTotal: (total) => t('system.totalRecords', { total }),
          showSizeChanger: true,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />
    </div>
  );
};

export default AuditLogPage;

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Table, Tag, Space, Popconfirm, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import {
  listGrayscaleRules, fullRolloutGrayscale, rollbackGrayscale,
  type GrayscaleRuleVO,
} from '@/api/grayscale';

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'blue', label: 'grayscale.statusActive' },
  ROLLED_OUT: { color: 'green', label: 'grayscale.statusRolledOut' },
  ROLLED_BACK: { color: 'orange', label: 'grayscale.statusRolledBack' },
};

const STRATEGY_MAP: Record<string, string> = {
  PERCENTAGE: 'grayscale.percentage',
  CONDITION: 'grayscale.condition',
};

const GrayscaleRulePage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [data, setData] = useState<GrayscaleRuleVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const fetchData = useCallback(async (p = page) => {
    setLoading(true);
    try {
      const res = await listGrayscaleRules({ page: p, pageSize: 20 });
      setData(res.data?.data?.items ?? res.data?.items ?? []);
      setTotal(res.data?.data?.total ?? res.data?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleRollout = async (id: number) => {
    try {
      await fullRolloutGrayscale(id);
      message.success(t('grayscale.rolloutComplete'));
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const handleRollback = async (id: number) => {
    try {
      await rollbackGrayscale(id);
      message.success(t('grayscale.rolledBack'));
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const columns: ColumnsType<GrayscaleRuleVO> = useMemo(() => [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: t('grayscale.project'), dataIndex: 'project', width: 120, ellipsis: true },
    { title: t('grayscale.knowledgePackage'), dataIndex: 'packageId', ellipsis: true },
    {
      title: t('grayscale.strategy'), dataIndex: 'strategy', width: 100,
      render: (s: string) => t(STRATEGY_MAP[s] ?? s),
    },
    {
      title: t('grayscale.config'), width: 200, ellipsis: true,
      render: (_: any, r: GrayscaleRuleVO) =>
        r.strategy === 'PERCENTAGE' ? `${r.percentage}%`
          : <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.conditionExpr ?? '-'}</span>,
    },
    {
      title: t('common.status'), dataIndex: 'status', width: 90,
      render: (s: string) => { const m = STATUS_MAP[s]; return m ? <Tag color={m.color}>{t(m.label)}</Tag> : s; },
    },
    { title: t('grayscale.createdBy'), dataIndex: 'createdBy', width: 90 },
    {
      title: t('grayscale.createdAt'), dataIndex: 'createdAt', width: 170,
      render: (t: number) => t ? new Date(t).toLocaleString() : '-',
    },
    {
      title: t('common.operation'), width: 180, fixed: 'right',
      render: (_: any, record: GrayscaleRuleVO) => (
        <Space size="small">
          {record.status === 'ACTIVE' && (
            <>
              <Popconfirm
                title={t('grayscale.confirmRollout')}
                onConfirm={() => handleRollout(record.id)}
                okText={t('common.confirm')}
                cancelText={t('common.cancel')}
              >
                <Button size="small" type="primary">{t('grayscale.rollout')}</Button>
              </Popconfirm>
              <Popconfirm
                title={t('grayscale.confirmRollback')}
                onConfirm={() => handleRollback(record.id)}
                okText={t('common.confirm')}
                cancelText={t('common.cancel')}
              >
                <Button size="small" danger>{t('grayscale.rollback')}</Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ], [i18n.language]);

  return (
    <div style={{ padding: 24 }}>
      <Table<GrayscaleRuleVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (total) => t('grayscale.totalItems', { count: total }) }}
        scroll={{ x: 1100 }}
        size="middle"
      />
    </div>
  );
};

export default GrayscaleRulePage;

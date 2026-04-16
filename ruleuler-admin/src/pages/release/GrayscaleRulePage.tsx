import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Table, Tag, Space, Popconfirm, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  listGrayscaleRules, fullRolloutGrayscale, rollbackGrayscale,
  type GrayscaleRuleVO,
} from '@/api/grayscale';

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'blue', label: '运行中' },
  ROLLED_OUT: { color: 'green', label: '已全量' },
  ROLLED_BACK: { color: 'orange', label: '已回退' },
};

const STRATEGY_MAP: Record<string, string> = {
  PERCENTAGE: '流量比例',
  CONDITION: '条件匹配',
};

const GrayscaleRulePage: React.FC = () => {
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
      message.success('全量发布完成');
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const handleRollback = async (id: number) => {
    try {
      await rollbackGrayscale(id);
      message.success('已回退');
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const columns: ColumnsType<GrayscaleRuleVO> = useMemo(() => [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '项目', dataIndex: 'project', width: 120, ellipsis: true },
    { title: '知识包', dataIndex: 'packageId', ellipsis: true },
    {
      title: '策略', dataIndex: 'strategy', width: 100,
      render: (s: string) => STRATEGY_MAP[s] ?? s,
    },
    {
      title: '配置', width: 200, ellipsis: true,
      render: (_: any, r: GrayscaleRuleVO) =>
        r.strategy === 'PERCENTAGE' ? `${r.percentage}%`
          : <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.conditionExpr ?? '-'}</span>,
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (s: string) => { const m = STATUS_MAP[s]; return m ? <Tag color={m.color}>{m.label}</Tag> : s; },
    },
    { title: '创建人', dataIndex: 'createdBy', width: 90 },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 170,
      render: (t: number) => t ? new Date(t).toLocaleString() : '-',
    },
    {
      title: '操作', width: 180, fixed: 'right',
      render: (_: any, record: GrayscaleRuleVO) => (
        <Space size="small">
          {record.status === 'ACTIVE' && (
            <>
              <Popconfirm
                title="确认全量发布？灰度版本将替换当前生产版本"
                onConfirm={() => handleRollout(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button size="small" type="primary">全量发布</Button>
              </Popconfirm>
              <Popconfirm
                title="确认回退？所有流量回到基线版本"
                onConfirm={() => handleRollback(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button size="small" danger>回退</Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ], []);

  return (
    <div style={{ padding: 24 }}>
      <Table<GrayscaleRuleVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        scroll={{ x: 1100 }}
        size="middle"
      />
    </div>
  );
};

export default GrayscaleRulePage;

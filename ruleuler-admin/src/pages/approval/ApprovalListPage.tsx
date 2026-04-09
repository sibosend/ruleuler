import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button, Table, Tag, Space, Select, Drawer, Modal, Input,
  message, Descriptions,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  listApprovals, getApprovalDetail, approveApproval, rejectApproval,
  submitApproval, listPackages,
  type ApprovalVO, type ApprovalDiffItem,
} from '@/api/approval';
import { usePermission } from '@/hooks/usePermission';

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'blue', label: '待审批' },
  APPROVED: { color: 'green', label: '已通过' },
  REJECTED: { color: 'red', label: '已拒绝' },
  PUBLISH_FAILED: { color: 'orange', label: '发布失败' },
};

const CHANGE_TYPE_MAP: Record<string, { color: string; label: string }> = {
  ADDED: { color: 'green', label: '新增' },
  MODIFIED: { color: 'blue', label: '修改' },
  DELETED: { color: 'red', label: '删除' },
};

const ApprovalListPage: React.FC = () => {
  const { name: project } = useParams<{ name: string }>();
  const [data, setData] = useState<ApprovalVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [diffDrawer, setDiffDrawer] = useState<{ open: boolean; approval: ApprovalVO | null; diffs: ApprovalDiffItem[] }>({
    open: false, approval: null, diffs: [],
  });
  const [submitModal, setSubmitModal] = useState(false);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [selectedPkg, setSelectedPkg] = useState<string | null>(null);
  const [commentModal, setCommentModal] = useState<{ open: boolean; type: 'approve' | 'reject'; id: number } | null>(null);
  const [comment, setComment] = useState('');

  const canSubmit = usePermission('pack:publish:submit');
  const canApprove = usePermission('pack:publish:approve');
  const fetched = useRef(false);

  const fetchData = useCallback(async (p = page, s = statusFilter) => {
    if (!project) return;
    setLoading(true);
    try {
      const res = await listApprovals({ project, status: s, page: p, pageSize: 20 });
      setData(res.data?.data?.items ?? res.data?.items ?? []);
      setTotal(res.data?.data?.total ?? res.data?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [project, page, statusFilter]);

  useEffect(() => {
    if (!fetched.current) {
      fetched.current = true;
      fetchData();
    }
  }, [fetchData]);

  useEffect(() => {
    fetchData();
  }, [page, statusFilter]);

  const handleSubmit = async () => {
    if (!project || !selectedPkg) return;
    try {
      await submitApproval({ project, packageId: selectedPkg });
      message.success('提交审批成功');
      setSubmitModal(false);
      setSelectedPkg(null);
      fetchData(1);
    } catch { /* request.ts 已处理 */ }
  };

  const handleApprove = async () => {
    if (!commentModal) return;
    try {
      if (commentModal.type === 'approve') {
        await approveApproval(commentModal.id, comment || undefined);
        message.success('审批通过');
      } else {
        await rejectApproval(commentModal.id, comment || undefined);
        message.success('已拒绝');
      }
      setCommentModal(null);
      setComment('');
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const openDetail = async (record: ApprovalVO) => {
    try {
      const res = await getApprovalDetail(record.id);
      const detail: ApprovalVO = res.data?.data ?? res.data;
      setDiffDrawer({ open: true, approval: detail, diffs: detail.diffs ?? [] });
    } catch { /* request.ts 已处理 */ }
  };

  const openSubmitModal = async () => {
    if (!project) return;
    try {
      const res = await listPackages(project);
      const list = res.data?.data ?? res.data ?? [];
      setPackages(list.map((p: any) => ({ id: p.id, name: p.name })));
      setSubmitModal(true);
    } catch { /* request.ts 已处理 */ }
  };

  const columns: ColumnsType<ApprovalVO> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '知识包', dataIndex: 'packageName', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100,
      render: (s: string) => { const m = STATUS_MAP[s]; return m ? <Tag color={m.color}>{m.label}</Tag> : s; },
    },
    { title: '提交人', dataIndex: 'submitter', width: 100 },
    { title: '提交时间', dataIndex: 'submittedAt', width: 170,
      render: (t: number) => t ? new Date(t).toLocaleString() : '-',
    },
    { title: '审批人', dataIndex: 'approver', width: 100, render: (v: string) => v ?? '-' },
    { title: '审批时间', dataIndex: 'approvedAt', width: 170,
      render: (t: number) => t ? new Date(t).toLocaleString() : '-',
    },
    { title: '操作', width: 200, fixed: 'right',
      render: (_: any, record: ApprovalVO) => (
        <Space size="small">
          <Button size="small" onClick={() => openDetail(record)}>查看</Button>
          {canApprove && record.status === 'PENDING' && (
            <>
              <Button size="small" type="primary"
                onClick={() => { setCommentModal({ open: true, type: 'approve', id: record.id }); setComment(''); }}>
                通过
              </Button>
              <Button size="small" danger
                onClick={() => { setCommentModal({ open: true, type: 'reject', id: record.id }); setComment(''); }}>
                拒绝
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  // diff 按组件类型分组
  const groupedDiffs = diffDrawer.diffs.reduce<Record<string, ApprovalDiffItem[]>>((acc, d) => {
    const type = d.componentType || '未知';
    if (!acc[type]) acc[type] = [];
    acc[type].push(d);
    return acc;
  }, {});

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="状态筛选"
          allowClear
          style={{ width: 140 }}
          value={statusFilter}
          onChange={(v) => { setStatusFilter(v); setPage(1); }}
        >
          {Object.entries(STATUS_MAP).map(([k, v]) => (
            <Select.Option key={k} value={k}>{v.label}</Select.Option>
          ))}
        </Select>
        {canSubmit && (
          <Button type="primary" onClick={openSubmitModal}>提交发布审批</Button>
        )}
      </Space>

      <Table<ApprovalVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        scroll={{ x: 1000 }}
        size="middle"
      />

      {/* 提交审批弹窗 */}
      <Modal title="提交发布审批" open={submitModal} onOk={handleSubmit}
        onCancel={() => { setSubmitModal(false); setSelectedPkg(null); }}
        okButtonProps={{ disabled: !selectedPkg }}>
        <Select
          placeholder="选择知识包"
          style={{ width: '100%' }}
          value={selectedPkg}
          onChange={setSelectedPkg}
        >
          {packages.map(p => (
            <Select.Option key={p.id} value={p.id}>{p.name}</Select.Option>
          ))}
        </Select>
      </Modal>

      {/* 审批意见弹窗 */}
      <Modal
        title={commentModal?.type === 'approve' ? '审批通过' : '拒绝审批'}
        open={!!commentModal?.open}
        onOk={handleApprove}
        onCancel={() => { setCommentModal(null); setComment(''); }}
      >
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder="审批意见（选填）"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>

      {/* Diff 详情抽屉 */}
      <Drawer
        title={`审批详情 #${diffDrawer.approval?.id ?? ''}`}
        width={640}
        open={diffDrawer.open}
        onClose={() => setDiffDrawer({ open: false, approval: null, diffs: [] })}
      >
        {diffDrawer.approval && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="知识包">{diffDrawer.approval.packageName}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_MAP[diffDrawer.approval.status]?.color}>
                  {STATUS_MAP[diffDrawer.approval.status]?.label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提交人">{diffDrawer.approval.submitter}</Descriptions.Item>
              <Descriptions.Item label="提交时间">
                {diffDrawer.approval.submittedAt ? new Date(diffDrawer.approval.submittedAt).toLocaleString() : '-'}
              </Descriptions.Item>
              {diffDrawer.approval.comment && (
                <Descriptions.Item label="审批意见" span={2}>{diffDrawer.approval.comment}</Descriptions.Item>
              )}
              {diffDrawer.approval.failReason && (
                <Descriptions.Item label="失败原因" span={2}>
                  <span style={{ color: '#ff4d4f' }}>{diffDrawer.approval.failReason}</span>
                </Descriptions.Item>
              )}
            </Descriptions>

            {Object.entries(groupedDiffs).map(([type, items]) => (
              <div key={type} style={{ marginBottom: 16 }}>
                <strong>{type}（{items.length} 项变更）</strong>
                <Table<ApprovalDiffItem>
                  size="small"
                  rowKey="id"
                  pagination={false}
                  dataSource={items}
                  columns={[
                    { title: '组件', dataIndex: 'componentName', ellipsis: true },
                    { title: '变更', dataIndex: 'changeType', width: 70,
                      render: (t: string) => {
                        const m = CHANGE_TYPE_MAP[t];
                        return m ? <Tag color={m.color}>{m.label}</Tag> : t;
                      },
                    },
                    { title: '原版本', dataIndex: 'prevVersion', width: 80, render: (v: string) => v ?? '-' },
                    { title: '新版本', dataIndex: 'currVersion', width: 80, render: (v: string) => v ?? '-' },
                  ]}
                />
              </div>
            ))}

            {diffDrawer.diffs.length === 0 && (
              <div style={{ textAlign: 'center', color: '#999', padding: 24 }}>无变更</div>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default ApprovalListPage;

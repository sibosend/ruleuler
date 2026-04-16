import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button, Table, Tag, Space, Select, Drawer, Modal, Input, Slider, Radio,
  message, Descriptions, Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useLocation, useSearchParams } from 'react-router-dom';
import {
  listApprovals, getApprovalDetail, approveApproval, rejectApproval,
  publishApproval,
  type ApprovalVO, type ApprovalDiffItem, type RuleDiffDetail, type FieldDiff,
} from '@/api/approval';
import { createGrayscaleRule } from '@/api/grayscale';
import { loadProjects } from '@/api/project';
import { loadProjectLibs, loadXml, type ProjectLibs } from '@/pages/rea/api/reaApi';
import type { LibraryData } from '@/pages/rea/lib/expressionParser';
import ReaConditionInput from '@/pages/rea/components/ReaConditionInput';
import { usePermission } from '@/hooks/usePermission';
import { useAuthStore } from '@/stores/authStore';
import { useTabStore } from '@/stores/tabStore';

type ReleaseMode = 'pending' | 'pending-publish' | 'my' | 'all';

interface Props {
  mode: ReleaseMode;
}

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  TESTING: { color: 'processing', label: '测试中' },
  PENDING: { color: 'blue', label: '待审核' },
  APPROVED: { color: 'cyan', label: '待上线' },
  REJECTED: { color: 'red', label: '已拒绝' },
  PUBLISH_FAILED: { color: 'orange', label: '上线失败' },
  PUBLISHED: { color: 'green', label: '已上线' },
};

const CHANGE_TYPE_MAP: Record<string, { color: string; label: string }> = {
  ADDED: { color: 'green', label: '新增' },
  MODIFIED: { color: 'blue', label: '修改' },
  DELETED: { color: 'red', label: '删除' },
};

const PAGE_SIZE = 20;

const ReleaseListPage: React.FC<Props> = ({ mode }) => {
  const [projects, setProjects] = useState<string[]>([]);
  const [project, setProject] = useState<string | null>(null);
  const [data, setData] = useState<ApprovalVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [diffDrawer, setDiffDrawer] = useState<{ open: boolean; approval: ApprovalVO | null; diffs: ApprovalDiffItem[] }>({
    open: false, approval: null, diffs: [],
  });
  const [commentModal, setCommentModal] = useState<{ open: boolean; type: 'approve' | 'reject'; id: number } | null>(null);
  const [comment, setComment] = useState('');
  const [grayscaleModal, setGrayscaleModal] = useState<{ open: boolean; id: number } | null>(null);
  const [gsStrategy, setGsStrategy] = useState<'PERCENTAGE' | 'CONDITION'>('PERCENTAGE');
  const [gsPercentage, setGsPercentage] = useState(10);
  const [gsCondition, setGsCondition] = useState('');
  const [gsDescription, setGsDescription] = useState('');
  const [gsLibraries, setGsLibraries] = useState<LibraryData>({ variables: [], parameters: [] });

  const canApprove = usePermission('pack:publish:approve');
  const canSubmit = usePermission('pack:publish:submit');
  const currentUser = useAuthStore((s) => s.user?.username);
  const location = useLocation();
  const refreshTime = useTabStore((s) => s.refreshMap[location.pathname]);
  const [searchParams, setSearchParams] = useSearchParams();
  const projectsFetched = useRef(false);

  // 固定状态筛选
  const fixedStatus = useMemo(() => {
    switch (mode) {
      case 'pending': return 'PENDING';
      case 'pending-publish': return 'APPROVED,PUBLISH_FAILED';
      default: return undefined;
    }
  }, [mode]);

  // 固定提交人筛选
  const fixedSubmitter = useMemo(() => (mode === 'my' ? currentUser : undefined), [mode, currentUser]);

  // 加载项目列表 + 自动选中（URL参数优先，否则默认第一个）
  useEffect(() => {
    if (projectsFetched.current) return;
    projectsFetched.current = true;
    loadProjects().then((res) => {
      const list: { name: string }[] = res.data?.data ?? res.data ?? [];
      const names = list.map((p: any) => p.name ?? p);
      setProjects(names);
      const urlProject = searchParams.get('project');
      const initial = (urlProject && names.includes(urlProject)) ? urlProject
        : names.length > 0 ? names[0] : null;
      if (initial) {
        setProject(initial);
        if (initial !== urlProject) {
          setSearchParams({ project: initial }, { replace: true });
        }
      }
    });
  }, []);

  const fetchData = useCallback(async (p = page, proj = project, s = fixedStatus ?? statusFilter, sub = fixedSubmitter) => {
    if (!proj && mode !== 'my') return;
    setLoading(true);
    try {
      const res = await listApprovals({ project: proj ?? undefined, status: s, submitter: sub, page: p, pageSize: PAGE_SIZE });
      setData(res.data?.data?.items ?? res.data?.items ?? []);
      setTotal(res.data?.data?.total ?? res.data?.total ?? 0);
    } finally {
      setLoading(false);
    }
  }, [project, page, fixedStatus, statusFilter, fixedSubmitter, mode]);

  useEffect(() => { fetchData(); }, [fetchData, refreshTime]);

  const handleProjectChange = (v: string) => {
    setProject(v);
    setPage(1);
    setSearchParams({ project: v }, { replace: true });
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

  const handlePublish = async (id: number) => {
    try {
      await publishApproval(id);
      message.success('上线成功');
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const handleGrayscale = async () => {
    if (!grayscaleModal) return;
    try {
      await createGrayscaleRule({
        approvalId: grayscaleModal.id,
        strategy: gsStrategy,
        percentage: gsStrategy === 'PERCENTAGE' ? gsPercentage : undefined,
        conditionExpr: gsStrategy === 'CONDITION' ? gsCondition : undefined,
        description: gsDescription || undefined,
        operator: currentUser || undefined,
      });
      message.success('灰度发布已激活');
      setGrayscaleModal(null);
      setGsDescription('');
      setGsCondition('');
      setGsPercentage(10);
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const openGrayscaleModal = async (id: number, projectName: string) => {
    setGrayscaleModal({ open: true, id });
    setGsStrategy('PERCENTAGE');
    setGsPercentage(10);
    setGsCondition('');
    setGsDescription('');
    // 加载审批单所属项目的变量库（灰度弹窗变量提示用）
    try {
      const libs: ProjectLibs = await loadProjectLibs(projectName);
      const allFiles = [...libs.variable, ...libs.parameter];
      const loaded: LibraryData = { variables: [], parameters: [] };
      if (allFiles.length > 0) {
        const xmlData = await loadXml(allFiles.join(';'));
        for (const item of xmlData as any[]) {
          if (item.variables) loaded.variables.push(...item.variables);
          if (item.parameters) loaded.parameters.push(...item.parameters);
        }
      }
      setGsLibraries(loaded);
    } catch { /* 变量库加载失败不影响弹窗使用 */ }
  };

  const openDetail = async (record: ApprovalVO) => {
    try {
      const res = await getApprovalDetail(record.id);
      const detail: ApprovalVO = res.data?.data ?? res.data;
      setDiffDrawer({ open: true, approval: detail, diffs: detail.diffs ?? [] });
    } catch { /* request.ts 已处理 */ }
  };

  // 列定义
  const columns: ColumnsType<ApprovalVO> = useMemo(() => {
    const base: ColumnsType<ApprovalVO> = [
      { title: 'ID', dataIndex: 'id', width: 60 },
      { title: '知识包', dataIndex: 'packageName', ellipsis: true },
      {
        title: '状态', dataIndex: 'status', width: 100,
        render: (s: string) => { const m = STATUS_MAP[s]; return m ? <Tag color={m.color}>{m.label}</Tag> : s; },
      },
      { title: '提交人', dataIndex: 'submitter', width: 90 },
      {
        title: '提交时间', dataIndex: 'submittedAt', width: 170,
        render: (t: number) => t ? new Date(t).toLocaleString() : '-',
      },
    ];

    // 审批列：pending / all / my 都显示
    if (mode !== 'pending-publish') {
      base.push(
        { title: '审批人', dataIndex: 'approver', width: 90, render: (v: string) => v ?? '-' },
        {
          title: '审批时间', dataIndex: 'approvedAt', width: 170,
          render: (t: number) => t ? new Date(t).toLocaleString() : '-',
        },
      );
    }

    // 上线列：pending-publish / all / my 都显示
    if (mode !== 'pending') {
      base.push(
        { title: '上线人', dataIndex: 'publisher', width: 90, render: (v: string) => v ?? '-' },
        {
          title: '上线时间', dataIndex: 'publishedAt', width: 170,
          render: (t: number) => t ? new Date(t).toLocaleString() : '-',
        },
      );
    }

    // 失败原因
    if (mode === 'pending-publish' || mode === 'all') {
      base.push({
        title: '失败原因', dataIndex: 'failReason', width: 200, ellipsis: true,
        render: (v: string) => v ? <span style={{ color: '#ff4d4f' }}>{v}</span> : '-',
      });
    }

    // 操作列
    base.push({
      title: '操作', width: 200, fixed: 'right',
      render: (_: any, record: ApprovalVO) => (
        <Space size="small">
          <Button size="small" onClick={() => openDetail(record)}>查看</Button>
          {/* 待审核：审批操作 */}
          {canApprove && record.status === 'PENDING' && (mode === 'pending' || mode === 'all') && (
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
          {/* 待上线：上线操作 */}
          {canSubmit && (record.status === 'APPROVED' || record.status === 'PUBLISH_FAILED')
            && (mode === 'pending-publish' || mode === 'all') && (
            <>
              <Popconfirm
                title={record.status === 'PUBLISH_FAILED' ? '确认重新上线？' : '确认上线？'}
                onConfirm={() => handlePublish(record.id)}
                okText="确认"
                cancelText="取消"
              >
                <Button size="small" type="primary">
                  {record.status === 'PUBLISH_FAILED' ? '重新上线' : '上线'}
                </Button>
              </Popconfirm>
              {record.status === 'APPROVED' && (
                <Button size="small"
                  onClick={() => openGrayscaleModal(record.id, record.project)}
                >
                  灰度发布
                </Button>
              )}
            </>
          )}
        </Space>
      ),
    });

    return base;
  }, [mode, canApprove, canSubmit]);

  // diff 分组
  const groupedDiffs = diffDrawer.diffs.reduce<Record<string, ApprovalDiffItem[]>>((acc, d) => {
    const type = d.componentType || '未知';
    if (!acc[type]) acc[type] = [];
    acc[type].push(d);
    return acc;
  }, {});

  // 筛选器
  const showStatusFilter = mode === 'my' || mode === 'all';

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="选择项目"
          showSearch
          style={{ width: 200 }}
          value={project}
          onChange={handleProjectChange}
        >
          {projects.map(p => (
            <Select.Option key={p} value={p}>{p}</Select.Option>
          ))}
        </Select>
        {showStatusFilter && (
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
        )}
      </Space>

      <Table<ApprovalVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{ current: page, total, pageSize: PAGE_SIZE, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        scroll={{ x: 1200 }}
        size="middle"
      />

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

      {/* 灰度发布配置弹窗 */}
      <Modal
        title="灰度发布配置"
        open={!!grayscaleModal?.open}
        onOk={handleGrayscale}
        onCancel={() => setGrayscaleModal(null)}
        okText="启动灰度"
      >
        <div style={{ marginBottom: 16 }}>
          <Radio.Group value={gsStrategy} onChange={(e) => setGsStrategy(e.target.value)}>
            <Radio value="PERCENTAGE">流量比例</Radio>
            <Radio value="CONDITION">条件匹配</Radio>
          </Radio.Group>
        </div>
        {gsStrategy === 'PERCENTAGE' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8 }}>灰度流量比例：<strong>{gsPercentage}%</strong></div>
            <Slider min={1} max={100} value={gsPercentage} onChange={setGsPercentage} />
          </div>
        )}
        {gsStrategy === 'CONDITION' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, color: '#666', fontSize: 12 }}>
              条件表达式（REA 语法），满足条件的请求路由到灰度版本
            </div>
            <ReaConditionInput
              value={gsCondition}
              onChange={setGsCondition}
              libraries={gsLibraries}
              placeholder='例: FlightInfo.level == "VIP" AND FlightInfo.score > 5'
            />
          </div>
        )}
        <Input.TextArea
          rows={2}
          maxLength={500}
          showCount
          placeholder="灰度说明（选填）"
          value={gsDescription}
          onChange={(e) => setGsDescription(e.target.value)}
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
              {diffDrawer.approval.approver && (
                <Descriptions.Item label="审批人">{diffDrawer.approval.approver}</Descriptions.Item>
              )}
              {diffDrawer.approval.approvedAt && (
                <Descriptions.Item label="审批时间">
                  {new Date(diffDrawer.approval.approvedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {diffDrawer.approval.publisher && (
                <Descriptions.Item label="上线人">{diffDrawer.approval.publisher}</Descriptions.Item>
              )}
              {diffDrawer.approval.publishedAt && (
                <Descriptions.Item label="上线时间">
                  {new Date(diffDrawer.approval.publishedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {diffDrawer.approval.description && (
                <Descriptions.Item label="变更说明" span={2}>{diffDrawer.approval.description}</Descriptions.Item>
              )}
              {diffDrawer.approval.comment && (
                <Descriptions.Item label="审批意见" span={2}>{diffDrawer.approval.comment}</Descriptions.Item>
              )}
              {diffDrawer.approval.failReason && (
                <Descriptions.Item label="失败原因" span={2}>
                  <span style={{ color: '#ff4d4f' }}>{diffDrawer.approval.failReason}</span>
                </Descriptions.Item>
              )}
            </Descriptions>

            {/* 测试结果摘要 */}
            {diffDrawer.approval.status === 'TESTING' ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 4 }}>
                <strong>自动测试</strong><span style={{ marginLeft: 12 }}>执行中，完成后自动进入待审核...</span>
              </div>
            ) : diffDrawer.approval.testSummary ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', borderRadius: 4,
                background: diffDrawer.approval.testSummary.failedCases > 0 ? '#fff2f0' : '#f6ffed',
                border: `1px solid ${diffDrawer.approval.testSummary.failedCases > 0 ? '#ffccc7' : '#b7eb8f'}` }}>
                <strong>自动测试</strong>
                <span style={{ marginLeft: 12 }}>
                  共 {diffDrawer.approval.testSummary.totalCases} 条，
                  <span style={{ color: '#389e0d' }}>通过 {diffDrawer.approval.testSummary.passedCases}</span>
                  {diffDrawer.approval.testSummary.failedCases > 0 && (
                    <span style={{ color: '#cf1322', marginLeft: 8 }}>失败 {diffDrawer.approval.testSummary.failedCases}</span>
                  )}
                </span>
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/autotest/report/${diffDrawer.approval!.testSummary!.runId}`, '_blank')}>
                  查看报告
                </Button>
              </div>
            ) : diffDrawer.approval.testRunId ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4 }}>
                <strong>自动测试</strong>
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/autotest/report/${diffDrawer.approval!.testRunId}`, '_blank')}>
                  查看报告
                </Button>
              </div>
            ) : (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f5f5f5', border: '1px solid #d9d9d9', borderRadius: 4 }}>
                <strong>自动测试</strong><span style={{ marginLeft: 12, color: '#999' }}>无用例包，未执行测试</span>
              </div>
            )}

            {Object.entries(groupedDiffs).map(([type, items]) => (
              <div key={type} style={{ marginBottom: 16 }}>
                <strong>{type}（{items.length} 项变更）</strong>
                <Table<ApprovalDiffItem>
                  size="small"
                  rowKey="id"
                  pagination={false}
                  dataSource={items}
                  expandable={{
                    rowExpandable: (r) => !!r.details,
                    expandedRowRender: (r) => {
                      if (!r.details) return null;
                      try {
                        const rules: RuleDiffDetail[] = JSON.parse(r.details);
                        return (
                          <div style={{ padding: '4px 0' }}>
                            {rules.map((rd, i) => (
                              <div key={i} style={{ padding: '4px 8px', fontSize: 13, borderBottom: i < rules.length - 1 ? '1px solid #f0f0f0' : undefined }}>
                                <div style={{ marginBottom: 4 }}>
                                  <Tag color={rd.change === 'ADDED' ? 'green' : rd.change === 'DELETED' ? 'red' : 'blue'}
                                    style={{ marginRight: 8 }}>
                                    {rd.change === 'ADDED' ? '新增' : rd.change === 'DELETED' ? '删除' : '修改'}
                                  </Tag>
                                  <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{rd.rule}</span>
                                </div>
                                {rd.fields && rd.fields.length > 0 && (
                                  <table style={{ marginLeft: 24, fontSize: 12, borderCollapse: 'collapse' }}>
                                    <thead>
                                      <tr style={{ color: '#999' }}>
                                        <td style={{ padding: '2px 12px 2px 0' }}>字段</td>
                                        <td style={{ padding: '2px 12px 2px 0' }}>变更前</td>
                                        <td style={{ padding: '2px 12px 2px 0' }}>变更后</td>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      {rd.fields.map((f: FieldDiff, j: number) => (
                                        <tr key={j}>
                                          <td style={{ padding: '2px 12px 2px 0', fontFamily: 'monospace' }}>{f.field}</td>
                                          <td style={{ padding: '2px 12px 2px 0', color: '#cf1322', background: f.prev ? '#fff1f0' : undefined }}>
                                            {f.prev ?? '-'}
                                          </td>
                                          <td style={{ padding: '2px 12px 2px 0', color: '#389e0d', background: f.curr ? '#f6ffed' : undefined }}>
                                            {f.curr ?? '-'}
                                          </td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                )}
                              </div>
                            ))}
                            {rules.length === 0 && <span style={{ color: '#999' }}>无具体变动</span>}
                          </div>
                        );
                      } catch { return <span style={{ color: '#999' }}>解析失败</span>; }
                    },
                  }}
                  columns={[
                    { title: '组件', dataIndex: 'componentName', ellipsis: true },
                    { title: '变更', dataIndex: 'changeType', width: 70,
                      render: (t: string) => {
                        const m = CHANGE_TYPE_MAP[t];
                        return m ? <Tag color={m.color}>{m.label}</Tag> : t;
                      },
                    },
                    {
                      title: '规则变动', width: 200,
                      render: (_: any, r: ApprovalDiffItem) => {
                        if (!r.details) return '-';
                        try {
                          const rules: RuleDiffDetail[] = JSON.parse(r.details);
                          const added = rules.filter(x => x.change === 'ADDED').length;
                          const mod = rules.filter(x => x.change === 'MODIFIED').length;
                          const del = rules.filter(x => x.change === 'DELETED').length;
                          const parts: string[] = [];
                          if (added) parts.push(`${added}新增`);
                          if (mod) parts.push(`${mod}修改`);
                          if (del) parts.push(`${del}删除`);
                          return parts.length > 0 ? <span style={{ fontSize: 13 }}>{parts.join('、')}</span> : '-';
                        } catch { return '-'; }
                      },
                    },
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

export default ReleaseListPage;

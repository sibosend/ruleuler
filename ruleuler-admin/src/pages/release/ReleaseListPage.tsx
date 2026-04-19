import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Button, Table, Tag, Space, Select, Drawer, Modal, Input, Slider, Radio,
  message, Descriptions, Popconfirm,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useLocation, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  listApprovals, getApprovalDetail, approveApproval, rejectApproval,
  publishApproval,
  type ApprovalVO, type ApprovalDiffItem, type RuleDiffDetail, type FieldDiff,
} from '@/api/approval';
import { createGrayscaleRule, listGrayscaleRules } from '@/api/grayscale';
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
  TESTING: { color: 'processing', label: 'release.statusTesting' },
  PENDING: { color: 'blue', label: 'release.statusPending' },
  APPROVED: { color: 'cyan', label: 'release.statusApproved' },
  GRAYSCALE: { color: 'purple', label: 'release.statusGrayscale' },
  REJECTED: { color: 'red', label: 'release.statusRejected' },
  PUBLISH_FAILED: { color: 'orange', label: 'release.statusPublishFailed' },
  PUBLISHED: { color: 'green', label: 'release.statusPublished' },
};

const CHANGE_TYPE_MAP: Record<string, { color: string; label: string }> = {
  ADDED: { color: 'green', label: 'release.changeAdded' },
  MODIFIED: { color: 'blue', label: 'release.changeModified' },
  DELETED: { color: 'red', label: 'release.changeDeleted' },
};

const PAGE_SIZE = 20;

const ReleaseListPage: React.FC<Props> = ({ mode }) => {
  const { t, i18n } = useTranslation();
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

  // TESTING 状态下 5 秒轮询审批详情（更新 autotest + replay 进度）
  useEffect(() => {
    if (!diffDrawer.open || !diffDrawer.approval || diffDrawer.approval.status !== 'TESTING') return;
    const timer = setInterval(async () => {
      try {
        const res = await getApprovalDetail(diffDrawer.approval!.id);
        const detail: ApprovalVO = res.data?.data ?? res.data;
        setDiffDrawer(prev => ({ ...prev, approval: detail, diffs: detail.diffs ?? prev.diffs }));
        if (detail.status !== 'TESTING') clearInterval(timer);
      } catch { /* ignore */ }
    }, 5000);
    return () => clearInterval(timer);
  }, [diffDrawer.open, diffDrawer.approval?.id, diffDrawer.approval?.status]);

  // 固定状态筛选
  const fixedStatus = useMemo(() => {
    switch (mode) {
      case 'pending': return 'PENDING';
      case 'pending-publish': return 'APPROVED,PUBLISH_FAILED,GRAYSCALE';
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
        message.success(t('release.approveSuccess'));
      } else {
        await rejectApproval(commentModal.id, comment || undefined);
        message.success(t('release.rejectSuccess'));
      }
      setCommentModal(null);
      setComment('');
      fetchData();
    } catch { /* request.ts 已处理 */ }
  };

  const handlePublish = async (id: number) => {
    try {
      const approval = data.find(a => a.id === id);
      if (approval) {
        const gsRes = await listGrayscaleRules({ project: approval.project, packageId: approval.packageId, status: 'ACTIVE' });
        const gsItems: any[] = gsRes.data?.data?.items ?? gsRes.data?.items ?? [];
        if (gsItems.length > 0) {
          Modal.warning({
            title: t('release.grayscaleActiveWarning'),
            content: t('release.grayscaleActiveDesc', { id: gsItems[0].id }),
          });
          return;
        }
      }
      await publishApproval(id);
      message.success(t('release.publishSuccess'));
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
      message.success(t('release.grayscaleActivated'));
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
      { title: t('release.knowledgePackage'), dataIndex: 'packageName', ellipsis: true },
      {
        title: t('common.status'), dataIndex: 'status', width: 100,
        render: (s: string) => { const m = STATUS_MAP[s]; return m ? <Tag color={m.color}>{t(m.label)}</Tag> : s; },
      },
      { title: t('release.submitter'), dataIndex: 'submitter', width: 90 },
      {
        title: t('release.submitTime'), dataIndex: 'submittedAt', width: 170,
        render: (v: number) => v ? new Date(v).toLocaleString() : '-',
      },
    ];

    // 审批列：pending / all / my 都显示
    if (mode !== 'pending-publish') {
      base.push(
        { title: t('release.approver'), dataIndex: 'approver', width: 90, render: (v: string) => v ?? '-' },
        {
          title: t('release.approveTime'), dataIndex: 'approvedAt', width: 170,
          render: (v: number) => v ? new Date(v).toLocaleString() : '-',
        },
      );
    }

    // 上线列：pending-publish / all / my 都显示
    if (mode !== 'pending') {
      base.push(
        { title: t('release.publisher'), dataIndex: 'publisher', width: 90, render: (v: string) => v ?? '-' },
        {
          title: t('release.publishTime'), dataIndex: 'publishedAt', width: 170,
          render: (v: number) => v ? new Date(v).toLocaleString() : '-',
        },
      );
    }

    // 失败原因
    if (mode === 'pending-publish' || mode === 'all') {
      base.push({
        title: t('release.failReason'), dataIndex: 'failReason', width: 200, ellipsis: true,
        render: (v: string) => v ? <span style={{ color: '#ff4d4f' }}>{v}</span> : '-',
      });
    }

    // 操作列
    base.push({
      title: t('common.operation'), width: 200, fixed: 'right',
      render: (_: any, record: ApprovalVO) => (
        <Space size="small">
          <Button size="small" onClick={() => openDetail(record)}>{t('release.viewDetail')}</Button>
          {/* 待审核：审批操作 */}
          {canApprove && record.status === 'PENDING' && (mode === 'pending' || mode === 'all') && (
            <>
              <Button size="small" type="primary"
                onClick={() => { setCommentModal({ open: true, type: 'approve', id: record.id }); setComment(''); }}>
                {t('release.approveAction')}
              </Button>
              <Button size="small" danger
                onClick={() => { setCommentModal({ open: true, type: 'reject', id: record.id }); setComment(''); }}>
                {t('release.rejectAction')}
              </Button>
            </>
          )}
          {/* 待上线：上线操作 */}
          {canSubmit && (record.status === 'APPROVED' || record.status === 'PUBLISH_FAILED')
            && (mode === 'pending-publish' || mode === 'all') && (
            <>
              <Popconfirm
                title={record.status === 'PUBLISH_FAILED' ? t('release.confirmRepublish') : t('release.confirmPublish')}
                onConfirm={() => handlePublish(record.id)}
                okText={t('common.confirm')}
                cancelText={t('common.cancel')}
              >
                <Button size="small" type="primary">
                  {record.status === 'PUBLISH_FAILED' ? t('release.republish') : t('release.publishBtn')}
                </Button>
              </Popconfirm>
              {record.status === 'APPROVED' && (
                <Button size="small"
                  onClick={() => openGrayscaleModal(record.id, record.project)}
                >
                  {t('release.grayscaleBtn')}
                </Button>
              )}
            </>
          )}
        </Space>
      ),
    });

    return base;
  }, [mode, canApprove, canSubmit, i18n.language]);

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
          placeholder={t('common.selectProject')}
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
            placeholder={t('release.statusFilter')}
            allowClear
            style={{ width: 140 }}
            value={statusFilter}
            onChange={(v) => { setStatusFilter(v); setPage(1); }}
          >
            {Object.entries(STATUS_MAP).map(([k, v]) => (
              <Select.Option key={k} value={k}>{t(v.label)}</Select.Option>
            ))}
          </Select>
        )}
      </Space>

      <Table<ApprovalVO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{ current: page, total, pageSize: PAGE_SIZE, onChange: setPage, showTotal: (total) => t('common.total', { count: total }) }}
        scroll={{ x: 1200 }}
        size="middle"
      />

      {/* 审批意见弹窗 */}
      <Modal
        title={commentModal?.type === 'approve' ? t('release.approveAction') : t('release.rejectAction')}
        open={!!commentModal?.open}
        onOk={handleApprove}
        onCancel={() => { setCommentModal(null); setComment(''); }}
      >
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          placeholder={t('release.commentPlaceholder')}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
      </Modal>

      {/* 灰度发布配置弹窗 */}
      <Modal
        title={t('release.grayscaleConfig')}
        open={!!grayscaleModal?.open}
        onOk={handleGrayscale}
        onCancel={() => setGrayscaleModal(null)}
        okText={t('release.startGrayscale')}
      >
        <div style={{ marginBottom: 16 }}>
          <Radio.Group value={gsStrategy} onChange={(e) => setGsStrategy(e.target.value)}>
            <Radio value="PERCENTAGE">{t('release.percentage')}</Radio>
            <Radio value="CONDITION">{t('release.conditionMatch')}</Radio>
          </Radio.Group>
        </div>
        {gsStrategy === 'PERCENTAGE' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8 }}>{t('release.grayscaleRatio', { percent: gsPercentage })}</div>
            <div style={{ marginBottom: 8, color: '#999', fontSize: 12 }}>
              {t('release.grayscaleRatioDesc')}
            </div>
            <Slider min={1} max={100} value={gsPercentage} onChange={setGsPercentage} />
          </div>
        )}
        {gsStrategy === 'CONDITION' && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ marginBottom: 8, color: '#666', fontSize: 12 }}>
              {t('release.conditionDesc')}
            </div>
            <ReaConditionInput
              value={gsCondition}
              onChange={setGsCondition}
              libraries={gsLibraries}
              placeholder={t('release.conditionPlaceholder')}
            />
          </div>
        )}
        <Input.TextArea
          rows={2}
          maxLength={500}
          showCount
          placeholder={t('release.grayscaleDescPlaceholder')}
          value={gsDescription}
          onChange={(e) => setGsDescription(e.target.value)}
        />
      </Modal>

      {/* Diff 详情抽屉 */}
      <Drawer
        title={t('release.approvalDetail', { id: diffDrawer.approval?.id ?? '' })}
        width={640}
        open={diffDrawer.open}
        onClose={() => setDiffDrawer({ open: false, approval: null, diffs: [] })}
      >
        {diffDrawer.approval && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label={t('release.knowledgePackage')}>{diffDrawer.approval.packageName}</Descriptions.Item>
              <Descriptions.Item label={t('common.status')}>
                <Tag color={STATUS_MAP[diffDrawer.approval.status]?.color}>
                  {t(STATUS_MAP[diffDrawer.approval.status]?.label ?? diffDrawer.approval.status)}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('release.submitter')}>{diffDrawer.approval.submitter}</Descriptions.Item>
              <Descriptions.Item label={t('release.submitTime')}>
                {diffDrawer.approval.submittedAt ? new Date(diffDrawer.approval.submittedAt).toLocaleString() : '-'}
              </Descriptions.Item>
              {diffDrawer.approval.approver && (
                <Descriptions.Item label={t('release.approver')}>{diffDrawer.approval.approver}</Descriptions.Item>
              )}
              {diffDrawer.approval.approvedAt && (
                <Descriptions.Item label={t('release.approveTime')}>
                  {new Date(diffDrawer.approval.approvedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {diffDrawer.approval.publisher && (
                <Descriptions.Item label={t('release.publisher')}>{diffDrawer.approval.publisher}</Descriptions.Item>
              )}
              {diffDrawer.approval.publishedAt && (
                <Descriptions.Item label={t('release.publishTime')}>
                  {new Date(diffDrawer.approval.publishedAt).toLocaleString()}
                </Descriptions.Item>
              )}
              {diffDrawer.approval.description && (
                <Descriptions.Item label={t('release.changeDescription')} span={2}>{diffDrawer.approval.description}</Descriptions.Item>
              )}
              {diffDrawer.approval.comment && (
                <Descriptions.Item label={t('release.approvalComment')} span={2}>{diffDrawer.approval.comment}</Descriptions.Item>
              )}
              {diffDrawer.approval.failReason && (
                <Descriptions.Item label={t('release.failReason')} span={2}>
                  <span style={{ color: '#ff4d4f' }}>{diffDrawer.approval.failReason}</span>
                </Descriptions.Item>
              )}
            </Descriptions>

            {/* 测试结果摘要 */}
            {diffDrawer.approval.status === 'TESTING' ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 4 }}>
                <strong>{t('release.autoTest')}</strong><span style={{ marginLeft: 12 }}>{t('release.testRunning')}</span>
              </div>
            ) : diffDrawer.approval.testSummary ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', borderRadius: 4,
                background: diffDrawer.approval.testSummary.failedCases > 0 ? '#fff2f0' : '#f6ffed',
                border: `1px solid ${diffDrawer.approval.testSummary.failedCases > 0 ? '#ffccc7' : '#b7eb8f'}` }}>
                <strong>{t('release.autoTest')}</strong>
                <span style={{ marginLeft: 12 }}>
                  {t('release.testTotal', {
                    total: diffDrawer.approval.testSummary.totalCases,
                    passed: diffDrawer.approval.testSummary.passedCases,
                    failed: diffDrawer.approval.testSummary.failedCases,
                  })}
                </span>
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/autotest/report/${diffDrawer.approval!.testSummary!.runId}`, '_blank')}>
                  {t('release.viewReport')}
                </Button>
              </div>
            ) : diffDrawer.approval.testRunId ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4 }}>
                <strong>{t('release.autoTest')}</strong>
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/autotest/report/${diffDrawer.approval!.testRunId}`, '_blank')}>
                  {t('release.viewReport')}
                </Button>
              </div>
            ) : (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f5f5f5', border: '1px solid #d9d9d9', borderRadius: 4 }}>
                <strong>{t('release.autoTest')}</strong><span style={{ marginLeft: 12, color: '#999' }}>{t('release.noTestPack')}</span>
              </div>
            )}

            {/* 回放结果摘要 */}
            {diffDrawer.approval.status === 'TESTING' && diffDrawer.approval.replayTaskId ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#e6f7ff', border: '1px solid #91d5ff', borderRadius: 4 }}>
                <strong>{t('release.replayTest')}</strong><span style={{ marginLeft: 12 }}>{t('release.replayRunning')}</span>
              </div>
            ) : diffDrawer.approval.replaySummary ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', borderRadius: 4,
                background: diffDrawer.approval.replaySummary.mismatchCount > 0 ? '#fff2f0' : '#f6ffed',
                border: `1px solid ${diffDrawer.approval.replaySummary.mismatchCount > 0 ? '#ffccc7' : '#b7eb8f'}` }}>
                <strong>{t('release.replayTest')}</strong>
                <span style={{ marginLeft: 12 }}>
                  {t('release.replayTotal', {
                    total: diffDrawer.approval.replaySummary.totalCount,
                    match: diffDrawer.approval.replaySummary.matchCount,
                    mismatch: diffDrawer.approval.replaySummary.mismatchCount,
                    errors: diffDrawer.approval.replaySummary.errorCount,
                  })}
                </span>
                {diffDrawer.approval.replaySummary.totalCount > 0 && (
                  <span style={{ marginLeft: 8 }}>
                    {t('release.replayConsistencyRate')}:
                    {(diffDrawer.approval.replaySummary.matchCount / diffDrawer.approval.replaySummary.totalCount * 100).toFixed(1)}%
                  </span>
                )}
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/projects/${diffDrawer.approval!.project}/replay`, '_blank')}>
                  {t('release.viewReport')}
                </Button>
              </div>
            ) : diffDrawer.approval.replayTaskId ? (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#fffbe6', border: '1px solid #ffe58f', borderRadius: 4 }}>
                <strong>{t('release.replayTest')}</strong>
                <Button size="small" type="link"
                  onClick={() => window.open(`/admin/projects/${diffDrawer.approval!.project}/replay`, '_blank')}>
                  {t('release.viewReport')}
                </Button>
              </div>
            ) : (
              <div style={{ marginBottom: 16, padding: '8px 12px', background: '#f5f5f5', border: '1px solid #d9d9d9', borderRadius: 4 }}>
                <strong>{t('release.replayTest')}</strong><span style={{ marginLeft: 12, color: '#999' }}>{t('release.noReplayTask')}</span>
              </div>
            )}

            {Object.entries(groupedDiffs).map(([type, items]) => (
              <div key={type} style={{ marginBottom: 16 }}>
                <strong>{t('release.itemsChanged', { type, count: items.length })}</strong>
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
                                    {rd.change === 'ADDED' ? t('release.changeAdded') : rd.change === 'DELETED' ? t('release.changeDeleted') : t('release.changeModified')}
                                  </Tag>
                                  <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{rd.rule}</span>
                                </div>
                                {rd.fields && rd.fields.length > 0 && (
                                  <table style={{ marginLeft: 24, fontSize: 12, borderCollapse: 'collapse' }}>
                                    <thead>
                                      <tr style={{ color: '#999' }}>
                                        <td style={{ padding: '2px 12px 2px 0' }}>{t('release.field')}</td>
                                        <td style={{ padding: '2px 12px 2px 0' }}>{t('release.before')}</td>
                                        <td style={{ padding: '2px 12px 2px 0' }}>{t('release.after')}</td>
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
                            {rules.length === 0 && <span style={{ color: '#999' }}>{t('release.noSpecificChange')}</span>}
                          </div>
                        );
                      } catch { return <span style={{ color: '#999' }}>{t('release.parseFailed')}</span>; }
                    },
                  }}
                  columns={[
                    { title: t('release.component'), dataIndex: 'componentName', ellipsis: true },
                    { title: t('release.change'), dataIndex: 'changeType', width: 70,
                      render: (v: string) => {
                        const m = CHANGE_TYPE_MAP[v];
                        return m ? <Tag color={m.color}>{t(m.label)}</Tag> : v;
                      },
                    },
                    {
                      title: t('release.ruleChange'), width: 200,
                      render: (_: any, r: ApprovalDiffItem) => {
                        if (!r.details) return '-';
                        try {
                          const rules: RuleDiffDetail[] = JSON.parse(r.details);
                          const added = rules.filter(x => x.change === 'ADDED').length;
                          const mod = rules.filter(x => x.change === 'MODIFIED').length;
                          const del = rules.filter(x => x.change === 'DELETED').length;
                          const parts: string[] = [];
                          if (added) parts.push(t('release.changeCount', { count: added, type: t('release.changeAdded') }));
                          if (mod) parts.push(t('release.changeCount', { count: mod, type: t('release.changeModified') }));
                          if (del) parts.push(t('release.changeCount', { count: del, type: t('release.changeDeleted') }));
                          return parts.length > 0 ? <span style={{ fontSize: 13 }}>{parts.join(t('release.comma'))}</span> : '-';
                        } catch { return '-'; }
                      },
                    },
                  ]}
                />
              </div>
            ))}

            {diffDrawer.diffs.length === 0 && (
              <div style={{ textAlign: 'center', color: '#999', padding: 24 }}>{t('release.noChange')}</div>
            )}
          </>
        )}
      </Drawer>
    </div>
  );
};

export default ReleaseListPage;

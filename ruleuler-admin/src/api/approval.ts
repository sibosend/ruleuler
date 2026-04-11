import request from './request';

export interface RuleDiffDetail {
  rule: string;
  change: 'ADDED' | 'MODIFIED' | 'DELETED';
}

export interface ApprovalDiffItem {
  id: number;
  approvalId: number;
  componentPath: string;
  componentName: string;
  componentType: string;
  changeType: 'ADDED' | 'MODIFIED' | 'DELETED';
  prevVersion: string | null;
  currVersion: string | null;
  details: string | null; // JSON: RuleDiffDetail[]
}

export interface ApprovalVO {
  id: number;
  project: string;
  packageId: string;
  packageName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'PUBLISH_FAILED' | 'PUBLISHED';
  submitter: string;
  comment: string | null;
  failReason: string | null;
  publisher: string | null;
  submittedAt: number;
  approver: string | null;
  approvedAt: number | null;
  publishedAt: number | null;
  diffs?: ApprovalDiffItem[];
}

export interface ApprovalListResult {
  items: ApprovalVO[];
  total: number;
}

export function submitApproval(data: { project: string; packageId: string }) {
  return request.post('/api/approvals', data);
}

export function listApprovals(params: {
  project?: string;
  packageId?: string;
  status?: string;
  submitter?: string;
  page?: number;
  pageSize?: number;
}) {
  return request.get('/api/approvals', { params });
}

export function getApprovalDetail(id: number) {
  return request.get(`/api/approvals/${id}`);
}

export function approveApproval(id: number, comment?: string) {
  return request.put(`/api/approvals/${id}/approve`, { comment });
}

export function rejectApproval(id: number, comment?: string) {
  return request.put(`/api/approvals/${id}/reject`, { comment });
}

export function publishApproval(id: number) {
  return request.put(`/api/approvals/${id}/publish`);
}

export function listPackages(project: string) {
  return request.get(`/api/projects/${encodeURIComponent(project)}/packages`);
}

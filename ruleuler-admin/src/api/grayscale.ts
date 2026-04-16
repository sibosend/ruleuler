import request from './request';

export interface GrayscaleRuleVO {
  id: number;
  project: string;
  packageId: string;
  approvalId: number;
  snapshotId: number;
  strategy: 'PERCENTAGE' | 'CONDITION';
  percentage?: number;
  conditionExpr?: string;
  status: 'ACTIVE' | 'ROLLED_OUT' | 'ROLLED_BACK';
  description?: string;
  createdBy: string;
  createdAt: number;
  updatedAt: number;
}

export function createGrayscaleRule(params: {
  approvalId: number;
  strategy: 'PERCENTAGE' | 'CONDITION';
  percentage?: number;
  conditionExpr?: string;
  description?: string;
  operator?: string;
}) {
  return request.post('/api/grayscale/rules', params);
}

export function listGrayscaleRules(params: {
  project?: string;
  packageId?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}) {
  return request.get('/api/grayscale/rules', { params });
}

export function fullRolloutGrayscale(ruleId: number, operator?: string) {
  return request.put(`/api/grayscale/rules/${ruleId}/rollout`, { operator });
}

export function rollbackGrayscale(ruleId: number, operator?: string) {
  return request.put(`/api/grayscale/rules/${ruleId}/rollback`, { operator });
}

export function getGrayscaleMetrics(ruleId: number, startDate?: string, endDate?: string) {
  return request.get(`/api/grayscale/rules/${ruleId}/metrics`, { params: { startDate, endDate } });
}

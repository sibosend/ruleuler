import request from './request';

export interface ReplayTaskVO {
  id: number;
  project: string;
  packageId: string;
  flowId: string | null;
  trafficQuery: string;
  sampleStrategy: string;
  sampleSize: number;
  missingVarStrategy: string;
  totalCount: number;
  executedCount: number;
  matchCount: number;
  mismatchCount: number;
  errorCount: number;
  incompleteCount: number;
  status: string;
  toleranceConfig: string | null;
  startedAt: number | null;
  finishedAt: number | null;
  createdAt: number;
}

export interface ReplaySessionVO {
  id: number;
  taskId: number;
  originalExecutionId: string;
  replayInput: string | null;
  originalOutput: string | null;
  replayOutput: string | null;
  diffResult: string | null;
  missingCategories: string | null;
  missingVariables: string | null;
  filledVariables: string | null;
  completenessStatus: string;
  execMs: number | null;
  originalExecMs: number | null;
  status: string;
  errorMessage: string | null;
  createdAt: number;
}

export interface FieldDiffVO {
  category: string;
  name: string;
  status: string;
  oldValue: unknown;
  newValue: unknown;
}

export interface ReplayReportVO {
  task: ReplayTaskVO;
  summary: {
    totalCount: number;
    matchCount: number;
    mismatchCount: number;
    errorCount: number;
    incompleteCount: number;
    matchRate: number;
  };
  variableStats: Array<{
    category: string;
    name: string;
    changeCount: number;
    totalCompared: number;
    changeRate: number;
  }>;
  timing: {
    originalAvgMs: number;
    replayAvgMs: number;
    originalP50Ms: number;
    replayP50Ms: number;
    originalP95Ms: number;
    replayP95Ms: number;
  };
}

export function createReplayTask(data: {
  project: string;
  packageId: string;
  flowId?: string;
  startTime?: number;
  endTime?: number;
  sampleStrategy?: string;
  sampleSize?: number;
  missingVarStrategy?: string;
}) {
  return request.post('/api/replay/tasks', data);
}

export function listReplayTasks(params: {
  project?: string;
  packageId?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}) {
  return request.get('/api/replay/tasks', { params });
}

export function getReplayTask(taskId: number) {
  return request.get(`/api/replay/tasks/${taskId}`);
}

export function getReplaySessions(
  taskId: number,
  params: { status?: string; match?: boolean; page?: number; pageSize?: number },
) {
  return request.get(`/api/replay/tasks/${taskId}/sessions`, { params });
}

export function getReplayReport(taskId: number) {
  return request.get(`/api/replay/tasks/${taskId}/report`);
}

export function exportReplayTask(taskId: number, scope: string) {
  return request.post(`/api/replay/tasks/${taskId}/export`, { scope });
}

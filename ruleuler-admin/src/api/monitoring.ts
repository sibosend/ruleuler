import request from './request';

// 变量列表 + 最新指标摘要
export async function fetchVariables(params: {
  project: string;
  packageId: string;
  ioType?: string;
  showAll?: boolean;
}) {
  const res = await request.get('/api/monitoring/variables', { params });
  return res.data.data;
}

// 趋势数据
export async function fetchTrend(params: {
  project: string;
  packageId: string;
  varCategory: string;
  varName: string;
  ioType?: string;
  startDate?: string;
  endDate?: string;
}) {
  const res = await request.get('/api/monitoring/trend', { params });
  return res.data.data;
}

// 周期对比
export async function fetchCompare(body: {
  project: string;
  packageId: string;
  variables: Array<{ varCategory: string; varName: string; ioType: string }>;
  periodA: { start: string; end: string };
  periodB: { start: string; end: string };
}) {
  const res = await request.post('/api/monitoring/compare', body);
  return res.data.data;
}

// 执行记录分页
export async function fetchExecutions(params: {
  project: string;
  packageId: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  pageSize?: number;
}) {
  const res = await request.get('/api/monitoring/executions', { params });
  return res.data.data;
}

// 执行明细
export async function fetchExecutionDetail(id: string) {
  const res = await request.get(`/api/monitoring/executions/${id}`);
  return res.data.data;
}

// 实时大盘
export async function fetchRealtimeDashboard(params: {
  project: string;
  packageId: string;
}) {
  const res = await request.get('/api/monitoring/realtime/dashboard', { params });
  return res.data.data;
}

// 实时变量列表
export async function fetchRealtimeVariables(params: {
  project: string;
  packageId: string;
  ioType?: string;
  sortBy?: string;
}) {
  const res = await request.get('/api/monitoring/realtime/variables', { params });
  return res.data.data;
}

// 实时变量列表（含 DoD/WoW 对比）
export async function fetchRealtimeVariablesWithComparison(params: {
  project: string;
  packageId: string;
  ioType?: string;
  sortBy?: string;
  date?: string;
}) {
  const res = await request.get('/api/monitoring/realtime/variables-with-comparison', { params });
  return res.data.data;
}

// PSI 分布稳定性指数
export async function fetchRealtimePsi(params: {
  project: string;
  packageId: string;
  varCategory: string;
  varName: string;
  ioType?: string;
}) {
  const res = await request.get('/api/monitoring/realtime/psi', { params });
  return res.data.data;
}

// 枚举漂移检测
export async function fetchRealtimeEnumDrift(params: {
  project: string;
  packageId: string;
  ioType?: string;
  date?: string;
}) {
  const res = await request.get('/api/monitoring/realtime/enum-drift', { params });
  return res.data.data;
}

// 实时缺失率趋势
export async function fetchRealtimeMissingRateTrend(params: {
  project: string;
  packageId: string;
  varCategory: string;
  varName: string;
  ioType?: string;
}) {
  const res = await request.get('/api/monitoring/realtime/missing-rate-trend', { params });
  return res.data.data;
}

// 版本对比
export async function fetchVersionCompare(body: {
  project: string;
  packageId: string;
  versionA: string;
  versionB: string;
}) {
  const res = await request.post('/api/monitoring/realtime/version-compare', body);
  return res.data.data;
}

// 近N日执行量走势
export async function fetchDailyTrend(params: {
  project: string;
  packageId: string;
  days?: number;
}) {
  const res = await request.get('/api/monitoring/realtime/daily-trend', { params });
  return res.data.data;
}

// 分时走势（5分钟粒度）
export interface IntradayTrendPoint {
  window_start: string;
  sample_count: number;
  missing_count: number;
  error_count: number;
  anomaly_rate: number;
  error_rate: number;
  day_type: 'target' | 'previous';
}

export async function fetchIntradayTrend(params: {
  project: string;
  packageId: string;
  date?: string;
}): Promise<IntradayTrendPoint[]> {
  const res = await request.get('/api/monitoring/realtime/intraday-trend', { params });
  return res.data.data;
}

// 异常记录下钻
export async function fetchAnomalyRecords(params: {
  project: string;
  packageId: string;
  varCategory: string;
  varName: string;
  ioType?: string;
  anomalyType?: string;
  page?: number;
  pageSize?: number;
}) {
  const res = await request.get('/api/monitoring/realtime/anomaly-records', { params });
  return res.data.data;
}

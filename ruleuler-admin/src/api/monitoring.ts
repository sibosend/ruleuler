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

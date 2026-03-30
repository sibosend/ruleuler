import request from './request';

// ==================== V1 兼容 API ====================

export function runAutoTest(project: string, packageId: string) {
  return request.post('/api/autotest/run', null, { params: { project, packageId } });
}

export function loadTestRuns(project: string) {
  return request.get('/api/autotest/runs', { params: { project } });
}

export function loadTestCases(packId: number) {
  return request.get('/api/autotest/cases', { params: { packId } });
}

export function loadTestResults(runId: number) {
  return request.get('/api/autotest/results', { params: { runId } });
}

// ==================== V2 API ====================

/** 自动生成用例包 */
export function generatePack(project: string, packageId: string) {
  return request.post('/api/autotest/packs/generate', null, { params: { project, packageId } });
}

/** 查询项目下的知识包列表 */
export function listPackages(project: string) {
  return request.get(`/api/projects/${encodeURIComponent(project)}/packages`);
}

/** 查询用例包列表 */
export function listPacks(project: string, packageId?: string) {
  return request.get('/api/autotest/packs', { params: { project, packageId } });
}

/** 查询用例包详情（含用例列表） */
export function getPackDetail(packId: number) {
  return request.get(`/api/autotest/packs/${packId}`);
}

/** 执行测试运行 */
export function executeRun(packId: number, baselineRunId?: number) {
  return request.post('/api/autotest/runs', null, { params: { packId, baselineRunId } });
}

/** 查询运行进度 */
export function getRunStatus(runId: number) {
  return request.get('/api/autotest/run-status', { params: { runId } });
}

/** 查询冲突检测结果 */
export function getConflicts(runId: number) {
  return request.get(`/api/autotest/conflicts/${runId}`);
}

/** 查询测试报告 */
export function getReport(runId: number) {
  return request.get(`/api/autotest/report/${runId}`);
}

/** 查询分段统计 */
export function getSegments(runId: number) {
  return request.get(`/api/autotest/segments/${runId}`);
}

/** 更新 baseline */
export function updateBaseline(runId: number) {
  return request.put(`/api/autotest/baseline/${runId}`);
}

/** 手动导入用例包（CSV/JSON 文件） */
export function importPack(project: string, packageId: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/api/autotest/packs/import', formData, {
    params: { project, packageId },
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

import request from './request';

// ─── Types ───────────────────────────────────────────────────────────

export type FileNodeType =
  | 'root' | 'project' | 'resource' | 'resourcePackage'
  | 'lib' | 'action' | 'parameter' | 'constant' | 'variable'
  | 'ruleLib' | 'decisionTableLib' | 'decisionTreeLib' | 'scorecardLib' | 'flowLib'
  | 'scorecard' | 'rule' | 'rea' | 'ul'
  | 'decisionTable' | 'scriptDecisionTable' | 'decisionTree' | 'flow'
  | 'all' | 'folder';

export interface RepositoryFile {
  id: string;
  name: string;
  fullPath: string;
  type: FileNodeType;
  folderType: FileNodeType | null;
  lock: boolean;
  lockInfo: string | null;
  storageType: string | null;
  children: RepositoryFile[] | null;
}

export interface Repository {
  rootFile: RepositoryFile;
  projectNames: string[];
}

export interface LoadProjectsResponse {
  repo: Repository;
  classify: boolean;
}

// ─── API functions ───────────────────────────────────────────────────
// 走 /api/console/*，JWT 认证，JSON 格式

export async function loadProjects(projectName: string): Promise<LoadProjectsResponse> {
  const res = await request.get(`/api/console/tree/${encodeURIComponent(projectName)}`);
  return res.data.data as LoadProjectsResponse;
}

export async function createFile(path: string, type: string): Promise<void> {
  const content = getDefaultContent(type);
  await request.post('/api/console/file', { path, content });
}

export async function createFolder(path: string, folderType?: string): Promise<void> {
  await request.post('/api/console/folder', { path, folderType });
}

export async function deleteFile(path: string): Promise<void> {
  await request.delete('/api/console/file', { params: { path } });
}

export async function fileRename(path: string, newPath: string): Promise<void> {
  await request.post('/api/console/rename', { path, newPath });
}

export async function copyFile(oldFullPath: string, newFullPath: string): Promise<void> {
  await request.post('/api/console/copy', { oldFullPath, newFullPath });
}

export async function lockFile(file: string): Promise<void> {
  await request.post('/api/console/lock', { file });
}

export async function unlockFile(file: string): Promise<void> {
  await request.post('/api/console/unlock', { file });
}

export async function fileSource(path: string): Promise<string> {
  const res = await request.get('/api/console/source', { params: { path } });
  return res.data.data.content as string;
}

export interface VersionFile {
  name: string;
  createDate: string;
  comment: string;
}

export async function fileVersions(path: string): Promise<VersionFile[]> {
  const res = await request.get('/api/console/versions', { params: { path } });
  return res.data.data as VersionFile[];
}

/** 根据文件类型返回默认 XML 内容 */
function getDefaultContent(type: string): string {
  const rootTags: Record<string, string> = {
    'vl.xml': 'variable-library',
    'cl.xml': 'constant-library',
    'pl.xml': 'parameter-library',
    'al.xml': 'action-library',
    'rs.xml': 'rule-set',
    'dt.xml': 'decision-table',
    'dts.xml': 'script-decision-table',
    'dtree.xml': 'decision-tree',
    'rl.xml': 'rule-flow',
    'sc': 'scorecard',
  };
  const tag = rootTags[type];
  if (tag) return `<?xml version="1.0" encoding="utf-8"?>\n<${tag}></${tag}>`;
  return '';
}

/** 文件后缀 → 编辑器路径 的映射 */
export const FILE_TYPE_EDITOR_MAP: Record<string, string> = {
  'vl.xml':    '/urule/variableeditor',
  'cl.xml':    '/urule/constanteditor',
  'pl.xml':    '/urule/parametereditor',
  'al.xml':    '/urule/actioneditor',
  'rs.xml':    '/urule/ruleseteditor',
  'rea.xml':   '/urule/reaeditor',
  'ul':        '/urule/uleditor',
  'dt.xml':    '/urule/decisiontableeditor',
  'dts.xml':   '/urule/scriptdecisiontableeditor',
  'dtree.xml': '/urule/decisiontreeeditor',
  'rl.xml':    '/urule/ruleflowdesigner',
  'sc':        '/urule/scorecardeditor',
};

// 按后缀长度降序排列，确保最长匹配优先（如 dts.xml 优先于 ts.xml）
const SORTED_SUFFIXES = Object.keys(FILE_TYPE_EDITOR_MAP).sort(
  (a, b) => b.length - a.length,
);

/** 根据文件名获取编辑器 URL，未匹配返回 null */
export function getEditorUrl(fileName: string): string | null {
  const suffix = SORTED_SUFFIXES.find((s) => fileName.endsWith(`.${s}`));
  return suffix != null ? (FILE_TYPE_EDITOR_MAP[suffix] ?? null) : null;
}

/** 根据文件名和 fullPath 构建完整的 iframe src，未匹配返回 null */
export function buildEditorSrc(
  fileName: string,
  fullPath: string,
): string | null {
  // .rea.xml 走独立前端页面，构建 rea-editor URL
  if (fileName.endsWith('.rea.xml')) {
    const normalized = fullPath.startsWith('/') ? fullPath : `/${fullPath}`;
    const project = normalized.split('/')[1] ?? '';
    return `/admin/rea-editor?file=${encodeURIComponent(`dbr:${normalized}`)}&project=${encodeURIComponent(project)}`;
  }
  const editorUrl = getEditorUrl(fileName);
  if (!editorUrl) return null;
  const normalized = fullPath.startsWith('/') ? fullPath : `/${fullPath}`;
  const fileParam = `dbr:${normalized}`;
  return `${editorUrl}?file=${encodeURIComponent(fileParam)}`;
}

const EDITOR_ROUTE_PREFIX = '/console/';
const EDITOR_ROUTE_EDIT_SEG = '/edit/';

/** 构建编辑器路由路径：/console/:project/edit/:filePath */
export function buildEditorRoute(project: string, filePath: string): string {
  // fullPath 可能带前导 /，路由中去掉避免双斜杠
  const clean = filePath.startsWith('/') ? filePath.slice(1) : filePath;
  return `${EDITOR_ROUTE_PREFIX}${project}${EDITOR_ROUTE_EDIT_SEG}${clean}`;
}

/** 解析编辑器路由路径，返回 { project, filePath } 或 null */
export function parseEditorRoute(
  routePath: string,
): { project: string; filePath: string } | null {
  if (!routePath.startsWith(EDITOR_ROUTE_PREFIX)) return null;
  const rest = routePath.slice(EDITOR_ROUTE_PREFIX.length);
  const editIdx = rest.indexOf(EDITOR_ROUTE_EDIT_SEG);
  if (editIdx < 0) return null;
  const project = rest.slice(0, editIdx);
  const filePath = rest.slice(editIdx + EDITOR_ROUTE_EDIT_SEG.length);
  if (!project || !filePath) return null;
  return { project, filePath };
}

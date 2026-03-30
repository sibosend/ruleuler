/**
 * REA 编辑器后端接口层。
 *
 * REA 编辑器是 iframe 内的裸路由页面，不走 admin 的 auth 体系，
 * 直接用 fetch 调用 /urule 路径。参数用 URLSearchParams（后端 Servlet 读 request.getParameter）。
 */

// ─── 类型定义 ───────────────────────────────────────────────

/** loadProjectLibs 返回：按类型分组的库文件路径列表 */
export interface ProjectLibs {
  variable: string[];
  constant: string[];
  parameter: string[];
  action: string[];
}

/** scriptValidation 返回的单条错误信息 */
export interface ErrorInfo {
  line: number;
  column: number;
  message: string;
}

// ─── 内部工具 ───────────────────────────────────────────────

async function post<T>(url: string, params: Record<string, string>): Promise<T> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params).toString(),
  });
  if (!resp.ok) {
    throw new Error(`POST ${url} failed: ${resp.status} ${resp.statusText}`);
  }
  return resp.json() as Promise<T>;
}

// ─── 公开接口 ───────────────────────────────────────────────

/**
 * 扫描项目下所有库文件路径，按类型分组返回。
 * 失败时快速失败（throw），编辑器不可用。
 */
export async function loadProjectLibs(project: string): Promise<ProjectLibs> {
  return post<ProjectLibs>('/urule/reaeditor/loadProjectLibs', { project });
}

/**
 * 批量加载 XML 文件并反序列化为 JSON。
 * @param files 分号分隔的文件路径，或单个路径
 */
export async function loadXml(files: string): Promise<unknown[]> {
  return post<unknown[]>('/urule/common/loadXml', { files });
}

/**
 * 保存文件内容。
 * @param newVersion 是否保存为新版本（"true" / "false"）
 */
export async function saveFile(
  file: string,
  content: string,
  newVersion: boolean,
): Promise<void> {
  const resp = await fetch('/urule/common/saveFile', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      file,
      content,
      newVersion: String(newVersion),
    }).toString(),
  });
  if (!resp.ok) {
    throw new Error(`保存失败: ${resp.status} ${resp.statusText}`);
  }
}

/**
 * 加载文件的原始 XML 内容。
 */
export async function loadRawXml(file: string): Promise<string> {
  const resp = await fetch('/urule/reaeditor/loadRawXml', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ file }).toString(),
  });
  if (!resp.ok) {
    throw new Error(`加载文件失败: ${resp.status} ${resp.statusText}`);
  }
  return resp.text();
}

/**
 * 语法校验。
 * @param type 校验类型（如 "DecisionSet"）
 * @param content 待校验内容
 */
export async function scriptValidation(
  type: string,
  content: string,
): Promise<ErrorInfo[]> {
  return post<ErrorInfo[]>('/urule/common/scriptValidation', { type, content });
}

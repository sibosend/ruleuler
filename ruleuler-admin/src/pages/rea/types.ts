/**
 * REA 编辑器共享类型定义
 */

import type { ProjectLibs } from './api/reaApi';
import type { LibraryData } from './lib/expressionParser';

/** 单条规则的前端状态 */
export interface RuleState {
  id: string;
  name: string;
  properties: Record<string, string | boolean | number>;
  conditionText: string;
  actionText: string;
  elseText: string;
  conditionError: boolean;
  actionError: boolean;
  elseError: boolean;
}

/** 库文件路径 + 解析后数据 */
export interface LibraryState {
  paths: ProjectLibs;
  data: LibraryData;
}

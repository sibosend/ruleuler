/**
 * REA 硬保留字。一份表，lexer / 补全 / 高亮 / lint 共用。
 * 识别形态：/^[A-Z][A-Z0-9]*$/，只认全大写精确匹配。
 */

import { WORD_OPS } from './operatorMap';
import { FLAT_FUNC_NAMES, NAMESPACES } from './functionMap';

export const RESERVED_LOGIC = ['AND', 'OR'] as const;
export const RESERVED_BOOLEAN = ['TRUE', 'FALSE'] as const;

/** 所有硬保留字（英文整词） */
export const ALL_RESERVED: Set<string> = new Set<string>([
  ...RESERVED_LOGIC,
  ...RESERVED_BOOLEAN,
  ...WORD_OPS,
  ...FLAT_FUNC_NAMES,
  ...NAMESPACES,
]);

const ENGLISH_WORD = /^[A-Za-z][A-Za-z0-9]*$/;

/** 英文整词大小写不敏感命中保留字（拦类别名 / 参数名，不拦属性名） */
export function isReservedIdent(word: string): boolean {
  return ENGLISH_WORD.test(word) && ALL_RESERVED.has(word.toUpperCase());
}

/**
 * 近形保留字：大小写不对。
 * 返回应使用的全大写形式；不是近形则返回 null。
 */
export function suggestReserved(word: string): string | null {
  if (!ENGLISH_WORD.test(word)) return null;
  const upper = word.toUpperCase();
  if (!ALL_RESERVED.has(upper)) return null;
  if (word === upper) return null;
  return upper;
}

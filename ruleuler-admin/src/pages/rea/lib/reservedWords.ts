/**
 * REA 硬保留字。一份表，lexer / 补全 / 高亮 / lint 共用。
 * 识别形态：/^[A-Z][A-Z0-9]*$/，只认全大写精确匹配。
 */

import { WORD_OPS } from './operatorMap';
import { FLAT_FUNC_NAMES, NAMESPACES } from './functionMap';

export const RESERVED_LOGIC = ['AND', 'OR'] as const;
export const RESERVED_BOOLEAN = ['TRUE', 'FALSE'] as const;

/** 谓词：编成 Op.Null/NotNull，不进 functionMap（没有 Bean） */
export const RESERVED_PREDICATE = ['ISNULL', 'ISNOTNULL'] as const;

export const PREDICATE_HINTS: ReadonlyMap<string, string> = new Map([
  ['ISNULL', '为空（引擎把空串也当空；灰度只认 == null，空串不算空）'],
  ['ISNOTNULL', '不为空'],
]);

/** 所有硬保留字（英文整词） */
export const ALL_RESERVED: Set<string> = new Set<string>([
  ...RESERVED_LOGIC,
  ...RESERVED_BOOLEAN,
  ...WORD_OPS,
  ...FLAT_FUNC_NAMES,
  ...NAMESPACES,
  ...RESERVED_PREDICATE,
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

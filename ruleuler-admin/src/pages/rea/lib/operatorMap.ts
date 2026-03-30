/**
 * 操作符映射表：文本操作符 ↔ XML op 值
 */

const OPERATOR_PAIRS: readonly [string, string][] = [
  ['==', 'Equals'],
  ['!=', 'NotEquals'],
  ['>', 'GreaterThen'],
  ['>=', 'GreaterThenEquals'],
  ['<', 'LessThen'],
  ['<=', 'LessThenEquals'],
  ['Contain', 'Contain'],
  ['NotContain', 'NotContain'],
  ['In', 'In'],
  ['NotIn', 'NotIn'],
  ['Match', 'Match'],
  ['NotMatch', 'NotMatch'],
  ['Startwith', 'StartWith'],
  ['NotStartwith', 'NotStartWith'],
  ['Endwith', 'EndWith'],
  ['NotEndwith', 'NotEndWith'],
  ['EqualsIgnoreCase', 'EqualsIgnoreCase'],
  ['NotEqualsIgnoreCase', 'NotEqualsIgnoreCase'],
] as const;

/** 文本操作符 → XML op 值 */
export const textToXmlOp = new Map<string, string>(OPERATOR_PAIRS);

/** XML op 值 → 文本操作符 */
export const xmlOpToText = new Map<string, string>(
  OPERATOR_PAIRS.map(([text, xml]) => [xml, text]),
);

/** 所有文本操作符列表（用于自动补全） */
export const ALL_TEXT_OPERATORS: string[] = OPERATOR_PAIRS.map(([text]) => text);

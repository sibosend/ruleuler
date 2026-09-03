/**
 * 操作符映射表：REA 文本操作符 ↔ XML op 值
 * 一张配对表，两个索引。REA 只认全大写。
 */

const OPERATOR_PAIRS: readonly [string, string][] = [
  ['==', 'Equals'],
  ['!=', 'NotEquals'],
  ['>', 'GreaterThen'],
  ['>=', 'GreaterThenEquals'],
  ['<', 'LessThen'],
  ['<=', 'LessThenEquals'],
  ['CONTAIN', 'Contain'],
  ['NOTCONTAIN', 'NotContain'],
  ['IN', 'In'],
  ['NOTIN', 'NotIn'],
  ['MATCH', 'Match'],
  ['NOTMATCH', 'NotMatch'],
  ['STARTWITH', 'StartWith'],
  ['NOTSTARTWITH', 'NotStartWith'],
  ['ENDWITH', 'EndWith'],
  ['NOTENDWITH', 'NotEndWith'],
  ['EQUALSIGNORECASE', 'EqualsIgnoreCase'],
  ['NOTEQUALSIGNORECASE', 'NotEqualsIgnoreCase'],
] as const;

/** 文本操作符 → XML op 值 */
export const textToXmlOp = new Map<string, string>(OPERATOR_PAIRS);

/** XML op 值 → 文本操作符 */
export const xmlOpToText = new Map<string, string>(
  OPERATOR_PAIRS.map(([text, xml]) => [xml, text]),
);

/** 所有文本操作符列表（用于自动补全） */
export const ALL_TEXT_OPERATORS: string[] = OPERATOR_PAIRS.map(([text]) => text);

/** 文字操作符（非符号） */
export const WORD_OPS: string[] = ALL_TEXT_OPERATORS.filter((op) => /^[A-Z]/.test(op));

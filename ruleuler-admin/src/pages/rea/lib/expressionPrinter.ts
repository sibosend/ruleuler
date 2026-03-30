/**
 * expressionPrinter.ts — XML → 文本表达式
 *
 * printCondition: <if>/<and>/<or> XML → 条件文本
 * printAssignment: <then>/<else> XML → 赋值文本
 */

import { xmlOpToText } from './operatorMap';

// ─── 类型定义 ───

export interface PrintResult {
  text: string;
  hasError: boolean;
}

const ERROR_MARKER = '[不支持的表达式]';

// ─── XML 解析辅助 ───

function parseXmlDoc(xml: string): Document {
  const parser = new DOMParser();
  return parser.parseFromString(xml, 'text/xml');
}

/** 获取元素的直接子元素（按标签名） */
function childElements(el: Element, tagName?: string): Element[] {
  const result: Element[] = [];
  for (let i = 0; i < el.children.length; i++) {
    const child = el.children[i]!;
    if (!tagName || child.tagName === tagName) {
      result.push(child);
    }
  }
  return result;
}

// ─── Left / Value 格式化 ───

function formatLeft(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';
  if (type === 'method' || type === 'commonfunction') {
    return { text: ERROR_MARKER, hasError: true };
  }
  if (type === 'parameter') {
    const varName = el.getAttribute('var') || '';
    return { text: varName, hasError: false };
  }
  // type === 'variable' or default
  const category = el.getAttribute('var-category') || '';
  const varName = el.getAttribute('var') || '';
  return { text: `${category}.${varName}`, hasError: false };
}

function isNumeric(s: string): boolean {
  return /^-?\d+(\.\d+)?$/.test(s);
}

function formatValue(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';

  if (type === 'Method' || type === 'CommonFunction') {
    return { text: ERROR_MARKER, hasError: true };
  }

  if (type === 'Input') {
    const content = el.getAttribute('content') || '';
    // In/NotIn 列表值：content 中含逗号，用括号包裹
    if (content.includes(',')) {
      const items = content.split(',').map((v) => v.trim());
      const formatted = items
        .map((v) => (isNumeric(v) ? v : `"${v}"`))
        .join(', ');
      return { text: `(${formatted})`, hasError: false };
    }
    if (isNumeric(content)) {
      return { text: content, hasError: false };
    }
    if (content === 'true' || content === 'false') {
      return { text: content, hasError: false };
    }
    return { text: `"${content}"`, hasError: false };
  }

  if (type === 'Variable') {
    const category = el.getAttribute('var-category') || '';
    const varName = el.getAttribute('var') || '';
    return { text: `${category}.${varName}`, hasError: false };
  }

  if (type === 'Parameter') {
    const varName = el.getAttribute('var') || '';
    return { text: varName, hasError: false };
  }

  if (type === 'Constant') {
    const category = el.getAttribute('const-category') || '';
    const constName = el.getAttribute('const') || '';
    return { text: `$${category}.${constName}`, hasError: false };
  }

  // 未知类型
  return { text: ERROR_MARKER, hasError: true };
}

// ─── 条件格式化 ───

function formatAtom(atom: Element): { text: string; hasError: boolean } {
  const op = atom.getAttribute('op') || '';
  const textOp = xmlOpToText.get(op);
  if (!textOp) {
    return { text: ERROR_MARKER, hasError: true };
  }

  const leftEl = atom.querySelector(':scope > left');
  const valueEl = atom.querySelector(':scope > value');
  if (!leftEl || !valueEl) {
    return { text: ERROR_MARKER, hasError: true };
  }

  const left = formatLeft(leftEl);
  const value = formatValue(valueEl);
  const hasError = left.hasError || value.hasError;

  return { text: `${left.text} ${textOp} ${value.text}`, hasError };
}

function formatJunction(el: Element, nested = false): PrintResult {
  const tag = el.tagName; // 'and' or 'or'
  const connector = tag === 'and' ? ' AND ' : ' OR ';
  const children = Array.from(el.children);

  if (children.length === 0) {
    return { text: '', hasError: false };
  }

  let hasError = false;
  const parts: string[] = [];

  for (const child of children) {
    let result: PrintResult;
    if (child.tagName === 'atom') {
      result = formatAtom(child);
    } else if (child.tagName === 'and' || child.tagName === 'or') {
      result = formatJunction(child, true);
    } else {
      result = { text: ERROR_MARKER, hasError: true };
    }
    if (result.hasError) hasError = true;
    parts.push(result.text);
  }

  const text = parts.join(connector);
  // 嵌套的 junction 加括号
  return { text: nested ? `(${text})` : text, hasError };
}

/**
 * 将 <if> XML 片段转为条件文本
 */
export function printCondition(xml: string): PrintResult {
  const trimmed = xml.trim();
  if (!trimmed) return { text: '', hasError: false };

  const doc = parseXmlDoc(trimmed);
  const root = doc.documentElement;

  if (root.tagName === 'parsererror') {
    return { text: ERROR_MARKER, hasError: true };
  }

  // 根元素可能是 <if>、<and>、<or>
  let junctionEl: Element | null = null;

  if (root.tagName === 'if') {
    // <if> 内部应有 <and> 或 <or>
    junctionEl =
      root.querySelector(':scope > and') ||
      root.querySelector(':scope > or');
    if (!junctionEl) {
      // 可能 <if> 直接包含 <atom>（单条件无 junction）
      const atoms = childElements(root, 'atom');
      if (atoms.length > 0) {
        const result = formatAtom(atoms[0]!);
        return result;
      }
      return { text: '', hasError: false };
    }
  } else if (root.tagName === 'and' || root.tagName === 'or') {
    junctionEl = root;
  } else {
    return { text: ERROR_MARKER, hasError: true };
  }

  return formatJunction(junctionEl);
}

// ─── 赋值格式化 ───

function formatVarAssign(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';
  let leftText: string;
  let hasError = false;

  if (type === 'method' || type === 'commonfunction') {
    return { text: ERROR_MARKER, hasError: true };
  }

  if (type === 'parameter') {
    const varName = el.getAttribute('var') || '';
    leftText = varName;
  } else {
    // variable
    const category = el.getAttribute('var-category') || '';
    const varName = el.getAttribute('var') || '';
    leftText = `${category}.${varName}`;
  }

  const valueEl = el.querySelector(':scope > value');
  if (!valueEl) {
    return { text: ERROR_MARKER, hasError: true };
  }

  const value = formatValue(valueEl);
  if (value.hasError) hasError = true;

  return { text: `${leftText} = ${value.text}`, hasError };
}

/**
 * 将 <then>/<else> XML 片段转为赋值文本
 */
export function printAssignment(xml: string): PrintResult {
  const trimmed = xml.trim();
  if (!trimmed) return { text: '', hasError: false };

  const doc = parseXmlDoc(trimmed);
  const root = doc.documentElement;

  if (root.tagName === 'parsererror') {
    return { text: ERROR_MARKER, hasError: true };
  }

  // root 可能是 <then> 或 <else>，内部包含 <var-assign> 或其他 action
  const children = Array.from(root.children);

  if (children.length === 0) {
    return { text: '', hasError: false };
  }

  let hasError = false;
  const parts: string[] = [];

  for (const child of children) {
    if (child.tagName === 'var-assign') {
      const result = formatVarAssign(child);
      if (result.hasError) hasError = true;
      parts.push(result.text);
    } else {
      // 不支持的 action（execute-method, console-print 等）
      hasError = true;
      parts.push(ERROR_MARKER);
    }
  }

  return { text: parts.join('; '), hasError };
}

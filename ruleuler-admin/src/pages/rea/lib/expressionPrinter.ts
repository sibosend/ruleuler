/**
 * expressionPrinter.ts — XML → 文本表达式
 *
 * printCondition: <if>/<and>/<or> XML → 条件文本
 * printAssignment: <then>/<else> XML → 赋值/动作文本
 */

import { xmlOpToText } from './operatorMap';
import { lookupByMethod, lookupByCommonFunction } from './functionMap';

export interface PrintResult {
  text: string;
  hasError: boolean;
}

const ERROR_MARKER = '[不支持的表达式]';

function parseXmlDoc(xml: string): Document {
  const parser = new DOMParser();
  return parser.parseFromString(xml, 'text/xml');
}

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

function formatCallArgsFromParameters(el: Element): { text: string; hasError: boolean } {
  const params = childElements(el, 'parameter');
  const parts: string[] = [];
  let hasError = false;
  for (const p of params) {
    const valueEl = p.querySelector(':scope > value');
    if (!valueEl) {
      hasError = true;
      parts.push(ERROR_MARKER);
      continue;
    }
    const v = formatValue(valueEl);
    if (v.hasError) hasError = true;
    parts.push(v.text);
  }
  return { text: parts.join(', '), hasError };
}

function formatMethodCall(el: Element, beanAttr: 'bean-name' | 'bean'): { text: string; hasError: boolean } {
  const bean = el.getAttribute(beanAttr) || '';
  const method = el.getAttribute('method-name') || '';
  const fn = lookupByMethod(bean, method);
  const args = formatCallArgsFromParameters(el);
  if (fn) {
    return { text: `${fn.rea}(${args.text})`, hasError: args.hasError };
  }
  if (!bean || !method || bean.startsWith('urule.')) {
    return { text: ERROR_MARKER, hasError: true };
  }
  return {
    text: `${bean.toUpperCase()}.${method.toUpperCase()}(${args.text})`,
    hasError: args.hasError,
  };
}

function formatCommonCall(el: Element): { text: string; hasError: boolean } {
  const name = el.getAttribute('function-name') || '';
  const fn = lookupByCommonFunction(name);
  if (!fn) return { text: ERROR_MARKER, hasError: true };
  const fp = el.querySelector(':scope > function-parameter');
  if (!fp) return { text: ERROR_MARKER, hasError: true };
  const valueEl = fp.querySelector(':scope > value');
  if (!valueEl) return { text: ERROR_MARKER, hasError: true };
  const obj = formatValue(valueEl);
  const prop = fp.getAttribute('property-name');
  if (fn.needProperty) {
    if (!prop) return { text: ERROR_MARKER, hasError: true };
    return { text: `${fn.rea}(${obj.text}, ${prop})`, hasError: obj.hasError };
  }
  return { text: `${fn.rea}(${obj.text})`, hasError: obj.hasError };
}

function formatLeft(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';
  if (type === 'method') return formatMethodCall(el, 'bean-name');
  if (type === 'commonfunction') return formatCommonCall(el);
  if (type === 'parameter') {
    const varName = el.getAttribute('var') || '';
    return { text: varName, hasError: false };
  }
  const category = el.getAttribute('var-category') || '';
  const varName = el.getAttribute('var') || '';
  return { text: `${category}.${varName}`, hasError: false };
}

function isNumeric(s: string): boolean {
  return /^-?\d+(\.\d+)?$/.test(s);
}

function formatValue(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';

  if (type === 'Method') return formatMethodCall(el, 'bean-name');
  if (type === 'CommonFunction') return formatCommonCall(el);

  if (type === 'Input') {
    const content = el.getAttribute('content') || '';
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
      return { text: content === 'true' ? 'TRUE' : 'FALSE', hasError: false };
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

  return { text: ERROR_MARKER, hasError: true };
}

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
  const tag = el.tagName;
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
  return { text: nested ? `(${text})` : text, hasError };
}

export function printCondition(xml: string): PrintResult {
  const trimmed = xml.trim();
  if (!trimmed) return { text: '', hasError: false };

  const doc = parseXmlDoc(trimmed);
  const root = doc.documentElement;

  if (root.tagName === 'parsererror') {
    return { text: ERROR_MARKER, hasError: true };
  }

  let junctionEl: Element | null = null;

  if (root.tagName === 'if') {
    junctionEl =
      root.querySelector(':scope > and') ||
      root.querySelector(':scope > or');
    if (!junctionEl) {
      const atoms = childElements(root, 'atom');
      if (atoms.length > 0) {
        return formatAtom(atoms[0]!);
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

function formatVarAssign(el: Element): { text: string; hasError: boolean } {
  const type = el.getAttribute('type') || '';

  if (type === 'method' || type === 'commonfunction') {
    return { text: ERROR_MARKER, hasError: true };
  }

  let leftText: string;
  let hasError = false;

  if (type === 'parameter') {
    const varName = el.getAttribute('var') || '';
    leftText = varName;
  } else {
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

function formatExecuteMethod(el: Element): { text: string; hasError: boolean } {
  return formatMethodCall(el, 'bean');
}

export function printAssignment(xml: string): PrintResult {
  const trimmed = xml.trim();
  if (!trimmed) return { text: '', hasError: false };

  const doc = parseXmlDoc(trimmed);
  const root = doc.documentElement;

  if (root.tagName === 'parsererror') {
    return { text: ERROR_MARKER, hasError: true };
  }

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
    } else if (child.tagName === 'execute-method') {
      const result = formatExecuteMethod(child);
      if (result.hasError) hasError = true;
      parts.push(result.text);
    } else {
      hasError = true;
      parts.push(ERROR_MARKER);
    }
  }

  return { text: parts.join('; '), hasError };
}

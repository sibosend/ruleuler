/**
 * expressionParser.ts — 文本表达式 → XML
 *
 * 条件: `类别.变量 操作符 值 [AND|OR ...]` → <and>/<or> + <atom> XML
 * 赋值: `类别.变量 = 值 [; ...]` → <var-assign> XML
 */

import { textToXmlOp, ALL_TEXT_OPERATORS } from './operatorMap';

// ─── 常量 ───

/** 参数内部类别标识，不对用户暴露 */
export const PARAMETER_CATEGORY = '__parameter__';

// ─── 类型定义 ───

export interface LibraryData {
  variables: Array<{
    name: string;
    variables: Array<{ name: string; label: string; type: string }>;
  }>;
  parameters: Array<{ name: string; label: string; type: string }>;
}

export class ParseError extends Error {
  position: number;
  constructor(message: string, position: number) {
    super(message);
    this.name = 'ParseError';
    this.position = position;
  }
}

// ─── XML 转义 ───

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

// ─── 库数据查找 ───

interface VarInfo {
  category: string;
  name: string;
  label: string;
  datatype: string;
  kind: 'variable' | 'parameter';
}

function lookupVar(
  category: string,
  varName: string,
  libs?: LibraryData,
): VarInfo {
  if (category === PARAMETER_CATEGORY) {
    if (libs) {
      const p = libs.parameters.find((p) => p.name === varName);
      if (p) {
        return {
          category: '',
          name: p.name,
          label: p.label,
          datatype: p.type || 'String',
          kind: 'parameter',
        };
      }
    }
    return {
      category: '',
      name: varName,
      label: varName,
      datatype: 'String',
      kind: 'parameter',
    };
  }

  if (libs) {
    const cat = libs.variables.find((c) => c.name === category);
    if (cat) {
      const v = cat.variables.find((v) => v.name === varName);
      if (v) {
        return {
          category,
          name: v.name,
          label: v.label,
          datatype: v.type || 'String',
          kind: 'variable',
        };
      }
    }
  }
  return {
    category,
    name: varName,
    label: varName,
    datatype: 'String',
    kind: 'variable',
  };
}

// ─── Tokenizer ───

type TokenType =
  | 'IDENT'
  | 'DOT'
  | 'OP'
  | 'STRING'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'AND'
  | 'OR'
  | 'SEMI'
  | 'EQ'
  | 'LPAREN'
  | 'RPAREN'
  | 'COMMA'
  | 'EOF';

interface Token {
  type: TokenType;
  value: string;
  pos: number;
}

// 按长度降序排列，确保 >= 优先于 >
const SYMBOL_OPS = ['>=', '<=', '!=', '==', '>', '<'] as const;
const WORD_OPS = ALL_TEXT_OPERATORS.filter(
  (op) => !SYMBOL_OPS.includes(op as (typeof SYMBOL_OPS)[number]),
);

function tokenize(text: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  const len = text.length;

  while (i < len) {
    // 跳过空白
    if (/\s/.test(text[i]!)) {
      i++;
      continue;
    }

    const pos = i;

    // 字符串字面量
    if (text[i] === '"') {
      i++;
      let val = '';
      while (i < len && text[i] !== '"') {
        if (text[i] === '\\' && i + 1 < len) {
          val += text[i + 1];
          i += 2;
        } else {
          val += text[i];
          i++;
        }
      }
      if (i >= len) throw new ParseError('未闭合的字符串', pos);
      i++; // skip closing "
      tokens.push({ type: 'STRING', value: val, pos });
      continue;
    }

    // 分号
    if (text[i] === ';') {
      tokens.push({ type: 'SEMI', value: ';', pos });
      i++;
      continue;
    }

    // 点号
    if (text[i] === '.') {
      tokens.push({ type: 'DOT', value: '.', pos });
      i++;
      continue;
    }

    // 括号和逗号
    if (text[i] === '(') {
      tokens.push({ type: 'LPAREN', value: '(', pos });
      i++;
      continue;
    }
    if (text[i] === ')') {
      tokens.push({ type: 'RPAREN', value: ')', pos });
      i++;
      continue;
    }
    if (text[i] === '[') {
      throw new ParseError('列表请使用圆括号 ()，不支持方括号 []', pos);
    }
    if (text[i] === ',') {
      tokens.push({ type: 'COMMA', value: ',', pos });
      i++;
      continue;
    }

    // 符号操作符（按长度降序，>= 优先于 >）
    let matched = false;
    for (const op of SYMBOL_OPS) {
      if (text.startsWith(op, i)) {
        tokens.push({ type: 'OP', value: op, pos });
        i += op.length;
        matched = true;
        break;
      }
    }
    if (matched) continue;

    // 单独的 = 号（赋值）
    if (text[i] === '=') {
      tokens.push({ type: 'EQ', value: '=', pos });
      i++;
      continue;
    }

    // 数字（含负数和小数）
    if (/[0-9]/.test(text[i]!) || (text[i] === '-' && i + 1 < len && /[0-9]/.test(text[i + 1]!))) {
      let num = '';
      if (text[i] === '-') {
        num += '-';
        i++;
      }
      while (i < len && /[0-9.]/.test(text[i]!)) {
        num += text[i];
        i++;
      }
      tokens.push({ type: 'NUMBER', value: num, pos });
      continue;
    }

    // 标识符 / 关键字 / 文字操作符
    if (/[a-zA-Z_\u4e00-\u9fff]/.test(text[i]!)) {
      let word = '';
      while (i < len && /[a-zA-Z0-9_\u4e00-\u9fff]/.test(text[i]!)) {
        word += text[i];
        i++;
      }

      if (word === 'true' || word === 'false') {
        tokens.push({ type: 'BOOLEAN', value: word, pos });
      } else if (word.toLowerCase() === 'true' || word.toLowerCase() === 'false') {
        throw new ParseError(`布尔值请使用小写: ${word.toLowerCase()}`, pos);
      } else if (word === 'AND') {
        tokens.push({ type: 'AND', value: 'AND', pos });
      } else if (word === 'OR') {
        tokens.push({ type: 'OR', value: 'OR', pos });
      } else if (WORD_OPS.includes(word)) {
        tokens.push({ type: 'OP', value: word, pos });
      } else {
        tokens.push({ type: 'IDENT', value: word, pos });
      }
      continue;
    }

    throw new ParseError(`意外的字符: '${text[i]}'`, pos);
  }

  tokens.push({ type: 'EOF', value: '', pos: len });
  return tokens;
}

// ─── Parser helpers ───

class Parser {
  private tokens: Token[];
  private pos: number;

  constructor(tokens: Token[]) {
    this.tokens = tokens;
    this.pos = 0;
  }

  peek(): Token {
    return this.tokens[this.pos]!;
  }

  advance(): Token {
    const t = this.tokens[this.pos]!;
    this.pos++;
    return t;
  }

  expect(type: TokenType, msg?: string): Token {
    const t = this.peek();
    if (t.type !== type) {
      throw new ParseError(
        msg ?? `期望 ${type}，但得到 ${t.type}(${t.value})`,
        t.pos,
      );
    }
    return this.advance();
  }

  isAtEnd(): boolean {
    return this.peek().type === 'EOF';
  }

  /** 解析 `类别.变量名` 或裸参数名引用 */
  parseRef(): { category: string; name: string } {
    const first = this.expect('IDENT', '期望变量类别或参数名');
    // 有 `.` → 变量引用：类别.变量名
    if (this.peek().type === 'DOT') {
      this.advance();
      const name = this.expect('IDENT', '期望变量名');
      return { category: first.value, name: name.value };
    }
    // 无 `.` → 参数引用：裸参数名
    return { category: PARAMETER_CATEGORY, name: first.value };
  }

  /** 解析右值：字符串、数字、变量引用、参数引用、括号列表 */
  parseValue(libs?: LibraryData): string {
    const t = this.peek();

    // 字符串字面量
    if (t.type === 'STRING') {
      this.advance();
      return `<value content="${escapeXml(t.value)}" type="Input"/>`;
    }

    // 数字字面量
    if (t.type === 'NUMBER') {
      this.advance();
      return `<value content="${escapeXml(t.value)}" type="Input"/>`;
    }

    // 布尔字面量
    if (t.type === 'BOOLEAN') {
      this.advance();
      return `<value content="${t.value}" type="Input"/>`;
    }

    // 括号列表 (v1, v2, ...) — 用于 In / NotIn
    if (t.type === 'LPAREN') {
      this.advance();
      const items: string[] = [];
      while (this.peek().type !== 'RPAREN') {
        if (items.length > 0) {
          this.expect('COMMA', '期望 ","');
        }
        const vt = this.peek();
        if (vt.type === 'STRING') {
          this.advance();
          items.push(escapeXml(vt.value));
        } else if (vt.type === 'NUMBER') {
          this.advance();
          items.push(escapeXml(vt.value));
        } else if (vt.type === 'IDENT') {
          // 可能是变量引用
          const ref = this.parseRef();
          const info = lookupVar(ref.category, ref.name, libs);
          items.push(escapeXml(`${ref.category}.${ref.name}`));
          void info; // In 列表中暂时只取文本值
        } else {
          throw new ParseError('期望值', vt.pos);
        }
      }
      this.expect('RPAREN', '期望 ")"');
      return `<value content="${items.join(',')}" type="Input"/>`;
    }

    // 变量引用 / 参数引用
    if (t.type === 'IDENT') {
      const ref = this.parseRef();
      const info = lookupVar(ref.category, ref.name, libs);
      if (info.kind === 'parameter') {
        return `<value var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="Parameter"/>`;
      }
      return `<value var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="Variable"/>`;
    }

    throw new ParseError('期望值（字符串、数字或变量引用）', t.pos);
  }
}

// ─── Left 元素生成 ───

function buildLeftXml(info: VarInfo): string {
  if (info.kind === 'parameter') {
    return `<left var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="parameter">\n      </left>`;
  }
  return `<left var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="variable">\n      </left>`;
}

// ─── 条件解析 ───

/** 解析单个 atom：支持 `A.B op value` 和 boolean 隐式 `A.B` → `A.B == true` */
function parseSingleAtom(parser: Parser, libs?: LibraryData): string {
  const ref = parser.parseRef();
  const leftInfo = lookupVar(ref.category, ref.name, libs);
  const leftXml = buildLeftXml(leftInfo);

  // Boolean 隐式展开：后面不是操作符，自动当作 == true
  const next = parser.peek();
  if (next.type !== 'OP' && leftInfo.datatype === 'Boolean') {
    return `<atom op="Equals">\n      ${leftXml}\n      <value content="true" type="Input"/>\n    </atom>`;
  }

  const opToken = parser.expect('OP', '期望操作符');
  const xmlOp = textToXmlOp.get(opToken.value);
  if (!xmlOp) {
    throw new ParseError(`未知操作符: ${opToken.value}`, opToken.pos);
  }
  const valueXml = parser.parseValue(libs);
  return `<atom op="${xmlOp}">\n      ${leftXml}\n      ${valueXml}\n    </atom>`;
}

/**
 * 解析一个条件单元：atom 或 括号分组。
 * 括号内递归调用 parseExpression，生成嵌套 junction XML。
 */
function parseUnit(parser: Parser, libs?: LibraryData): string {
  if (parser.peek().type === 'LPAREN') {
    parser.advance();
    const innerXml = parseExpression(parser, libs);
    parser.expect('RPAREN', '期望 ")"');
    return innerXml;
  }
  return parseSingleAtom(parser, libs);
}

/**
 * 解析条件表达式（递归）：unit (AND|OR unit)*
 * 同层不允许混用 AND/OR，不同层（括号内外）可以不同。
 */
function parseExpression(parser: Parser, libs?: LibraryData): string {
  const units: string[] = [];
  let junction: 'and' | 'or' | null = null;

  units.push(parseUnit(parser, libs));

  while (!parser.isAtEnd()) {
    const jt = parser.peek();
    if (jt.type !== 'AND' && jt.type !== 'OR') break;

    const currentJunction = jt.type === 'AND' ? 'and' : 'or';
    if (junction === null) {
      junction = currentJunction;
    } else if (junction !== currentJunction) {
      throw new ParseError(
        '同层不支持混合 AND/OR，请用括号分组',
        jt.pos,
      );
    }
    parser.advance();
    units.push(parseUnit(parser, libs));
  }

  const tag = junction ?? 'and';
  const inner = units.map((u) => `    ${u}`).join('\n');
  return `<${tag}>\n${inner}\n  </${tag}>`;
}

/**
 * 解析条件表达式入口
 */
export function parseCondition(text: string, libs?: LibraryData): string {
  const trimmed = text.trim();
  if (!trimmed) throw new ParseError('条件表达式不能为空', 0);

  const tokens = tokenize(trimmed);
  const parser = new Parser(tokens);
  const xml = parseExpression(parser, libs);

  if (!parser.isAtEnd()) {
    const t = parser.peek();
    throw new ParseError(`意外的内容: ${t.value}`, t.pos);
  }

  return xml;
}

// ─── 赋值解析 ───

function parseOneAssignment(parser: Parser, libs?: LibraryData): string {
  const ref = parser.parseRef();
  const info = lookupVar(ref.category, ref.name, libs);
  parser.expect('EQ', '期望 "="');
  const valueXml = parser.parseValue(libs);

  if (info.kind === 'parameter') {
    return `<var-assign var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="parameter">\n    ${valueXml}\n  </var-assign>`;
  }
  return `<var-assign var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="variable">\n    ${valueXml}\n  </var-assign>`;
}

/**
 * 解析赋值表达式，返回多个 <var-assign> XML 拼接
 */
export function parseAssignment(text: string, libs?: LibraryData): string {
  const trimmed = text.trim();
  if (!trimmed) throw new ParseError('赋值表达式不能为空', 0);

  const tokens = tokenize(trimmed);
  const parser = new Parser(tokens);
  const assigns: string[] = [];

  assigns.push(parseOneAssignment(parser, libs));

  while (!parser.isAtEnd()) {
    if (parser.peek().type === 'SEMI') {
      parser.advance();
      if (parser.isAtEnd()) break; // 尾部分号容忍
      assigns.push(parseOneAssignment(parser, libs));
    } else {
      const t = parser.peek();
      throw new ParseError(`期望 ";" 或结束，但得到 ${t.value}`, t.pos);
    }
  }

  return assigns.join('\n  ');
}

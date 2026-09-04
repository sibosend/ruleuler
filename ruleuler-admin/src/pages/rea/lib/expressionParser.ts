/**
 * expressionParser.ts — 文本表达式 → XML
 *
 * 条件: `类别.变量 操作符 值 [AND|OR ...]` → <and>/<or> + <atom> XML
 * 动作: `类别.变量 = 值` 或 `LISTADD(...)` `[; ...]` → <var-assign> / <execute-method>
 */

import { textToXmlOp, WORD_OPS } from './operatorMap';
import { isReservedIdent, suggestReserved } from './reservedWords';
import {
  FLAT_FUNC_NAMES,
  lookupByRea,
  lookupNamespaced,
  isNamespace,
  expectedArity,
  buildMethodValueXml,
  buildMethodLeftXml,
  buildExecuteMethodXml,
  buildCommonValueXml,
  buildCommonLeftXml,
  escapeXml,
  type BuiltinFunc,
} from './functionMap';

export const PARAMETER_CATEGORY = '__parameter__';

export interface ActionMethodDef {
  name: string;
  methodName: string;
  parameters: Array<{ name: string; type: string }>;
}

export interface ActionBeanDef {
  id: string;
  name: string;
  methods: ActionMethodDef[];
}

export interface LibraryData {
  variables: Array<{
    name: string;
    variables: Array<{ name: string; label: string; type: string }>;
  }>;
  parameters: Array<{ name: string; label: string; type: string }>;
  /** 项目动作库（不含 urule.* 内置） */
  actions?: ActionBeanDef[];
}

const CUSTOM_NAME = /^[A-Z][A-Z0-9_]*$/;

function paramTypeOf(raw: unknown): string {
  if (typeof raw === 'string' && raw) return raw;
  if (raw && typeof raw === 'object' && 'name' in raw && typeof (raw as { name: unknown }).name === 'string') {
    return (raw as { name: string }).name;
  }
  return 'Object';
}

/** 从 loadXml JSON 抽出项目动作库 Bean，跳过 urule.* 内置 */
export function extractActionBeans(libJsonArray: unknown[]): ActionBeanDef[] {
  const out: ActionBeanDef[] = [];
  for (const raw of libJsonArray) {
    if (!raw || typeof raw !== 'object') continue;
    const beans = (raw as { springBeans?: unknown }).springBeans;
    if (!Array.isArray(beans)) continue;
    for (const b of beans) {
      if (!b || typeof b !== 'object') continue;
      const id = String((b as { id?: unknown }).id ?? '');
      if (!id || id.startsWith('urule.')) continue;
      const methodsRaw = (b as { methods?: unknown }).methods;
      const methods: ActionMethodDef[] = [];
      if (Array.isArray(methodsRaw)) {
        for (const m of methodsRaw) {
          if (!m || typeof m !== 'object') continue;
          const rec = m as Record<string, unknown>;
          const methodName = String(rec.methodName ?? rec['method-name'] ?? '');
          if (!methodName) continue;
          const paramsRaw = rec.parameters;
          const parameters: ActionMethodDef['parameters'] = [];
          if (Array.isArray(paramsRaw)) {
            for (const p of paramsRaw) {
              if (!p || typeof p !== 'object') continue;
              const pr = p as Record<string, unknown>;
              parameters.push({
                name: String(pr.name ?? ''),
                type: paramTypeOf(pr.type),
              });
            }
          }
          methods.push({
            name: String(rec.name ?? methodName),
            methodName,
            parameters,
          });
        }
      }
      out.push({
        id,
        name: String((b as { name?: unknown }).name ?? id),
        methods,
      });
    }
  }
  return out;
}

export function lookupCustom(
  libs: LibraryData | undefined,
  beanRea: string,
  methodRea: string,
): (ActionBeanDef & { method: ActionMethodDef }) | undefined {
  if (!libs?.actions) return undefined;
  const b = beanRea.toUpperCase();
  const m = methodRea.toUpperCase();
  for (const bean of libs.actions) {
    if (bean.id.toUpperCase() !== b) continue;
    const method = bean.methods.find((x) => x.methodName.toUpperCase() === m);
    if (method) return { ...bean, method };
  }
  return undefined;
}

function customToFunc(hit: NonNullable<ReturnType<typeof lookupCustom>>): BuiltinFunc {
  return {
    rea: `${hit.id.toUpperCase()}.${hit.method.methodName.toUpperCase()}`,
    kind: 'both',
    engine: 'method',
    returnType: 'Object',
    bean: hit.id,
    beanLabel: hit.name,
    method: hit.method.methodName,
    methodLabel: hit.method.name,
    params: hit.method.parameters,
  };
}

export interface ParseOptions {
  /** 默认 true。灰度条件必须 false：拒绝函数 */
  allowFunctions?: boolean;
}

export class ParseError extends Error {
  position: number;
  constructor(message: string, position: number) {
    super(message);
    this.name = 'ParseError';
    this.position = position;
  }
}

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
  | 'FUNC'
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

const SYMBOL_OPS = ['>=', '<=', '!=', '==', '>', '<'] as const;
const FLAT_FUNC_SET = new Set(FLAT_FUNC_NAMES);
const WORD_OP_SET = new Set(WORD_OPS);

function tokenize(text: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  const len = text.length;

  while (i < len) {
    if (/\s/.test(text[i]!)) {
      i++;
      continue;
    }

    const pos = i;

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
      i++;
      tokens.push({ type: 'STRING', value: val, pos });
      continue;
    }

    if (text[i] === ';') {
      tokens.push({ type: 'SEMI', value: ';', pos });
      i++;
      continue;
    }
    if (text[i] === '.') {
      tokens.push({ type: 'DOT', value: '.', pos });
      i++;
      continue;
    }
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

    if (text[i] === '=') {
      tokens.push({ type: 'EQ', value: '=', pos });
      i++;
      continue;
    }

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

    if (/[a-zA-Z_\u4e00-\u9fff]/.test(text[i]!)) {
      let word = '';
      while (i < len && /[a-zA-Z0-9_\u4e00-\u9fff]/.test(text[i]!)) {
        word += text[i];
        i++;
      }

      if (word === 'TRUE' || word === 'FALSE') {
        tokens.push({ type: 'BOOLEAN', value: word, pos });
      } else if (word === 'AND') {
        tokens.push({ type: 'AND', value: 'AND', pos });
      } else if (word === 'OR') {
        tokens.push({ type: 'OR', value: 'OR', pos });
      } else if (WORD_OP_SET.has(word)) {
        tokens.push({ type: 'OP', value: word, pos });
      } else if (FLAT_FUNC_SET.has(word)) {
        tokens.push({ type: 'FUNC', value: word, pos });
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

// ─── Parser ───

class Parser {
  private tokens: Token[];
  private pos: number;
  readonly options: ParseOptions;

  constructor(tokens: Token[], options: ParseOptions) {
    this.tokens = tokens;
    this.pos = 0;
    this.options = options;
  }

  peek(): Token {
    return this.tokens[this.pos]!;
  }

  peekNth(n: number): Token {
    return this.tokens[this.pos + n] ?? this.tokens[this.tokens.length - 1]!;
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

  /** 解析 `类别.变量名` 或裸参数名。点后字段可以是保留字（FUNC）。 */
  parseRef(): { category: string; name: string } {
    const first = this.peek();
    if (first.type === 'FUNC') {
      if (this.peekNth(1).type === 'DOT') {
        throw new ParseError(`${first.value} 不能当类别名`, first.pos);
      }
      throw new ParseError('函数需要括号', first.pos);
    }
    if (first.type !== 'IDENT') {
      throw new ParseError('期望变量类别或参数名', first.pos);
    }

    if (this.peekNth(1).type === 'DOT') {
      if (isReservedIdent(first.value)) {
        throw new ParseError(`${first.value} 不能当类别名`, first.pos);
      }
      this.advance();
      this.advance();
      const nameTok = this.peek();
      if (nameTok.type !== 'IDENT' && nameTok.type !== 'FUNC') {
        throw new ParseError('期望变量名', nameTok.pos);
      }
      this.advance();
      return { category: first.value, name: nameTok.value };
    }

    const near = suggestReserved(first.value);
    if (near === 'TRUE' || near === 'FALSE') {
      throw new ParseError('请使用 TRUE/FALSE', first.pos);
    }
    if (isReservedIdent(first.value)) {
      throw new ParseError(`${first.value} 不能当参数名`, first.pos);
    }
    this.advance();
    return { category: PARAMETER_CATEGORY, name: first.value };
  }

  parseValue(libs?: LibraryData): string {
    const t = this.peek();

    if (t.type === 'STRING') {
      this.advance();
      return `<value content="${escapeXml(t.value)}" type="Input"/>`;
    }
    if (t.type === 'NUMBER') {
      this.advance();
      return `<value content="${escapeXml(t.value)}" type="Input"/>`;
    }
    if (t.type === 'BOOLEAN') {
      this.advance();
      const xmlBool = t.value === 'TRUE' ? 'true' : 'false';
      return `<value content="${xmlBool}" type="Input"/>`;
    }

    const call = detectCall(this);
    if (call) {
      const inv = parseInvocation(this, libs, 'value');
      return inv.xml;
    }

    if (t.type === 'FUNC') {
      throw new ParseError('函数需要括号', t.pos);
    }

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
        } else if (vt.type === 'IDENT' || vt.type === 'FUNC') {
          const ref = this.parseRef();
          items.push(escapeXml(`${ref.category}.${ref.name}`));
        } else {
          throw new ParseError('期望值', vt.pos);
        }
      }
      this.expect('RPAREN', '期望 ")"');
      return `<value content="${items.join(',')}" type="Input"/>`;
    }

    if (t.type === 'IDENT') {
      const near = suggestReserved(t.value);
      if (near === 'TRUE' || near === 'FALSE') {
        throw new ParseError('请使用 TRUE/FALSE', t.pos);
      }
      const ref = this.parseRef();
      const info = lookupVar(ref.category, ref.name, libs);
      if (info.kind === 'parameter') {
        return `<value var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="Parameter"/>`;
      }
      return `<value var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="Variable"/>`;
    }

    throw new ParseError('期望值（字符串、数字、变量引用或函数）', t.pos);
  }
}

interface CallHead {
  ns?: string;
  name: string;
  pos: number;
  unknown: boolean;
}

function detectCall(parser: Parser): CallHead | null {
  const t0 = parser.peek();
  const t1 = parser.peekNth(1);
  const t2 = parser.peekNth(2);
  const t3 = parser.peekNth(3);

  if (t0.type === 'FUNC' && t1.type === 'LPAREN') {
    return { name: t0.value, pos: t0.pos, unknown: false };
  }
  if (t0.type === 'IDENT' && t1.type === 'DOT' && (t2.type === 'FUNC' || t2.type === 'IDENT') && t3.type === 'LPAREN') {
    if (isNamespace(t0.value)) {
      return { ns: t0.value, name: t2.value, pos: t0.pos, unknown: false };
    }
    return { ns: t0.value, name: t2.value, pos: t0.pos, unknown: true };
  }
  if (t0.type === 'IDENT' && t1.type === 'LPAREN') {
    return { name: t0.value, pos: t0.pos, unknown: true };
  }
  return null;
}

interface InvocationResult {
  xml: string;
  fn: BuiltinFunc;
}

function resolveFunc(head: CallHead, libs?: LibraryData): BuiltinFunc {
  if (!head.unknown) {
    if (head.ns) {
      const fn = lookupNamespaced(head.ns, head.name);
      if (!fn) {
        throw new ParseError(`未知函数: ${head.ns}.${head.name}`, head.pos);
      }
      return fn;
    }
    const fn = lookupByRea(head.name);
    if (!fn) {
      throw new ParseError(`未知函数: ${head.name}`, head.pos);
    }
    return fn;
  }

  if (!head.ns) {
    const near = suggestReserved(head.name);
    if (near && FLAT_FUNC_SET.has(near)) {
      throw new ParseError(`请使用 ${near}`, head.pos);
    }
    throw new ParseError(`未知函数: ${head.name}`, head.pos);
  }

  const hit = lookupCustom(libs, head.ns, head.name);
  if (hit) {
    const rea = `${hit.id.toUpperCase()}.${hit.method.methodName.toUpperCase()}`;
    if (!CUSTOM_NAME.test(head.ns) || !CUSTOM_NAME.test(head.name) || `${head.ns}.${head.name}` !== rea) {
      throw new ParseError(`请使用 ${rea}`, head.pos);
    }
    return customToFunc(hit);
  }
  throw new ParseError(`未知函数: ${head.ns}.${head.name}`, head.pos);
}

function parsePropertyName(parser: Parser): string {
  const t = parser.peek();
  if (t.type !== 'IDENT' && t.type !== 'FUNC') {
    throw new ParseError('聚合第二参必须是属性名', t.pos);
  }
  parser.advance();
  return t.value;
}

function parseInvocation(
  parser: Parser,
  libs: LibraryData | undefined,
  ctx: 'value' | 'left' | 'action',
): InvocationResult {
  const head = detectCall(parser);
  if (!head) {
    throw new ParseError('期望函数调用', parser.peek().pos);
  }
  if (parser.options.allowFunctions === false) {
    throw new ParseError('灰度条件不支持函数', head.pos);
  }

  const fn = resolveFunc(head, libs);

  if (ctx === 'action' && fn.kind === 'value') {
    throw new ParseError(`${fn.rea} 是值函数，必须写成 y = ${fn.rea}(...)`, head.pos);
  }
  if ((ctx === 'value' || ctx === 'left') && fn.kind === 'action') {
    throw new ParseError(`${fn.rea} 没有返回值，不能写在赋值或条件里`, head.pos);
  }

  // 消耗头
  if (head.ns) {
    parser.advance(); // ns
    parser.advance(); // DOT
    parser.advance(); // name
  } else {
    parser.advance(); // FUNC
  }
  parser.expect('LPAREN', '期望 "("');

  const arity = expectedArity(fn);
  const argXmls: string[] = [];
  let property: string | undefined;

  if (parser.peek().type !== 'RPAREN') {
    if (fn.engine === 'commonfunction') {
      argXmls.push(parser.parseValue(libs));
      if (fn.needProperty) {
        parser.expect('COMMA', `${fn.rea} 需要属性名作为第二参`);
        property = parsePropertyName(parser);
      }
    } else {
      const params = fn.params ?? [];
      for (let i = 0; ; i++) {
        if (params[i]?.propertyName) {
          // 属性名槽位：裸名或字符串字面量，编译成 Input（不当变量/参数引用）
          const t = parser.peek();
          if (t.type === 'IDENT' || t.type === 'FUNC' || t.type === 'STRING') {
            parser.advance();
            argXmls.push(`<value content="${escapeXml(t.value)}" type="Input"/>`);
          } else {
            throw new ParseError('属性名必须是裸名', t.pos);
          }
        } else {
          argXmls.push(parser.parseValue(libs));
        }
        if (parser.peek().type !== 'COMMA') break;
        parser.advance();
      }
    }
  }
  parser.expect('RPAREN', '期望 ")"');

  if (argXmls.length !== (fn.engine === 'commonfunction' ? 1 : arity) || (fn.needProperty && !property)) {
    throw new ParseError(`${fn.rea} 需要 ${arity} 个参数`, head.pos);
  }
  if (fn.engine !== 'commonfunction' && argXmls.length !== arity) {
    throw new ParseError(`${fn.rea} 需要 ${arity} 个参数`, head.pos);
  }

  let xml: string;
  if (fn.engine === 'commonfunction') {
    if (ctx === 'left') xml = buildCommonLeftXml(fn, argXmls[0]!, property);
    else if (ctx === 'action') throw new ParseError(`${fn.rea} 是值函数，必须写成 y = ${fn.rea}(...)`, head.pos);
    else xml = buildCommonValueXml(fn, argXmls[0]!, property);
  } else if (ctx === 'left') {
    xml = buildMethodLeftXml(fn, argXmls);
  } else if (ctx === 'action') {
    xml = buildExecuteMethodXml(fn, argXmls);
  } else {
    xml = buildMethodValueXml(fn, argXmls);
  }

  return { xml, fn };
}

function buildLeftXml(info: VarInfo): string {
  if (info.kind === 'parameter') {
    return `<left var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="parameter">\n      </left>`;
  }
  return `<left var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="variable">\n      </left>`;
}

function expectOp(parser: Parser): Token {
  const t = parser.peek();
  if (t.type === 'OP') return parser.advance();
  if (t.type === 'IDENT') {
    const s = suggestReserved(t.value);
    if (s && WORD_OP_SET.has(s)) {
      throw new ParseError(`请使用 ${s}`, t.pos);
    }
  }
  throw new ParseError('期望操作符', t.pos);
}

function parseSingleAtom(parser: Parser, libs?: LibraryData): string {
  const call = detectCall(parser);
  if (call) {
    const inv = parseInvocation(parser, libs, 'left');
    const next = parser.peek();
    if (next.type !== 'OP') {
      if (inv.fn.returnType === 'Boolean') {
        return `<atom op="Equals">\n      ${inv.xml}\n      <value content="true" type="Input"/>\n    </atom>`;
      }
      throw new ParseError('期望操作符', next.pos);
    }
    const opToken = expectOp(parser);
    const xmlOp = textToXmlOp.get(opToken.value);
    if (!xmlOp) throw new ParseError(`未知操作符: ${opToken.value}`, opToken.pos);
    const valueXml = parser.parseValue(libs);
    return `<atom op="${xmlOp}">\n      ${inv.xml}\n      ${valueXml}\n    </atom>`;
  }

  const ref = parser.parseRef();
  const leftInfo = lookupVar(ref.category, ref.name, libs);
  const leftXml = buildLeftXml(leftInfo);

  const next = parser.peek();
  if (next.type !== 'OP' && leftInfo.datatype === 'Boolean') {
    return `<atom op="Equals">\n      ${leftXml}\n      <value content="true" type="Input"/>\n    </atom>`;
  }

  const opToken = expectOp(parser);
  const xmlOp = textToXmlOp.get(opToken.value);
  if (!xmlOp) {
    throw new ParseError(`未知操作符: ${opToken.value}`, opToken.pos);
  }
  const valueXml = parser.parseValue(libs);
  return `<atom op="${xmlOp}">\n      ${leftXml}\n      ${valueXml}\n    </atom>`;
}

function parseUnit(parser: Parser, libs?: LibraryData): string {
  if (parser.peek().type === 'LPAREN') {
    parser.advance();
    const innerXml = parseExpression(parser, libs);
    parser.expect('RPAREN', '期望 ")"');
    return innerXml;
  }
  return parseSingleAtom(parser, libs);
}

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
      throw new ParseError('同层不支持混合 AND/OR，请用括号分组', jt.pos);
    }
    parser.advance();
    units.push(parseUnit(parser, libs));
  }

  const tag = junction ?? 'and';
  const inner = units.map((u) => `    ${u}`).join('\n');
  return `<${tag}>\n${inner}\n  </${tag}>`;
}

export function parseCondition(text: string, libs?: LibraryData, options?: ParseOptions): string {
  const trimmed = text.trim();
  if (!trimmed) throw new ParseError('条件表达式不能为空', 0);

  const tokens = tokenize(trimmed);
  const parser = new Parser(tokens, options ?? {});
  const xml = parseExpression(parser, libs);

  if (!parser.isAtEnd()) {
    const t = parser.peek();
    throw new ParseError(`意外的内容: ${t.value}`, t.pos);
  }

  return xml;
}

function parseOneAction(parser: Parser, libs?: LibraryData): string {
  const call = detectCall(parser);
  if (call) {
    const inv = parseInvocation(parser, libs, 'action');
    return inv.xml;
  }
  if (parser.peek().type === 'FUNC') {
    throw new ParseError('函数需要括号', parser.peek().pos);
  }

  const ref = parser.parseRef();
  const info = lookupVar(ref.category, ref.name, libs);
  parser.expect('EQ', '期望 "="');
  const valueXml = parser.parseValue(libs);

  if (info.kind === 'parameter') {
    return `<var-assign var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="parameter">\n    ${valueXml}\n  </var-assign>`;
  }
  return `<var-assign var-category="${escapeXml(info.category)}" var="${escapeXml(info.name)}" var-label="${escapeXml(info.label)}" datatype="${escapeXml(info.datatype)}" type="variable">\n    ${valueXml}\n  </var-assign>`;
}

/** 解析那么/否则：赋值或动作函数，分号分隔 */
export function parseAction(text: string, libs?: LibraryData, options?: ParseOptions): string {
  const trimmed = text.trim();
  if (!trimmed) throw new ParseError('赋值表达式不能为空', 0);

  const tokens = tokenize(trimmed);
  const parser = new Parser(tokens, options ?? {});
  const parts: string[] = [];

  parts.push(parseOneAction(parser, libs));

  while (!parser.isAtEnd()) {
    if (parser.peek().type === 'SEMI') {
      parser.advance();
      if (parser.isAtEnd()) break;
      parts.push(parseOneAction(parser, libs));
    } else {
      const t = parser.peek();
      throw new ParseError(`期望 ";" 或结束，但得到 ${t.value}`, t.pos);
    }
  }

  return parts.join('\n  ');
}

/** @deprecated 用 parseAction；保留别名避免调用点大改 */
export const parseAssignment = parseAction;

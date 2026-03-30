/**
 * expressionParser/Printer round-trip 属性测试
 * Feature: ruleset-jql-editor, Property 1 & 2
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  parseCondition,
  parseAssignment,
  type LibraryData,
} from '@/pages/rea/lib/expressionParser';
import { printCondition, printAssignment } from '@/pages/rea/lib/expressionPrinter';
import { ALL_TEXT_OPERATORS } from '@/pages/rea/lib/operatorMap';

// ─── 辅助：规范化 XML 用于比较（忽略空白差异） ───

function normalizeXml(xml: string): string {
  return xml
    .replace(/>\s+</g, '><')
    .replace(/\s+/g, ' ')
    .trim();
}

// ─── 生成器：安全标识符（不与关键字冲突） ───

const KEYWORDS = new Set([
  'AND', 'OR',
  ...ALL_TEXT_OPERATORS,
  '参数', // 参数是特殊类别名，避免冲突
]);

/** 生成中文标识符（2-4 个汉字），避免关键字冲突 */
const chineseIdent = fc
  .array(
    fc.integer({ min: 0x4e00, max: 0x9fa5 }).map((c) => String.fromCharCode(c)),
    { minLength: 2, maxLength: 4 },
  )
  .map((arr) => arr.join(''))
  .filter((s) => !KEYWORDS.has(s));

/** 生成英文标识符（小写字母开头，3-6 字符），避免关键字冲突 */
const englishIdent = fc
  .tuple(
    fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
    fc.array(
      fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
      { minLength: 2, maxLength: 5 },
    ),
  )
  .map(([first, rest]) => first + rest.join(''))
  .filter((s) => !KEYWORDS.has(s));

/** 混合标识符生成器 */
const safeIdent = fc.oneof(chineseIdent, englishIdent);

// ─── 生成器：值 ───

/** 安全字符串值（不含双引号和反斜杠，避免转义复杂性） */
const safeStringValue = fc
  .array(
    fc.oneof(
      fc.integer({ min: 0x4e00, max: 0x9fa5 }).map((c) => String.fromCharCode(c)),
      fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
      fc.integer({ min: 48, max: 57 }).map((c) => String.fromCharCode(c)),
    ),
    { minLength: 1, maxLength: 8 },
  )
  .map((arr) => arr.join(''));

/** 整数值 */
const intValue = fc.integer({ min: -9999, max: 9999 }).map(String);

/** 简单小数值 */
const decimalValue = fc
  .tuple(
    fc.integer({ min: 0, max: 999 }),
    fc.integer({ min: 1, max: 99 }),
  )
  .map(([i, d]) => `${i}.${d}`);

/** 数字值 */
const numericValue = fc.oneof(intValue, decimalValue);

// ─── 条件操作符（排除 In/NotIn，它们需要括号列表值） ───

const simpleOps = ALL_TEXT_OPERATORS.filter(
  (op) => op !== 'In' && op !== 'NotIn',
);
const simpleOpArb = fc.constantFrom(...simpleOps);
const inOpArb = fc.constantFrom('In', 'NotIn');

// ─── 生成器：条件表达式文本 + 对应 LibraryData ───

interface AtomSpec {
  category: string;
  varName: string;
  op: string;
  value: string; // 格式化后的值文本
}

/** 生成简单值（字符串或数字） */
const simpleValueText = fc.oneof(
  safeStringValue.map((s) => `"${s}"`),
  numericValue,
);

/** 生成 In/NotIn 列表值 */
const inListValue = fc
  .array(
    fc.oneof(
      safeStringValue.map((s) => `"${s}"`),
      numericValue,
    ),
    { minLength: 1, maxLength: 4 },
  )
  .map((items) => `(${items.join(', ')})`);

/** 生成单个原子条件 */
const simpleAtom: fc.Arbitrary<AtomSpec> = fc
  .tuple(safeIdent, safeIdent, simpleOpArb, simpleValueText)
  .map(([cat, varName, op, val]) => ({ category: cat, varName, op, value: val }));

const inAtom: fc.Arbitrary<AtomSpec> = fc
  .tuple(safeIdent, safeIdent, inOpArb, inListValue)
  .map(([cat, varName, op, val]) => ({ category: cat, varName, op, value: val }));

const atomArb: fc.Arbitrary<AtomSpec> = fc.oneof(
  { weight: 4, arbitrary: simpleAtom },
  { weight: 1, arbitrary: inAtom },
);

/** 生成条件表达式（1-4 个原子，同一连接词） */
const conditionExprArb = fc
  .tuple(
    fc.array(atomArb, { minLength: 1, maxLength: 4 }),
    fc.constantFrom('AND', 'OR'),
  )
  .map(([atoms, junction]) => {
    const text = atoms
      .map((a) => `${a.category}.${a.varName} ${a.op} ${a.value}`)
      .join(` ${junction} `);
    return { atoms, text, junction };
  });

/** 从 AtomSpec[] 构建 LibraryData */
function buildLibsFromAtoms(atoms: AtomSpec[]): LibraryData {
  const catMap = new Map<string, Set<string>>();
  for (const a of atoms) {
    if (!catMap.has(a.category)) catMap.set(a.category, new Set());
    catMap.get(a.category)!.add(a.varName);
  }
  return {
    variables: Array.from(catMap.entries()).map(([name, vars]) => ({
      name,
      variables: Array.from(vars).map((v) => ({
        name: v,
        label: v,
        type: 'String',
      })),
    })),
    parameters: [],
  };
}

// ─── 生成器：赋值表达式 ───

interface AssignSpec {
  category: string;
  varName: string;
  value: string;
}

const assignArb: fc.Arbitrary<AssignSpec> = fc
  .tuple(safeIdent, safeIdent, simpleValueText)
  .map(([cat, varName, val]) => ({ category: cat, varName, value: val }));

const assignmentExprArb = fc
  .array(assignArb, { minLength: 1, maxLength: 3 })
  .map((assigns) => {
    const text = assigns
      .map((a) => `${a.category}.${a.varName} = ${a.value}`)
      .join('; ');
    return { assigns, text };
  });

function buildLibsFromAssigns(assigns: AssignSpec[]): LibraryData {
  const catMap = new Map<string, Set<string>>();
  for (const a of assigns) {
    if (!catMap.has(a.category)) catMap.set(a.category, new Set());
    catMap.get(a.category)!.add(a.varName);
  }
  return {
    variables: Array.from(catMap.entries()).map(([name, vars]) => ({
      name,
      variables: Array.from(vars).map((v) => ({
        name: v,
        label: v,
        type: 'String',
      })),
    })),
    parameters: [],
  };
}


// ============================================================
// Feature: ruleset-jql-editor, Property 1: 表达式 round-trip
// Validates: Requirements 8.1, 8.2, 8.3, 8.5, 8.6
// ============================================================
describe('Property 1: 表达式 round-trip（解析/格式化往返）', () => {
  it('条件表达式: text → XML → text → XML 语义等价', () => {
    fc.assert(
      fc.property(conditionExprArb, ({ atoms, text }) => {
        const libs = buildLibsFromAtoms(atoms);

        // text → XML
        const xml1 = parseCondition(text, libs);
        // XML → text
        const printed = printCondition(`<if>${xml1}</if>`);
        expect(printed.hasError).toBe(false);
        // text → XML (second pass)
        const xml2 = parseCondition(printed.text, libs);

        // 比较两次 XML 语义等价
        expect(normalizeXml(xml2)).toBe(normalizeXml(xml1));
      }),
      { numRuns: 200 },
    );
  });

  it('赋值表达式: text → XML → text → XML 语义等价', () => {
    fc.assert(
      fc.property(assignmentExprArb, ({ assigns, text }) => {
        const libs = buildLibsFromAssigns(assigns);

        // text → XML
        const xml1 = parseAssignment(text, libs);
        // XML → text (wrap in <then> for printer)
        const printed = printAssignment(`<then>${xml1}</then>`);
        expect(printed.hasError).toBe(false);
        // text → XML (second pass)
        const xml2 = parseAssignment(printed.text, libs);

        expect(normalizeXml(xml2)).toBe(normalizeXml(xml1));
      }),
      { numRuns: 200 },
    );
  });
});

// ============================================================
// Feature: ruleset-jql-editor, Property 2: XML round-trip
// Validates: Requirements 8.4, 8.6
// ============================================================

// ─── 生成器：构造有效 XML 片段 ───

/** 生成 <atom> XML */
function buildAtomXml(cat: string, varName: string, op: string, content: string): string {
  return `<atom op="${op}"><left var-category="${cat}" var="${varName}" var-label="${varName}" datatype="String" type="variable"></left><value content="${content}" type="Input"/></atom>`;
}

/** XML op 值列表（排除 In/NotIn 简化测试） */
const xmlOps = [
  'Equals', 'NotEquals', 'GreaterThen', 'GreaterThenEquals',
  'LessThen', 'LessThenEquals', 'Contain', 'NotContain',
  'Match', 'NotMatch', 'StartWith', 'NotStartWith',
  'EndWith', 'NotEndWith', 'EqualsIgnoreCase', 'NotEqualsIgnoreCase',
];
const xmlOpArb = fc.constantFrom(...xmlOps);

/** 生成安全的 XML content 值（不含 XML 特殊字符） */
const safeXmlContent = fc
  .array(
    fc.oneof(
      fc.integer({ min: 0x4e00, max: 0x9fa5 }).map((c) => String.fromCharCode(c)),
      fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
      fc.integer({ min: 48, max: 57 }).map((c) => String.fromCharCode(c)),
    ),
    { minLength: 1, maxLength: 8 },
  )
  .map((arr) => arr.join(''));

/** 生成条件 XML 片段 */
const conditionXmlArb = fc
  .tuple(
    fc.array(
      fc.tuple(safeIdent, safeIdent, xmlOpArb, safeXmlContent),
      { minLength: 1, maxLength: 4 },
    ),
    fc.constantFrom('and', 'or'),
  )
  .map(([atoms, junction]) => {
    const atomsXml = atoms
      .map(([cat, varName, op, content]) => buildAtomXml(cat, varName, op, content))
      .join('');
    const xml = `<if><${junction}>${atomsXml}</${junction}></if>`;
    const libs: LibraryData = {
      variables: atoms.map(([cat, varName]) => ({
        name: cat,
        variables: [{ name: varName, label: varName, type: 'String' }],
      })),
      parameters: [],
    };
    return { xml, libs };
  });

/** 生成 <var-assign> XML */
function buildVarAssignXml(cat: string, varName: string, content: string): string {
  return `<var-assign var-category="${cat}" var="${varName}" var-label="${varName}" datatype="String" type="variable"><value content="${content}" type="Input"/></var-assign>`;
}

/** 生成赋值 XML 片段 */
const assignmentXmlArb = fc
  .array(
    fc.tuple(safeIdent, safeIdent, safeXmlContent),
    { minLength: 1, maxLength: 3 },
  )
  .map((assigns) => {
    const assignsXml = assigns
      .map(([cat, varName, content]) => buildVarAssignXml(cat, varName, content))
      .join('');
    const xml = `<then>${assignsXml}</then>`;
    const libs: LibraryData = {
      variables: assigns.map(([cat, varName]) => ({
        name: cat,
        variables: [{ name: varName, label: varName, type: 'String' }],
      })),
      parameters: [],
    };
    return { xml, libs };
  });

describe('Property 2: XML round-trip（XML 往返）', () => {
  it('条件 XML: XML → text → XML 语义等价', () => {
    fc.assert(
      fc.property(conditionXmlArb, ({ xml, libs }) => {
        // XML → text
        const printed = printCondition(xml);
        expect(printed.hasError).toBe(false);
        // text → XML
        const parsedXml = parseCondition(printed.text, libs);
        // 重新包装为 <if> 以便再次 print
        const reprinted = printCondition(`<if>${parsedXml}</if>`);
        expect(reprinted.hasError).toBe(false);

        // 比较两次 print 的文本应一致
        expect(reprinted.text).toBe(printed.text);
      }),
      { numRuns: 200 },
    );
  });

  it('赋值 XML: XML → text → XML 语义等价', () => {
    fc.assert(
      fc.property(assignmentXmlArb, ({ xml, libs }) => {
        // XML → text
        const printed = printAssignment(xml);
        expect(printed.hasError).toBe(false);
        // text → XML
        const parsedXml = parseAssignment(printed.text, libs);
        // 重新包装为 <then> 以便再次 print
        const reprinted = printAssignment(`<then>${parsedXml}</then>`);
        expect(reprinted.hasError).toBe(false);

        expect(reprinted.text).toBe(printed.text);
      }),
      { numRuns: 200 },
    );
  });
});

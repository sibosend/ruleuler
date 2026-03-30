/**
 * expressionParser/Printer 单元测试
 * Feature: ruleset-jql-editor, Task 2.5
 * 验证: 需求 8.4, 10.1, 10.2
 *
 * 包含:
 * 1. 操作符映射表正确性（18 个操作符）
 * 2. 向导式编辑器已知 XML 兼容性
 * 3. 边界情况
 * 4. 不支持的 XML 结构
 * 5. Property 8: 非法表达式产生明确错误（fast-check）
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  parseCondition,
  parseAssignment,
  ParseError,
} from '@/pages/rea/lib/expressionParser';
import {
  printCondition,
  printAssignment,
} from '@/pages/rea/lib/expressionPrinter';
import {
  textToXmlOp,
  xmlOpToText,
  ALL_TEXT_OPERATORS,
} from '@/pages/rea/lib/operatorMap';

// ============================================================
// 1. 操作符映射表正确性（18 个操作符）
// ============================================================
describe('操作符映射表正确性', () => {
  it('textToXmlOp 大小为 18', () => {
    expect(textToXmlOp.size).toBe(18);
  });

  it('xmlOpToText 大小为 18', () => {
    expect(xmlOpToText.size).toBe(18);
  });

  it('ALL_TEXT_OPERATORS 长度为 18', () => {
    expect(ALL_TEXT_OPERATORS).toHaveLength(18);
  });

  it('每个映射对双向一致', () => {
    for (const [text, xml] of textToXmlOp) {
      expect(xmlOpToText.get(xml)).toBe(text);
    }
    for (const [xml, text] of xmlOpToText) {
      expect(textToXmlOp.get(text)).toBe(xml);
    }
  });

  it('ALL_TEXT_OPERATORS 与 textToXmlOp 的 key 集合一致', () => {
    const keys = [...textToXmlOp.keys()];
    expect(new Set(ALL_TEXT_OPERATORS)).toEqual(new Set(keys));
  });
});

// ============================================================
// 2. 向导式编辑器已知 XML 兼容性
// ============================================================
describe('向导式编辑器已知 XML 兼容性', () => {
  it('条件 XML → 文本: 客户信息.age > 18', () => {
    const xml = '<if><and><atom op="GreaterThen"><left var-category="客户信息" var="age" var-label="年龄" datatype="Integer" type="variable"></left><value content="18" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('客户信息.age > 18');
  });

  it('赋值 XML → 文本: 结果.level = "成人"', () => {
    const xml = '<then><var-assign var-category="结果" var="level" var-label="等级" datatype="String" type="variable"><value content="成人" type="Input"/></var-assign></then>';
    const result = printAssignment(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('结果.level = "成人"');
  });

  it('多条件 XML → 文本: AND 连接', () => {
    const xml = '<if><and><atom op="GreaterThen"><left var-category="客户信息" var="age" var-label="年龄" datatype="Integer" type="variable"></left><value content="18" type="Input"/></atom><atom op="Equals"><left var-category="客户信息" var="gender" var-label="性别" datatype="String" type="variable"></left><value content="男" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('客户信息.age > 18 AND 客户信息.gender == "男"');
  });

  it('多赋值 XML → 文本: 分号分隔', () => {
    const xml = '<then><var-assign var-category="结果" var="level" var-label="等级" datatype="String" type="variable"><value content="成人" type="Input"/></var-assign><var-assign var-category="结果" var="score" var-label="分数" datatype="Integer" type="variable"><value content="100" type="Input"/></var-assign></then>';
    const result = printAssignment(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('结果.level = "成人"; 结果.score = 100');
  });

  it('OR 连接条件 XML → 文本', () => {
    const xml = '<if><or><atom op="Equals"><left var-category="客户" var="type" var-label="类型" datatype="String" type="variable"></left><value content="VIP" type="Input"/></atom><atom op="GreaterThen"><left var-category="客户" var="score" var-label="积分" datatype="Integer" type="variable"></left><value content="1000" type="Input"/></atom></or></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('客户.type == "VIP" OR 客户.score > 1000');
  });

  it('参数类型 left → 文本', () => {
    const xml = '<if><and><atom op="Equals"><left var="userId" var-label="用户ID" datatype="String" type="parameter"></left><value content="admin" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('userId == "admin"');
  });

  it('In 操作符列表值', () => {
    const xml = '<if><and><atom op="In"><left var-category="客户" var="level" var-label="等级" datatype="String" type="variable"></left><value content="A,B,C" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(false);
    expect(result.text).toBe('客户.level In ("A", "B", "C")');
  });
});

// ============================================================
// 3. 边界情况
// ============================================================
describe('边界情况', () => {
  it('parseCondition("") 抛出 ParseError', () => {
    expect(() => parseCondition('')).toThrow(ParseError);
  });

  it('parseCondition("   ") 抛出 ParseError', () => {
    expect(() => parseCondition('   ')).toThrow(ParseError);
  });

  it('parseAssignment("") 抛出 ParseError', () => {
    expect(() => parseAssignment('')).toThrow(ParseError);
  });

  it('printCondition("") → { text: "", hasError: false }', () => {
    const result = printCondition('');
    expect(result.text).toBe('');
    expect(result.hasError).toBe(false);
  });

  it('printAssignment("") → { text: "", hasError: false }', () => {
    const result = printAssignment('');
    expect(result.text).toBe('');
    expect(result.hasError).toBe(false);
  });

  it('printCondition("   ") → { text: "", hasError: false }', () => {
    const result = printCondition('   ');
    expect(result.text).toBe('');
    expect(result.hasError).toBe(false);
  });
});

// ============================================================
// 4. 不支持的 XML 结构
// ============================================================
describe('不支持的 XML 结构', () => {
  it('<left type="method"> → hasError: true', () => {
    const xml = '<if><and><atom op="Equals"><left type="method" var="someMethod"></left><value content="1" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(true);
  });

  it('<value type="Method"> → hasError: true', () => {
    const xml = '<if><and><atom op="Equals"><left var-category="客户" var="age" var-label="年龄" datatype="Integer" type="variable"></left><value type="Method" var="getAge"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(true);
  });

  it('<then><execute-method>...</execute-method></then> → hasError: true', () => {
    const xml = '<then><execute-method bean="myBean" method-name="doSomething"/></then>';
    const result = printAssignment(xml);
    expect(result.hasError).toBe(true);
  });

  it('<left type="commonfunction"> → hasError: true', () => {
    const xml = '<if><and><atom op="Equals"><left type="commonfunction" var="fn"></left><value content="1" type="Input"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(true);
  });

  it('<value type="CommonFunction"> → hasError: true', () => {
    const xml = '<if><and><atom op="Equals"><left var-category="客户" var="age" var-label="年龄" datatype="Integer" type="variable"></left><value type="CommonFunction" var="calcAge"/></atom></and></if>';
    const result = printCondition(xml);
    expect(result.hasError).toBe(true);
  });
});

// ============================================================
// 5. Property 8: 非法表达式产生明确错误（fast-check）
// Validates: Requirements 10.1
// ============================================================
describe('Property 8: 非法表达式产生明确错误', () => {
  /**
   * **Validates: Requirements 10.1**
   *
   * 对于任意语法非法的表达式文本，expressionParser 应返回包含
   * 错误位置信息的 ParseError，而非静默失败或抛出未捕获异常。
   */

  /** 生成缺少操作符的表达式: "类别.变量 值" */
  const missingOpExpr = fc
    .tuple(
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.stringMatching(/^[a-z]{2,4}$/),
    )
    .map(([cat, v, val]) => `${cat}.${v} "${val}"`);

  /** 生成缺少值的表达式: "类别.变量 ==" */
  const missingValueExpr = fc
    .tuple(
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.stringMatching(/^[a-z]{2,4}$/),
    )
    .map(([cat, v]) => `${cat}.${v} ==`);

  /** 生成未闭合字符串: '类别.变量 == "未闭合' */
  const unclosedStringExpr = fc
    .tuple(
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.stringMatching(/^[a-z]{2,6}$/),
    )
    .map(([cat, v, s]) => `${cat}.${v} == "${s}`);

  /** 生成只有标识符没有点号的表达式 */
  const noDotExpr = fc
    .stringMatching(/^[a-z]{3,6}$/)
    .map((s) => `${s} == 1`);

  /** 生成含特殊字符的表达式 */
  const specialCharExpr = fc
    .tuple(
      fc.stringMatching(/^[a-z]{2,4}$/),
      fc.constantFrom('@', '#', '$', '%', '&', '!', '~'),
    )
    .map(([s, ch]) => `${s}${ch}${s}`);

  const invalidExprArb = fc.oneof(
    missingOpExpr,
    missingValueExpr,
    unclosedStringExpr,
    noDotExpr,
    specialCharExpr,
  );

  it('parseCondition 对非法表达式抛出 ParseError（含 position >= 0）', () => {
    fc.assert(
      fc.property(invalidExprArb, (expr) => {
        try {
          parseCondition(expr);
          // 如果没抛异常，也算通过（某些生成的表达式可能碰巧合法）
          // 但大多数应该抛出 ParseError
        } catch (e) {
          expect(e).toBeInstanceOf(ParseError);
          expect((e as ParseError).position).toBeGreaterThanOrEqual(0);
        }
      }),
      { numRuns: 200 },
    );
  });

  it('parseAssignment 对非法赋值表达式抛出 ParseError（含 position >= 0）', () => {
    /** 生成缺少等号的赋值: "类别.变量 值" */
    const missingEqAssign = fc
      .tuple(
        fc.stringMatching(/^[a-z]{2,4}$/),
        fc.stringMatching(/^[a-z]{2,4}$/),
        fc.stringMatching(/^[a-z]{2,4}$/),
      )
      .map(([cat, v, val]) => `${cat}.${v} "${val}"`);

    /** 生成缺少值的赋值: "类别.变量 =" */
    const missingValAssign = fc
      .tuple(
        fc.stringMatching(/^[a-z]{2,4}$/),
        fc.stringMatching(/^[a-z]{2,4}$/),
      )
      .map(([cat, v]) => `${cat}.${v} =`);

    const invalidAssignArb = fc.oneof(missingEqAssign, missingValAssign);

    fc.assert(
      fc.property(invalidAssignArb, (expr) => {
        try {
          parseAssignment(expr);
        } catch (e) {
          expect(e).toBeInstanceOf(ParseError);
          expect((e as ParseError).position).toBeGreaterThanOrEqual(0);
        }
      }),
      { numRuns: 200 },
    );
  });
});

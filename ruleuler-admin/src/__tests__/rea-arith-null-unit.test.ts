/**
 * ISNULL 谓词 + 四则：parse / printer / 灰度闸
 */
import { describe, it, expect } from 'vitest';
import {
  parseCondition,
  parseAssignment,
  ParseError,
  type LibraryData,
} from '@/pages/rea/lib/expressionParser';
import { printCondition, printAssignment } from '@/pages/rea/lib/expressionPrinter';
import { FLAT_FUNC_NAMES } from '@/pages/rea/lib/functionMap';
import { RESERVED_PREDICATE } from '@/pages/rea/lib/reservedWords';
import { buildCompletions } from '@/pages/rea/lib/cmAutocomplete';

function normalizeXml(xml: string): string {
  return xml.replace(/>\s+</g, '><').replace(/\s+/g, ' ').trim();
}

function roundTripCondition(text: string): string {
  const xml = parseCondition(text, libs);
  const printed = printCondition(`<if>${xml}</if>`);
  expect(printed.hasError).toBe(false);
  expect(printed.text).toBe(text);
  expect(normalizeXml(parseCondition(printed.text, libs))).toBe(normalizeXml(xml));
  return xml;
}

function roundTripAssign(text: string): string {
  const xml = parseAssignment(text, libs);
  const printed = printAssignment(`<then>${xml}</then>`);
  expect(printed.hasError).toBe(false);
  expect(printed.text).toBe(text);
  expect(normalizeXml(parseAssignment(printed.text, libs))).toBe(normalizeXml(xml));
  return xml;
}

const libs: LibraryData = {
  variables: [
    {
      name: 'FlightInfo',
      variables: [
        { name: 'gate', label: '登机口', type: 'String' },
        { name: 'name', label: '名称', type: 'String' },
        { name: 'score', label: '分数', type: 'Integer' },
        { name: 'amount', label: '金额', type: 'Double' },
        { name: 'bonus', label: '奖励', type: 'Integer' },
      ],
    },
  ],
  parameters: [
    { name: 'can_score', label: '分数出参', type: 'Integer' },
    { name: 'threshold', label: '阈值', type: 'Integer' },
    { name: 'flag', label: '标记', type: 'Boolean' },
  ],
};

describe('通道：ISNULL 不进 functionMap', () => {
  it('PAIRS 没有 ISNULL / ISNOTNULL', () => {
    expect(FLAT_FUNC_NAMES).not.toContain('ISNULL');
    expect(FLAT_FUNC_NAMES).not.toContain('ISNOTNULL');
    expect([...RESERVED_PREDICATE]).toEqual(['ISNULL', 'ISNOTNULL']);
  });
});

describe('空值谓词', () => {
  it('ISNULL(FlightInfo.gate)', () => {
    const xml = roundTripCondition('ISNULL(FlightInfo.gate)');
    expect(xml).toContain('op="Null"');
    expect(xml).toContain('var="gate"');
    expect(xml).not.toContain('<value');
  });

  it('ISNOTNULL(FlightInfo.gate)', () => {
    const xml = roundTripCondition('ISNOTNULL(FlightInfo.gate)');
    expect(xml).toContain('op="NotNull"');
  });

  it('ISNULL AND 比较', () => {
    roundTripCondition('ISNULL(FlightInfo.gate) AND FlightInfo.score > 0');
  });

  it('ISNULL(TRIM(...))', () => {
    const xml = roundTripCondition('ISNULL(TRIM(FlightInfo.name))');
    expect(xml).toContain('type="method"');
    expect(xml).toContain('method-name="trim"');
    expect(xml).toContain('op="Null"');
  });

  it('灰度 allowFunctions:false 仍能 parse ISNULL', () => {
    const xml = parseCondition('ISNULL(FlightInfo.gate)', libs, { allowFunctions: false });
    expect(xml).toContain('op="Null"');
  });

  it('灰度 TS parser 认后缀 NULL / Null', () => {
    const a = parseCondition('FlightInfo.gate NULL', libs, { allowFunctions: false });
    const b = parseCondition('FlightInfo.gate Null', libs, { allowFunctions: false });
    expect(a).toContain('op="Null"');
    expect(b).toContain('op="Null"');
  });

  it('编辑器拒绝后缀', () => {
    expect(() => parseCondition('FlightInfo.gate NULL', libs)).toThrow(/请使用 ISNULL/);
  });

  it('ISNULL 本身就是判断', () => {
    expect(() => parseCondition('ISNULL(FlightInfo.gate) == TRUE', libs)).toThrow(/本身就是判断/);
  });

  it('ISNULL 是谓词，不能赋值', () => {
    expect(() => parseAssignment('flag = ISNULL(FlightInfo.gate)', libs)).toThrow(/是谓词，不能赋值/);
  });

  it('ISNULL(1) 非法', () => {
    expect(() => parseCondition('ISNULL(1)', libs)).toThrow(/实参必须是变量、参数或函数/);
  });

  it('ISNULL(score + 10) 非法', () => {
    expect(() => parseCondition('ISNULL(FlightInfo.score + 10)', libs)).toThrow(/实参不能是运算/);
  });
});

describe('IN 列表回归', () => {
  it('IN 列表', () => {
    roundTripCondition('FlightInfo.gate IN ("A1", "A2")');
  });

  it('NOTIN 列表', () => {
    roundTripCondition('FlightInfo.gate NOTIN ("A1", "A2")');
  });

  it('逻辑括号仍活', () => {
    const xml = parseCondition('(FlightInfo.score > 10 OR FlightInfo.bonus > 0)', libs);
    expect(xml).toContain('<or>');
    roundTripCondition('FlightInfo.score > 10 AND (FlightInfo.bonus > 0 OR FlightInfo.amount > 1)');
  });
});

describe('比较左 simple-arith 平链', () => {
  it('score + 10 > 80：先算再比，XML 是 left+arith', () => {
    const xml = roundTripCondition('FlightInfo.score + 10 > 80');
    expect(xml).toContain('op="GreaterThen"');
    expect(xml).toContain('var="score"');
    expect(xml).toContain('type="Add"');
    expect(xml).toContain('value="10"');
    expect(xml).toContain('content="80"');
    expect(xml).not.toMatch(/op="GreaterThen"[^>]*>[\s\S]*content="10"/);
  });

  it('左 simple-arith 平链', () => {
    roundTripCondition('FlightInfo.amount * 1.1 >= threshold');
    roundTripCondition('FlightInfo.score - 10 > 0');
    roundTripCondition('FlightInfo.score / 2 > 10');
    roundTripCondition('FlightInfo.score % 10 == 0');
  });

  it('左双引用的合法替代', () => {
    const xml = roundTripCondition('80 < FlightInfo.score + FlightInfo.bonus');
    expect(xml).toContain('op="LessThen"');
    expect(xml).toContain('type="Add"');
    expect(xml).toContain('var="score"');
    expect(xml).toContain('var="bonus"');
  });

  it('四则内部优先级：平链 Add(10)→Mul(2)，不收成 Add(20)', () => {
    const xml = roundTripCondition('FlightInfo.score + 10 * 2 > 0');
    expect(xml).toContain('type="Add"');
    expect(xml).toContain('value="10"');
    expect(xml).toContain('type="Mul"');
    expect(xml).toContain('value="2"');
    expect(xml).not.toContain('value="20"');
  });

  it('哨兵默认值', () => {
    roundTripCondition('FlightInfo.score == -999');
    roundTripCondition('FlightInfo.score > -10');
    roundTripCondition('ISNULL(FlightInfo.score) OR FlightInfo.score == -999');
  });
});

describe('动作 / 函数实参 complex-arith', () => {
  it('赋值右', () => {
    roundTripAssign('can_score = FlightInfo.score + 10');
    roundTripAssign('can_score = FlightInfo.amount * 1.1');
    roundTripAssign('can_score = ABS(FlightInfo.score) + 10');
    roundTripAssign('can_score = FlightInfo.score * 1.1 + 10');
  });

  it('函数实参', () => {
    const xml = roundTripCondition('ABS(FlightInfo.score + 10) > 5');
    expect(xml).toContain('method-name="abs"');
    expect(xml).toContain('type="Add"');
  });

  it('操作数括号（括号在 op 右边）', () => {
    const a = roundTripAssign('can_score = 1.1 * (FlightInfo.score + 10)');
    expect(a).toContain('<paren>');
    roundTripAssign('can_score = 1.1 * (FlightInfo.score + FlightInfo.bonus)');
    roundTripCondition('80 < 1.1 * (FlightInfo.score + 10)');
    roundTripAssign('can_score = FlightInfo.score + (FlightInfo.bonus + 10) * 1.1');
  });
});

describe('非法', () => {
  it('左边两个引用相加', () => {
    expect(() => parseCondition('FlightInfo.score + FlightInfo.bonus > 80', libs)).toThrow(/左边不能是两个引用相加/);
  });

  it('两边都是复合运算', () => {
    expect(() => parseCondition('FlightInfo.score + threshold > FlightInfo.bonus + 1', libs)).toThrow(/左边不能是两个引用相加/);
  });

  it('括号头', () => {
    expect(() => parseCondition('(FlightInfo.score + 10) * 2 > 80', libs)).toThrow(/值不能以括号开头/);
    expect(() => parseAssignment('(FlightInfo.score + 10) * 1.1', libs)).toThrow(/期望/);
    expect(() => parseAssignment('can_score = (FlightInfo.score + 10) * 1.1', libs)).toThrow(/值不能以括号开头/);
    expect(() => parseCondition('80 < (FlightInfo.score + 10) * 1.1', libs)).toThrow(/值不能以括号开头/);
  });

  it('冗余括号', () => {
    expect(() => parseCondition('(FlightInfo.score + 10) > 80', libs)).toThrow(/请去掉括号/);
    expect(() => parseAssignment('can_score = (FlightInfo.score + 10)', libs)).toThrow(/请去掉括号/);
  });

  it('条件里没有比较', () => {
    expect(() => parseCondition('FlightInfo.score + 10', libs)).toThrow(/条件里没有比较/);
  });

  it('二元运算符两侧都要空格', () => {
    expect(() => parseCondition('FlightInfo.score-10 > 0', libs)).toThrow(/期望操作符/);
    expect(() => parseCondition('FlightInfo.score -10 > 0', libs)).toThrow(/期望操作符/);
  });

  it('灰度仍禁普通函数', () => {
    expect(() => parseCondition('ABS(FlightInfo.score) + 1 > 0', libs, { allowFunctions: false })).toThrow(/灰度条件不支持函数/);
  });
});

describe('发布闸 / 补全', () => {
  it('ReleaseListPage 同款 parse：ISNULL / 四则 / IN', () => {
    const opt = { allowFunctions: false };
    expect(() => parseCondition('ISNULL(FlightInfo.gate)', libs, opt)).not.toThrow();
    expect(() => parseCondition('FlightInfo.score + 10 > 80', libs, opt)).not.toThrow();
    expect(() => parseCondition('FlightInfo.gate IN ("A1", "A2")', libs, opt)).not.toThrow();
  });

  it('条件区补全出 ISNULL()，不出后缀 NULL', () => {
    const labels = buildCompletions('condition', libs, { allowFunctions: false }).map((c) => c.label);
    expect(labels).toContain('ISNULL');
    expect(labels).toContain('ISNOTNULL');
    expect(labels).not.toContain('NULL');
    expect(labels).not.toContain('TRIM');
  });
});

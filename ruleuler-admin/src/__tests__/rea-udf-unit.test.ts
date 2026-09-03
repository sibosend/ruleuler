/**
 * REA UDF：函数映射、大小写、round-trip
 */
import { describe, it, expect } from 'vitest';
import {
  parseCondition,
  parseAssignment,
  ParseError,
  extractActionBeans,
  type LibraryData,
} from '@/pages/rea/lib/expressionParser';
import { printCondition, printAssignment } from '@/pages/rea/lib/expressionPrinter';
import { lookupByRea, lookupByMethod, allFuncs } from '@/pages/rea/lib/functionMap';
import { buildCompletions } from '@/pages/rea/lib/cmAutocomplete';

function normalizeXml(xml: string): string {
  return xml.replace(/>\s+</g, '><').replace(/\s+/g, ' ').trim();
}

const libs: LibraryData = {
  variables: [
    {
      name: 'FlightInfo',
      variables: [
        { name: 'name', label: '名称', type: 'String' },
        { name: 'score', label: '分数', type: 'Integer' },
        { name: 'tags', label: '标签', type: 'List' },
        { name: 'extra', label: '扩展', type: 'Map' },
        { name: 'WEEK', label: '周字段', type: 'Integer' },
        { name: 'is_international', label: '国际', type: 'Boolean' },
      ],
    },
    {
      name: 'Order',
      variables: [{ name: 'items', label: '明细', type: 'List' }],
    },
  ],
  parameters: [
    { name: 'can_score', label: '分数出参', type: 'Integer' },
    { name: 'risk_level', label: '风险', type: 'String' },
  ],
  actions: [
    {
      id: 'riskService',
      name: 'RiskService',
      methods: [
        {
          name: '评分',
          methodName: 'score',
          parameters: [
            { name: '航司', type: 'String' },
            { name: '人数', type: 'Integer' },
          ],
        },
      ],
    },
  ],
};

describe('配对表双向', () => {
  it('每个 method 函数 bean+method 能反查回同一 REA 名', () => {
    for (const fn of allFuncs()) {
      if (fn.engine !== 'method') continue;
      expect(lookupByMethod(fn.bean!, fn.method!)?.rea).toBe(fn.rea);
      expect(lookupByRea(fn.rea)).toBe(fn);
    }
  });
});

describe('TRUE / true', () => {
  it('TRUE 合法', () => {
    const xml = parseCondition('FlightInfo.is_international == TRUE', libs);
    expect(xml).toContain('content="true"');
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toContain('TRUE');
  });

  it('true 报错', () => {
    expect(() => parseCondition('FlightInfo.is_international == true', libs)).toThrow(ParseError);
    expect(() => parseCondition('FlightInfo.is_international == true', libs)).toThrow(/TRUE/);
  });
});

describe('值函数 round-trip', () => {
  it('TRIM 条件左值', () => {
    const xml = parseCondition('TRIM(FlightInfo.name) == ""', libs);
    expect(xml).toContain('type="method"');
    expect(xml).toContain('bean-name="urule.stringAction"');
    expect(xml).toContain('method-name="trim"');
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('TRIM(FlightInfo.name) == ""');
    expect(normalizeXml(parseCondition(printed.text, libs))).toBe(normalizeXml(xml));
  });

  it('ABS 赋值右值 + 嵌套', () => {
    const xml = parseAssignment('can_score = ABS(MIN(FlightInfo.score, 100))', libs);
    expect(xml).toContain('type="Method"');
    expect(xml).toContain('method-name="abs"');
    expect(xml).toContain('method-name="min"');
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('can_score = ABS(MIN(FlightInfo.score, 100))');
  });

  it('STRING.TRIM 等价 TRIM', () => {
    const a = parseCondition('TRIM(FlightInfo.name) == ""', libs);
    const b = parseCondition('STRING.TRIM(FlightInfo.name) == ""', libs);
    expect(normalizeXml(a)).toBe(normalizeXml(b));
  });

  it('LISTEMPTY 隐式 == TRUE', () => {
    const xml = parseCondition('LISTEMPTY(FlightInfo.tags)', libs);
    expect(xml).toContain('op="Equals"');
    expect(xml).toContain('content="true"');
    expect(xml).toContain('method-name="isEmpty"');
  });

  it('SIN 落到 method in', () => {
    const xml = parseCondition('SIN(FlightInfo.score) > 0', libs);
    expect(xml).toContain('method-name="in"');
    expect(xml).not.toContain('method-name="sin"');
  });
});

describe('动作函数', () => {
  it('LISTADD → execute-method bean=', () => {
    const xml = parseAssignment('LISTADD(FlightInfo.tags, "VIP")', libs);
    expect(xml).toContain('<execute-method');
    expect(xml).toContain('bean="urule.listAction"');
    expect(xml).not.toContain('bean-name=');
    expect(xml).toContain('method-name="add"');
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('LISTADD(FlightInfo.tags, "VIP")');
  });

  it('LISTADD 进条件报错', () => {
    expect(() => parseCondition('LISTADD(FlightInfo.tags, "VIP") == TRUE', libs)).toThrow(/没有返回值/);
  });

  it('值函数当动作报错', () => {
    expect(() => parseAssignment('TRIM(FlightInfo.name)', libs)).toThrow(/值函数/);
  });

  it('listAdd 报错', () => {
    expect(() => parseAssignment('listAdd(FlightInfo.tags, "VIP")', libs)).toThrow(/LISTADD/);
  });
});

describe('聚合', () => {
  it('SUM 第二参是 property-name', () => {
    const xml = parseCondition('SUM(Order.items, amount) > 1000', libs);
    expect(xml).toContain('type="commonfunction"');
    expect(xml).toContain('function-name="Sum"');
    expect(xml).toContain('property-name="amount"');
    expect(xml).not.toContain('type="Parameter"');
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('SUM(Order.items, amount) > 1000');
  });

  it('COUNT 无第二参', () => {
    const xml = parseCondition('COUNT(Order.items) > 0', libs);
    expect(xml).toContain('function-name="Count"');
    expect(xml).not.toContain('property-name');
  });
});

describe('保留字', () => {
  it('属性名可以是 WEEK', () => {
    const xml = parseCondition('FlightInfo.WEEK > 1', libs);
    expect(xml).toContain('var="WEEK"');
  });

  it('WEEK 不能当类别', () => {
    expect(() => parseCondition('WEEK.foo == 1', libs)).toThrow(/类别名/);
  });

  it('Date 不能当类别', () => {
    expect(() => parseCondition('Date.foo == 1', libs)).toThrow(/类别名/);
  });

  it('灰度拒绝函数', () => {
    expect(() => parseCondition('TRIM(FlightInfo.name) == ""', libs, { allowFunctions: false })).toThrow(/灰度条件不支持函数/);
  });
});

describe('补全', () => {
  it('条件区有 TRIM 无 LISTADD', () => {
    const labels = buildCompletions('condition', libs).map((c) => c.label);
    expect(labels).toContain('TRIM');
    expect(labels).not.toContain('LISTADD');
    expect(labels).toContain('TRUE');
  });

  it('那么区有 LISTADD', () => {
    const labels = buildCompletions('action', libs).map((c) => c.label);
    expect(labels).toContain('LISTADD');
    expect(labels).toContain('TRIM');
  });

  it('灰度补全无函数', () => {
    const labels = buildCompletions('condition', libs, { allowFunctions: false }).map((c) => c.label);
    expect(labels).not.toContain('TRIM');
    expect(labels).toContain('CONTAIN');
  });

  it('自定义方法出现在补全', () => {
    const labels = buildCompletions('condition', libs).map((c) => c.label);
    expect(labels).toContain('RISKSERVICE.SCORE');
  });
});

describe('自定义动作库', () => {
  it('RISKSERVICE.SCORE 编译到原名 bean/method', () => {
    const xml = parseCondition('RISKSERVICE.SCORE(FlightInfo.name, 10) > 0', libs);
    expect(xml).toContain('bean-name="riskService"');
    expect(xml).toContain('method-name="score"');
    expect(xml).toContain('bean-label="RiskService"');
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('RISKSERVICE.SCORE(FlightInfo.name, 10) > 0');
  });

  it('骆驼峰报错要求全大写', () => {
    expect(() => parseCondition('RiskService.score(FlightInfo.name, 10) > 0', libs)).toThrow(/RISKSERVICE\.SCORE/);
  });

  it('参数个数不对直接失败', () => {
    expect(() => parseCondition('RISKSERVICE.SCORE(FlightInfo.name) > 0', libs)).toThrow(/参数/);
  });

  it('未导入的名字失败', () => {
    expect(() => parseCondition('FOO.BAR(1) > 0', libs)).toThrow(/未知函数/);
  });

  it('extractActionBeans 跳过 urule 内置', () => {
    const beans = extractActionBeans([
      {
        springBeans: [
          { id: 'urule.stringAction', name: '字符串', methods: [{ name: '去空格', methodName: 'trim', parameters: [] }] },
          { id: 'riskService', name: 'RiskService', methods: [{ name: '评分', methodName: 'score', parameters: [{ name: '航司', type: 'String' }] }] },
        ],
      },
    ]);
    expect(beans).toHaveLength(1);
    expect(beans[0]!.id).toBe('riskService');
    expect(beans[0]!.methods[0]!.methodName).toBe('score');
  });

  it('赋值右值编到 Method value', () => {
    const xml = parseAssignment('can_score = RISKSERVICE.SCORE(FlightInfo.name, 10)', libs);
    expect(xml).toContain('type="Method"');
    expect(xml).toContain('bean-name="riskService"');
    expect(xml).toContain('method-name="score"');
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('can_score = RISKSERVICE.SCORE(FlightInfo.name, 10)');
  });

  it('那么里裸调用编到 execute-method bean 不是 bean-name', () => {
    const xml = parseAssignment('RISKSERVICE.SCORE(FlightInfo.name, 10)', libs);
    expect(xml).toContain('<execute-method');
    expect(xml).toContain('bean="riskService"');
    expect(xml).not.toContain('bean-name=');
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('RISKSERVICE.SCORE(FlightInfo.name, 10)');
  });

  it('灰度禁用自定义函数', () => {
    expect(() => parseCondition('RISKSERVICE.SCORE(FlightInfo.name, 10) > 0', libs, { allowFunctions: false })).toThrow(/灰度条件不支持函数/);
  });
});

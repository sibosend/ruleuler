/**
 * rea-function-matrix.test.ts — 全函数编译矩阵。
 *
 * 遍历 functionMap 的 allFuncs()，一个不漏。断言全部从配对表自身派生：
 * bean/method/parameter type 顺序、位置规则、print round-trip。
 * 执行层断言在 ruleuler-client 的 ReaFunctionExecutionTest（读 rea-function-map.json）。
 */
import { describe, it, expect } from 'vitest';
import { parseCondition, parseAction, ParseError } from '@/pages/rea/lib/expressionParser';
import { printCondition, printAssignment } from '@/pages/rea/lib/expressionPrinter';
import { allFuncs, type BuiltinFunc, type FuncParam } from '@/pages/rea/lib/functionMap';
import { EXEC_LIBS } from '@/pages/rea/lib/functionExecSpec';

function normalizeXml(xml: string): string {
  return xml.replace(/>\s+</g, '><').replace(/\s+/g, ' ').trim();
}

/** 参数类型 → 通用实参 REA 文本 */
function argFor(p: FuncParam): string {
  switch (p.type) {
    case 'Integer':
      return '1';
    case 'Date':
      return 'FlightInfo.departure';
    case 'List':
      return 'FlightInfo.tags';
    case 'Map':
      return 'FlightInfo.extra';
    default: // String / Object
      return '"abc"';
  }
}

/** 返回类型 → 赋值目标变量 */
function targetFor(fn: BuiltinFunc): string {
  switch (fn.returnType) {
    case 'Number':
      return 'FlightInfo.score';
    case 'Date':
      return 'FlightInfo.departure';
    case 'List':
      return 'FlightInfo.tags';
    case 'Map':
      return 'FlightInfo.extra';
    default:
      return 'FlightInfo.name';
  }
}

function callText(fn: BuiltinFunc): string {
  const args = (fn.params ?? []).map(argFor);
  return `${fn.rea}(${args.join(', ')})`;
}

function expectParamTypes(fn: BuiltinFunc, xml: string): void {
  for (const p of fn.params ?? []) {
    expect(xml).toContain(`<parameter name="${p.name}" type="${p.type}">`);
  }
}

describe.each(allFuncs().map((fn) => [fn.rea, fn] as const))('%s', (_rea, fn) => {
  const call = callText(fn);

  if (fn.kind === 'action') {
    it('动作位 → execute-method bean=（不是 bean-name）', () => {
      const xml = parseAction(call, EXEC_LIBS);
      expect(xml).toContain(`<execute-method bean="${fn.bean}"`);
      expect(xml).toContain(`method-name="${fn.method}"`);
      expect(xml).not.toContain('bean-name=');
      expectParamTypes(fn, xml);
      const printed = printAssignment(`<then>${xml}</then>`);
      expect(printed.hasError).toBe(false);
      expect(printed.text).toBe(call);
    });

    it('进条件 / 赋值右值报错', () => {
      expect(() => parseCondition(`${call} == TRUE`, EXEC_LIBS)).toThrow(/没有返回值/);
      expect(() => parseAction(`FlightInfo.name = ${call}`, EXEC_LIBS)).toThrow(/没有返回值/);
    });
    return;
  }

  if (fn.engine === 'commonfunction') {
    it('聚合 → CommonFunction + property-name', () => {
      const args = fn.needProperty ? ['Order.items', 'amount'] : ['Order.items'];
      const text = `${fn.rea}(${args.join(', ')})`;
      const xml = parseCondition(`${text} > 1`, EXEC_LIBS);
      expect(xml).toContain(`function-name="${fn.functionName}"`);
      if (fn.needProperty) {
        expect(xml).toContain('property-name="amount"');
      } else {
        expect(xml).not.toContain('property-name');
      }
      const printed = printCondition(`<if>${xml}</if>`);
      expect(printed.hasError).toBe(false);
      expect(printed.text).toBe(`${text} > 1`);
    });
    return;
  }

  // 值函数（含 both）
  it('条件左值 + method XML 属性', () => {
    const xml = parseCondition(`${call} == 1`, EXEC_LIBS);
    expect(xml).toContain(`type="method"`);
    expect(xml).toContain(`bean-name="${fn.bean}"`);
    expect(xml).toContain(`method-name="${fn.method}"`);
    expectParamTypes(fn, xml);
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe(`${call} == 1`);
  });

  it('赋值右值 + round-trip', () => {
    const target = targetFor(fn);
    const xml = parseAction(`${target} = ${call}`, EXEC_LIBS);
    expect(xml).toContain(`type="Method"`);
    expect(xml).toContain(`bean-name="${fn.bean}"`);
    expect(xml).toContain(`method-name="${fn.method}"`);
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe(`${target} = ${call}`);
  });

  if (fn.returnType === 'Boolean') {
    it('Boolean 返回 → 裸 atom 隐式 == TRUE', () => {
      const xml = parseCondition(call, EXEC_LIBS);
      expect(xml).toContain('op="Equals"');
      expect(xml).toContain('content="true"');
      // 裸 atom 是语法糖，print 回显显式形式
      const printed = printCondition(`<if>${xml}</if>`);
      expect(printed.hasError).toBe(false);
      expect(printed.text).toBe(`${call} == TRUE`);
    });
  }

  if (fn.kind === 'both') {
    it('both：动作位也合法', () => {
      const xml = parseAction(call, EXEC_LIBS);
      expect(xml).toContain(`<execute-method bean="${fn.bean}"`);
      expect(xml).not.toContain('bean-name=');
    });
  }

  it('参数个数错误报错', () => {
    const wrong = `${fn.rea}()`;
    if ((fn.params ?? []).length > 0) {
      expect(() => parseCondition(`${wrong} == 1`, EXEC_LIBS)).toThrow(ParseError);
    }
  });
});

describe('命名空间等价（全部 ns 函数）', () => {
  for (const fn of allFuncs()) {
    if (!fn.ns || !fn.nsName) continue;
    // 动作函数只能在动作位比较，其余在条件位
    it(`${fn.ns}.${fn.nsName} ≡ ${fn.rea}`, () => {
      const args = (fn.params ?? []).map(argFor).join(', ');
      let flat: string;
      let nsForm: string;
      if (fn.kind === 'action') {
        flat = parseAction(`${fn.rea}(${args})`, EXEC_LIBS);
        nsForm = parseAction(`${fn.ns}.${fn.nsName}(${args})`, EXEC_LIBS);
      } else {
        flat = parseCondition(`${callText(fn)} == 1`, EXEC_LIBS);
        nsForm = parseCondition(`${fn.ns}.${fn.nsName}(${args}) == 1`, EXEC_LIBS);
      }
      expect(normalizeXml(nsForm)).toBe(normalizeXml(flat));
    });
  }
});

describe('写法补充', () => {
  it('TRIM ( 带空格仍是函数', () => {
    const xml = parseCondition('TRIM (FlightInfo.name) == ""', EXEC_LIBS);
    expect(xml).toContain('method-name="trim"');
  });

  it('嵌套 TRIM(SUBSTRING(...))', () => {
    const xml = parseCondition('TRIM(SUBSTRING(FlightInfo.name, 0, 3)) == ""', EXEC_LIBS);
    expect(xml).toContain('method-name="trim"');
    expect(xml).toContain('method-name="substring"');
    const printed = printCondition(`<if>${xml}</if>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('TRIM(SUBSTRING(FlightInfo.name, 0, 3)) == ""');
  });

  it('混合多语句：动作 + 赋值分号分隔', () => {
    const xml = parseAction('LISTADD(FlightInfo.tags, "VIP"); can_score = ABS(FlightInfo.score)', EXEC_LIBS);
    expect(xml).toContain('<execute-method');
    expect(xml).toContain('method-name="abs"');
    const printed = printAssignment(`<then>${xml}</then>`);
    expect(printed.hasError).toBe(false);
    expect(printed.text).toBe('LISTADD(FlightInfo.tags, "VIP"); can_score = ABS(FlightInfo.score)');
  });

  it('参数引用当函数实参', () => {
    const xml = parseAction('can_score = ABS(can_score)', EXEC_LIBS);
    expect(xml).toContain('type="Parameter"');
    expect(xml).toContain('method-name="abs"');
  });

  it('自定义函数裸 atom（返回类型未知）报错，须显式 == TRUE', () => {
    expect(() => parseCondition('RISKSERVICE.ISHIGH(FlightInfo.name)', EXEC_LIBS)).toThrow(/期望操作符/);
    const xml = parseCondition('RISKSERVICE.ISHIGH(FlightInfo.name) == TRUE', EXEC_LIBS);
    expect(xml).toContain('bean-name="riskService"');
  });
});

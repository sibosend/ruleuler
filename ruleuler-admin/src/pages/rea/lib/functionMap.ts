/**
 * REA 内置函数配对表：一张表，两个索引。
 * REA 名全大写；XML 仍写引擎的 bean / method-name。
 */

export type FuncKind = 'value' | 'action' | 'both';
export type FuncEngine = 'method' | 'commonfunction';
export type ReaReturnType =
  | 'String'
  | 'Number'
  | 'Boolean'
  | 'Date'
  | 'List'
  | 'Map'
  | 'Object'
  | 'Void';
export type FuncNamespace = 'STRING' | 'MATH' | 'DATE' | 'LIST' | 'MAP';

export interface FuncParam {
  name: string;
  type: string;
}

export interface BuiltinFunc {
  rea: string;
  ns?: FuncNamespace;
  /** 命名空间后的短名。LIST.SIZE 的 nsName 是 SIZE，rea 是 LISTSIZE */
  nsName?: string;
  kind: FuncKind;
  engine: FuncEngine;
  returnType: ReaReturnType;
  bean?: string;
  beanLabel?: string;
  method?: string;
  methodLabel?: string;
  params?: FuncParam[];
  functionName?: string;
  functionLabel?: string;
  needProperty?: boolean;
  hint?: string;
}

export const NAMESPACES: readonly FuncNamespace[] = [
  'STRING',
  'MATH',
  'DATE',
  'LIST',
  'MAP',
];

export function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function m(
  rea: string,
  ns: FuncNamespace,
  kind: FuncKind,
  returnType: ReaReturnType,
  bean: string,
  beanLabel: string,
  method: string,
  methodLabel: string,
  params: FuncParam[],
  extra?: { nsName?: string; hint?: string },
): BuiltinFunc {
  return {
    rea,
    ns,
    nsName: extra?.nsName ?? rea,
    kind,
    engine: 'method',
    returnType,
    bean,
    beanLabel,
    method,
    methodLabel,
    params,
    hint: extra?.hint,
  };
}

function agg(
  rea: string,
  functionName: string,
  functionLabel: string,
  needProperty: boolean,
): BuiltinFunc {
  return {
    rea,
    kind: 'value',
    engine: 'commonfunction',
    returnType: 'Object',
    functionName,
    functionLabel,
    needProperty,
  };
}

const S = 'urule.stringAction';
const MATH = 'urule.mathAction';
const DATE = 'urule.dateAction';
const LIST = 'urule.listAction';
const MAP = 'urule.mapAction';

const PAIRS: BuiltinFunc[] = [
  // 6.1 字符串
  m('TRIM', 'STRING', 'value', 'String', S, '字符串', 'trim', '去空格', [{ name: '目标字符串', type: 'String' }]),
  m('SUBSTRING', 'STRING', 'value', 'String', S, '字符串', 'substring', '指定起始的字符串截取', [
    { name: '目标字符串', type: 'String' },
    { name: '开始位置', type: 'Integer' },
    { name: '结束位置', type: 'Integer' },
  ]),
  m('SUBSTRINGFROM', 'STRING', 'value', 'String', S, '字符串', 'substringForStart', '指定开始的字符串截取', [
    { name: '目标字符串', type: 'String' },
    { name: '开始位置', type: 'Integer' },
  ]),
  m('SUBSTRINGTO', 'STRING', 'value', 'String', S, '字符串', 'substringForEnd', '指定结束的字符串截取', [
    { name: '目标字符串', type: 'String' },
    { name: '结束位置', type: 'Integer' },
  ]),
  m('LOWER', 'STRING', 'value', 'String', S, '字符串', 'toLowerCase', '转小写', [{ name: '目标字符串', type: 'String' }]),
  m('UPPER', 'STRING', 'value', 'String', S, '字符串', 'toUpperCase', '转大写', [{ name: '目标字符串', type: 'String' }]),
  m('LENGTH', 'STRING', 'value', 'Number', S, '字符串', 'length', '获取长度', [{ name: '目标字符串', type: 'String' }]),
  m('CHARAT', 'STRING', 'value', 'String', S, '字符串', 'charAt', '获取字符', [
    { name: '目标字符串', type: 'String' },
    { name: '位置', type: 'Integer' },
  ]),
  m('INDEXOF', 'STRING', 'value', 'Number', S, '字符串', 'indexOf', '字符首次出现位置', [
    { name: '目标字符串', type: 'String' },
    { name: '要查找的字符串', type: 'String' },
  ]),
  m('LASTINDEXOF', 'STRING', 'value', 'Number', S, '字符串', 'lastIndexOf', '字符最后出现位置', [
    { name: '目标字符串', type: 'String' },
    { name: '要查找的字符串', type: 'String' },
  ]),
  m('REPLACE', 'STRING', 'value', 'String', S, '字符串', 'replace', '替换字符串', [
    { name: '目标字符串', type: 'String' },
    { name: '原字符串', type: 'String' },
    { name: '新字符串', type: 'String' },
  ]),
  m('SPLIT', 'STRING', 'value', 'List', S, '字符串', 'split', '拆分字符串为集合', [
    { name: '目标字符串', type: 'String' },
    { name: '原字符串', type: 'String' },
  ]),

  // 6.2 数学
  m('ABS', 'MATH', 'value', 'Number', MATH, '数学函数', 'abs', '求绝对值', [{ name: '数字', type: 'Object' }]),
  m('MAX', 'MATH', 'value', 'Number', MATH, '数学函数', 'max', '求最大值', [
    { name: '数字1', type: 'Object' },
    { name: '数字2', type: 'Object' },
  ]),
  m('MIN', 'MATH', 'value', 'Number', MATH, '数学函数', 'min', '求最小值', [
    { name: '数字1', type: 'Object' },
    { name: '数字2', type: 'Object' },
  ]),
  m('SIN', 'MATH', 'value', 'Number', MATH, '数学函数', 'in', '求正弦', [{ name: '数字', type: 'Object' }]),
  m('COS', 'MATH', 'value', 'Number', MATH, '数学函数', 'cos', '求余弦', [{ name: '数字', type: 'Object' }]),
  m('TAN', 'MATH', 'value', 'Number', MATH, '数学函数', 'tan', '求正切', [{ name: '数字', type: 'Object' }]),
  m('COT', 'MATH', 'value', 'Number', MATH, '数学函数', 'cot', '求余切', [{ name: '数字', type: 'Object' }]),
  m('LN', 'MATH', 'value', 'Number', MATH, '数学函数', 'log', '求e为底的对数', [{ name: '数字', type: 'Object' }]),
  m('LOG10', 'MATH', 'value', 'Number', MATH, '数学函数', 'log10', '求10为底的对数', [{ name: '数字', type: 'Object' }]),
  m('ROUND', 'MATH', 'value', 'Number', MATH, '数学函数', 'round', '四舍五入', [{ name: '数字', type: 'Object' }]),

  // 6.3 日期
  m('NOW', 'DATE', 'value', 'Date', DATE, '日期', 'getDate', '当前日期', []),
  m('PARSEDATE', 'DATE', 'value', 'Date', DATE, '日期', 'formatString', '解析字符串为日期', [
    { name: '日期字符串', type: 'String' },
    { name: '格式', type: 'String' },
  ]),
  m('FORMATDATE', 'DATE', 'value', 'String', DATE, '日期', 'format', '格式化日期', [
    { name: '目标日期', type: 'Date' },
    { name: '格式', type: 'String' },
  ]),
  m('ADDYEARS', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForYear', '日期加年', [
    { name: '目标日期', type: 'Date' },
    { name: '年数', type: 'Integer' },
  ]),
  m('ADDMONTHS', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForMonth', '日期加月', [
    { name: '目标日期', type: 'Date' },
    { name: '月数', type: 'Integer' },
  ]),
  m('ADDDAYS', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForDay', '日期加天', [
    { name: '目标日期', type: 'Date' },
    { name: '天数', type: 'Integer' },
  ]),
  m('ADDHOURS', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForHour', '日期加小时', [
    { name: '目标日期', type: 'Date' },
    { name: '小时数', type: 'Integer' },
  ]),
  m('ADDMINUTES', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForMinute', '日期加分钟', [
    { name: '目标日期', type: 'Date' },
    { name: '分钟数', type: 'Integer' },
  ]),
  m('ADDSECONDS', 'DATE', 'value', 'Date', DATE, '日期', 'addDateForSecond', '日期加秒', [
    { name: '目标日期', type: 'Date' },
    { name: '秒数', type: 'Integer' },
  ]),
  m('SUBYEARS', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForYear', '减日期减年', [
    { name: '目标日期', type: 'Date' },
    { name: '年数', type: 'Integer' },
  ]),
  m('SUBMONTHS', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForMonth', '减日期减月', [
    { name: '目标日期', type: 'Date' },
    { name: '月数', type: 'Integer' },
  ]),
  m('SUBDAYS', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForDay', '减日期减天', [
    { name: '目标日期', type: 'Date' },
    { name: '天数', type: 'Integer' },
  ]),
  m('SUBHOURS', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForHour', '减日期减小时', [
    { name: '目标日期', type: 'Date' },
    { name: '小时', type: 'Integer' },
  ]),
  m('SUBMINUTES', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForMinute', '减日期减分钟', [
    { name: '目标日期', type: 'Date' },
    { name: '分钟', type: 'Integer' },
  ]),
  m('SUBSECONDS', 'DATE', 'value', 'Date', DATE, '日期', 'subDateForSecond', '减日期减秒', [
    { name: '目标日期', type: 'Date' },
    { name: '秒数', type: 'Integer' },
  ]),
  m('YEAR', 'DATE', 'value', 'Number', DATE, '日期', 'getYear', '取年份', [{ name: '目标日期', type: 'Date' }]),
  m('MONTH', 'DATE', 'value', 'Number', DATE, '日期', 'getMonth', '取月份', [{ name: '目标日期', type: 'Date' }], {
    hint: '返回 0-11，0=一月。MONTH(NOW()) == 9 是十月不是九月',
  }),
  m('WEEK', 'DATE', 'value', 'Number', DATE, '日期', 'getWeek', '取星期', [{ name: '目标日期', type: 'Date' }], {
    hint: 'DAY_OF_WEEK：1=周日 … 7=周六，不是 ISO 周一=1',
  }),
  m('DAY', 'DATE', 'value', 'Number', DATE, '日期', 'getay', '取天', [{ name: '目标日期', type: 'Date' }], {
    hint: '引擎方法名是 getay（历史拼写错误）',
  }),
  m('HOUR', 'DATE', 'value', 'Number', DATE, '日期', 'getHour', '取小时', [{ name: '目标日期', type: 'Date' }]),
  m('MINUTE', 'DATE', 'value', 'Number', DATE, '日期', 'getMinute', '取分钟', [{ name: '目标日期', type: 'Date' }]),
  m('SECOND', 'DATE', 'value', 'Number', DATE, '日期', 'getSecond', '取秒', [{ name: '目标日期', type: 'Date' }]),
  m('DIFFMILLIS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifMillSecond', '日期相减返回毫秒', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFSECONDS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifSecond', '日期相减返回秒', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFMINUTES', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifMinute', '日期相减返回分钟', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFHOURS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifHour', '日期相减返回小时', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFDAYS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifDay', '日期相减返回天', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFWEEKS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifWeek', '日期相减返回星期', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),
  m('DIFFMONTHS', 'DATE', 'value', 'Number', DATE, '日期', 'dateDifMonth', '日期相减返回月', [
    { name: '日期', type: 'Date' },
    { name: '减去的日期', type: 'Date' },
  ]),

  // 6.4 List / Map 值函数
  m('LISTSIZE', 'LIST', 'value', 'Number', LIST, 'List集合', 'size', '求List大小', [{ name: '集合对象', type: 'List' }], {
    nsName: 'SIZE',
  }),
  m('LISTMAX', 'LIST', 'value', 'Number', LIST, 'List集合', 'max', '求List中所有的数字最大值', [
    { name: '包含所有数字的集合对象', type: 'List' },
  ], { nsName: 'MAX' }),
  m('LISTMIN', 'LIST', 'value', 'Number', LIST, 'List集合', 'min', '求List中所有的数字最小值', [
    { name: '包含所有数字的集合对象', type: 'List' },
  ], { nsName: 'MIN' }),
  m('LISTCONTAINS', 'LIST', 'value', 'Boolean', LIST, 'List集合', 'contains', '指定对象是否存在', [
    { name: '集合对象', type: 'List' },
    { name: '要判断的对象', type: 'Object' },
  ], { nsName: 'CONTAINS' }),
  m('LISTEMPTY', 'LIST', 'value', 'Boolean', LIST, 'List集合', 'isEmpty', 'List是否为空', [
    { name: '集合对象', type: 'List' },
  ], { nsName: 'EMPTY' }),
  m('LISTSORT', 'LIST', 'both', 'List', LIST, 'List集合', 'sort', '集合排序', [
    { name: '集合对象', type: 'List' },
    { name: '属性名', type: 'String' },
    { name: '排序方式', type: 'String' },
  ], { nsName: 'SORT' }),
  m('LISTRETRIVE', 'LIST', 'value', 'List', LIST, 'List集合', 'retrive', '抽取集合属性', [
    { name: '集合对象', type: 'List' },
    { name: '属性名', type: 'String' },
  ], { nsName: 'RETRIVE', hint: '引擎方法名拼写为 retrive' }),
  m('MAPGET', 'MAP', 'value', 'Object', MAP, 'Map集合', 'get', '从Map中取值', [
    { name: 'Map对象', type: 'Map' },
    { name: 'key', type: 'String' },
  ], { nsName: 'GET' }),
  m('MAPSIZE', 'MAP', 'value', 'Number', MAP, 'Map集合', 'size', '返回Map大小', [{ name: 'Map对象', type: 'Map' }], {
    nsName: 'SIZE',
  }),
  m('MAPHAS', 'MAP', 'value', 'Boolean', MAP, 'Map集合', 'containsKey', '指定Key是否存在', [
    { name: 'Map对象', type: 'Map' },
    { name: 'key', type: 'String' },
  ], { nsName: 'HAS' }),

  // 动作
  m('LISTADD', 'LIST', 'action', 'Void', LIST, 'List集合', 'add', '向List中添加对象', [
    { name: '集合对象', type: 'List' },
    { name: '要添加的对象', type: 'Object' },
  ], { nsName: 'ADD' }),
  m('LISTREMOVE', 'LIST', 'action', 'Void', LIST, 'List集合', 'remove', '从List中删除对象', [
    { name: '集合对象', type: 'List' },
    { name: '要删除的对象', type: 'Object' },
  ], { nsName: 'REMOVE' }),
  m('MAPPUT', 'MAP', 'action', 'Void', MAP, 'Map集合', 'put', '添加到Map', [
    { name: 'Map对象', type: 'Map' },
    { name: 'key', type: 'String' },
    { name: 'value', type: 'Object' },
  ], { nsName: 'PUT' }),
  m('MAPREMOVE', 'MAP', 'action', 'Void', MAP, 'Map集合', 'remove', '从Map中删除', [
    { name: 'Map对象', type: 'Map' },
    { name: 'key', type: 'String' },
  ], { nsName: 'REMOVE' }),

  // 6.5 集合聚合
  agg('COUNT', 'Count', '统计数量', false),
  agg('SUM', 'Sum', '求和', true),
  agg('AVG', 'Avg', '求平均值', true),
  agg('MAXOF', 'Max', '求最大值', true),
  agg('MINOF', 'Min', '求最小值', true),
];

export const FLAT_FUNC_NAMES: string[] = PAIRS.map((f) => f.rea);

const byRea = new Map<string, BuiltinFunc>(PAIRS.map((f) => [f.rea, f]));
const byMethod = new Map<string, BuiltinFunc>();
const byCommon = new Map<string, BuiltinFunc>();
const byNs = new Map<string, BuiltinFunc>();

for (const f of PAIRS) {
  if (f.engine === 'method' && f.bean && f.method) {
    byMethod.set(`${f.bean}\0${f.method}`, f);
  }
  if (f.engine === 'commonfunction' && f.functionName) {
    byCommon.set(f.functionName, f);
  }
  if (f.ns && f.nsName) {
    byNs.set(`${f.ns}.${f.nsName}`, f);
  }
}

export function lookupByRea(name: string): BuiltinFunc | undefined {
  return byRea.get(name);
}

export function lookupByMethod(bean: string, method: string): BuiltinFunc | undefined {
  return byMethod.get(`${bean}\0${method}`);
}

export function lookupByCommonFunction(functionName: string): BuiltinFunc | undefined {
  return byCommon.get(functionName);
}

export function lookupNamespaced(ns: string, local: string): BuiltinFunc | undefined {
  return byNs.get(`${ns}.${local}`);
}

export function isNamespace(word: string): word is FuncNamespace {
  return (NAMESPACES as readonly string[]).includes(word);
}

export function funcsInNamespace(ns: string): BuiltinFunc[] {
  return PAIRS.filter((f) => f.ns === ns);
}

export function allFuncs(): BuiltinFunc[] {
  return PAIRS;
}

export const FUNC_HINTS = new Map<string, string>(
  PAIRS.filter((f) => f.hint).map((f) => [f.rea, f.hint!]),
);

function methodAttrs(fn: BuiltinFunc, beanAttr: 'bean-name' | 'bean'): string {
  return `${beanAttr}="${escapeXml(fn.bean!)}" bean-label="${escapeXml(fn.beanLabel!)}" method-name="${escapeXml(fn.method!)}" method-label="${escapeXml(fn.methodLabel!)}"`;
}

function wrapParams(fn: BuiltinFunc, argXmls: string[]): string {
  const params = fn.params ?? [];
  return params
    .map((p, i) => `<parameter name="${escapeXml(p.name)}" type="${p.type}">${argXmls[i]}</parameter>`)
    .join('');
}

export function expectedArity(fn: BuiltinFunc): number {
  if (fn.engine === 'commonfunction') return fn.needProperty ? 2 : 1;
  return fn.params?.length ?? 0;
}

export function buildMethodValueXml(fn: BuiltinFunc, argXmls: string[]): string {
  return `<value type="Method" ${methodAttrs(fn, 'bean-name')}>${wrapParams(fn, argXmls)}</value>`;
}

export function buildMethodLeftXml(fn: BuiltinFunc, argXmls: string[]): string {
  return `<left type="method" ${methodAttrs(fn, 'bean-name')}>\n      ${wrapParams(fn, argXmls)}\n    </left>`;
}

export function buildExecuteMethodXml(fn: BuiltinFunc, argXmls: string[]): string {
  return `<execute-method ${methodAttrs(fn, 'bean')}>${wrapParams(fn, argXmls)}</execute-method>`;
}

export function buildCommonValueXml(fn: BuiltinFunc, objectXml: string, property?: string): string {
  const prop = fn.needProperty && property != null ? ` property-name="${escapeXml(property)}"` : '';
  return `<value type="CommonFunction" function-name="${escapeXml(fn.functionName!)}" function-label="${escapeXml(fn.functionLabel!)}"><function-parameter name="集合对象"${prop}>${objectXml}</function-parameter></value>`;
}

export function buildCommonLeftXml(fn: BuiltinFunc, objectXml: string, property?: string): string {
  const prop = fn.needProperty && property != null ? ` property-name="${escapeXml(property)}"` : '';
  return `<left type="commonfunction" function-name="${escapeXml(fn.functionName!)}" function-label="${escapeXml(fn.functionLabel!)}">\n      <function-parameter name="集合对象"${prop}>${objectXml}</function-parameter>\n    </left>`;
}

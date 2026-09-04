/**
 * functionExecSpec.ts — REA 函数执行规格：每个函数一条实参/期望值。
 *
 * 事实源：functionMap.ts 的配对表。
 * 用途：
 *   1. rea-function-map-sync.test.ts 把本表 + 编译器输出导出成
 *      ruleuler-client/src/test/resources/rea-function-map.json
 *   2. Java 侧 ReaFunctionExecutionTest 读 JSON 在引擎里真跑，按 assert 断言。
 *
 * 固定事实（Java 测试侧必须与此一致，见 ReaFunctionExecutionTest）：
 *   FlightInfo: name="  ruleuler  ", score=-42, tags=[3,1,2], extra={k:"v"},
 *               is_international=true, departure=2026-09-04 10:30:15
 *   Order:      items=[{amount:100},{amount:200},{amount:300}]
 * 自定义 bean riskService（Java 测试上下文注册）：
 *   score(s, n) = s.trim().length() * n
 *   isHigh(s)   = s.trim().length() > 5
 *   addTag(list, t) → void
 */

import type { LibraryData } from './expressionParser';

/** 断言类型：Java 侧 ReaFunctionExecutionTest 按此分派 */
export type AssertType =
  | 'string'        // path 值 toString 等于 value
  | 'number'        // path 值转 BigDecimal 与 value 比较（忽略精度表示）
  | 'approx'        // path 值转 double 与 value 差 < 1e-9
  | 'nonnull'       // path 值非 null
  | 'dateEquals'    // path 值按 yyyy-MM-dd HH:mm:ss 格式化后等于 value
  | 'listSize'      // path 值为 List，size == value
  | 'mapSize'       // path 值为 Map，size == value
  | 'amountOf'      // path 值为 GeneralEntity，取 amount 与 value 比较
  | 'listFirstAmount'; // path 值为 List<GeneralEntity>，首元素 amount 与 value 比较

export interface ExecCase {
  /** 配对表里的 REA 名；自定义函数为 BEAN.METHOD */
  rea: string;
  /** value: 赋值右值导出 var-assign；action: 导出 execute-method；condition: 导出完整 if */
  position: 'value' | 'action' | 'condition';
  /** condition 时用 `FN(...) == TRUE` 而非裸 atom（自定义函数返回类型未知时必需） */
  compareTrue?: boolean;
  /** 每个实参的 REA 文本（允许嵌套函数，如 PARSEDATE(...)） */
  argsRea: string[];
  /** 覆盖固定事实（LISTEMPTY 需要 tags=[] 之类） */
  facts?: Record<string, Record<string, unknown>>;
  /** 断言目标，默认 FlightInfo.result（赋值/条件命中写它；动作看后置状态） */
  path?: string;
  assert: { type: AssertType; value?: string | number };
}

export const EXEC_LIBS: LibraryData = {
  variables: [
    {
      name: 'FlightInfo',
      variables: [
        { name: 'name', label: '名称', type: 'String' },
        { name: 'score', label: '分数', type: 'Integer' },
        { name: 'tags', label: '标签', type: 'List' },
        { name: 'extra', label: '扩展', type: 'Map' },
        { name: 'is_international', label: '国际', type: 'Boolean' },
        { name: 'departure', label: '起飞时间', type: 'Date' },
        { name: 'result', label: '结果', type: 'Object' },
        { name: 'tag_to_remove', label: '待删元素', type: 'Integer' },
      ],
    },
    {
      name: 'Order',
      variables: [{ name: 'items', label: '明细', type: 'List' }],
    },
  ],
  parameters: [{ name: 'can_score', label: '分数出参', type: 'Integer' }],
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
        {
          name: '高风险',
          methodName: 'isHigh',
          parameters: [{ name: '航司', type: 'String' }],
        },
        {
          name: '加标签',
          methodName: 'addTag',
          parameters: [
            { name: '集合', type: 'List' },
            { name: '标签', type: 'Object' },
          ],
        },
      ],
    },
  ],
};

const D = 'FlightInfo.departure';

export const EXEC_CASES: ExecCase[] = [
  // ── 6.1 字符串 ──
  { rea: 'TRIM', position: 'value', argsRea: ['FlightInfo.name'], assert: { type: 'string', value: 'ruleuler' } },
  { rea: 'SUBSTRING', position: 'value', argsRea: ['FlightInfo.name', '2', '6'], assert: { type: 'string', value: 'rule' } },
  { rea: 'SUBSTRINGFROM', position: 'value', argsRea: ['FlightInfo.name', '2'], assert: { type: 'string', value: 'ruleuler  ' } },
  { rea: 'SUBSTRINGTO', position: 'value', argsRea: ['FlightInfo.name', '6'], assert: { type: 'string', value: '  rule' } },
  { rea: 'LOWER', position: 'value', argsRea: ['"ReaFunc"'], assert: { type: 'string', value: 'reafunc' } },
  { rea: 'UPPER', position: 'value', argsRea: ['"reafunc"'], assert: { type: 'string', value: 'REAFUNC' } },
  { rea: 'LENGTH', position: 'value', argsRea: ['"ruleuler"'], assert: { type: 'number', value: 8 } },
  { rea: 'CHARAT', position: 'value', argsRea: ['"ruleuler"', '3'], assert: { type: 'string', value: 'e' } },
  { rea: 'INDEXOF', position: 'value', argsRea: ['"banana"', '"na"'], assert: { type: 'number', value: 2 } },
  { rea: 'LASTINDEXOF', position: 'value', argsRea: ['"banana"', '"na"'], assert: { type: 'number', value: 4 } },
  { rea: 'REPLACE', position: 'value', argsRea: ['"a-b-c"', '"-"', '"+"'], assert: { type: 'string', value: 'a+b+c' } },
  { rea: 'SPLIT', position: 'value', argsRea: ['"a,b,c"', '","'], assert: { type: 'listSize', value: 3 } },

  // ── 6.2 数学（引擎返回 double）──
  { rea: 'ABS', position: 'value', argsRea: ['FlightInfo.score'], assert: { type: 'number', value: 42 } },
  { rea: 'MAX', position: 'value', argsRea: ['3', '9'], assert: { type: 'number', value: 9 } },
  { rea: 'MIN', position: 'value', argsRea: ['3', '9'], assert: { type: 'number', value: 3 } },
  { rea: 'SIN', position: 'value', argsRea: ['0'], assert: { type: 'approx', value: 0 } },
  { rea: 'COS', position: 'value', argsRea: ['0'], assert: { type: 'approx', value: 1 } },
  { rea: 'TAN', position: 'value', argsRea: ['0'], assert: { type: 'approx', value: 0 } },
  { rea: 'COT', position: 'value', argsRea: ['1'], assert: { type: 'approx', value: 0.6420926159343308 } },
  { rea: 'LN', position: 'value', argsRea: ['1'], assert: { type: 'approx', value: 0 } },
  { rea: 'LOG10', position: 'value', argsRea: ['100'], assert: { type: 'approx', value: 2 } },
  { rea: 'ROUND', position: 'value', argsRea: ['3.6'], assert: { type: 'number', value: 4 } },

  // ── 6.3 日期（departure = 2026-09-04 10:30:15，周五）──
  { rea: 'NOW', position: 'value', argsRea: [], assert: { type: 'nonnull' } },
  { rea: 'PARSEDATE', position: 'value', argsRea: ['"2026-09-04"', '"yyyy-MM-dd"'], assert: { type: 'dateEquals', value: '2026-09-04 00:00:00' } },
  { rea: 'FORMATDATE', position: 'value', argsRea: [D, '"yyyy-MM-dd HH:mm:ss"'], assert: { type: 'string', value: '2026-09-04 10:30:15' } },
  { rea: 'ADDYEARS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2027-09-04 10:30:15' } },
  { rea: 'ADDMONTHS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-10-04 10:30:15' } },
  { rea: 'ADDDAYS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-05 10:30:15' } },
  { rea: 'ADDHOURS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 11:30:15' } },
  { rea: 'ADDMINUTES', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 10:31:15' } },
  { rea: 'ADDSECONDS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 10:30:16' } },
  { rea: 'SUBYEARS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2025-09-04 10:30:15' } },
  { rea: 'SUBMONTHS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-08-04 10:30:15' } },
  { rea: 'SUBDAYS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-03 10:30:15' } },
  { rea: 'SUBHOURS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 09:30:15' } },
  { rea: 'SUBMINUTES', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 10:29:15' } },
  { rea: 'SUBSECONDS', position: 'value', argsRea: [D, '1'], assert: { type: 'dateEquals', value: '2026-09-04 10:30:14' } },
  // MONTH 0-based、WEEK 1=周日、DAY→getay 的语义锁定
  { rea: 'YEAR', position: 'value', argsRea: [D], assert: { type: 'number', value: 2026 } },
  { rea: 'MONTH', position: 'value', argsRea: [D], assert: { type: 'number', value: 8 } },
  { rea: 'WEEK', position: 'value', argsRea: [D], assert: { type: 'number', value: 6 } },
  { rea: 'DAY', position: 'value', argsRea: [D], assert: { type: 'number', value: 4 } },
  { rea: 'HOUR', position: 'value', argsRea: [D], assert: { type: 'number', value: 10 } },
  { rea: 'MINUTE', position: 'value', argsRea: [D], assert: { type: 'number', value: 30 } },
  { rea: 'SECOND', position: 'value', argsRea: [D], assert: { type: 'number', value: 15 } },
  // 嵌套函数当实参
  { rea: 'DIFFMILLIS', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 86400000 } },
  { rea: 'DIFFSECONDS', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 86400 } },
  { rea: 'DIFFMINUTES', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 1440 } },
  { rea: 'DIFFHOURS', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 24 } },
  { rea: 'DIFFDAYS', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 1 } },
  { rea: 'DIFFWEEKS', position: 'value', argsRea: ['PARSEDATE("2026-09-05", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 0 } },
  { rea: 'DIFFMONTHS', position: 'value', argsRea: ['PARSEDATE("2026-10-04", "yyyy-MM-dd")', 'PARSEDATE("2026-09-04", "yyyy-MM-dd")'], assert: { type: 'number', value: 1 } },

  // ── 6.4 List / Map 值函数 ──
  { rea: 'LISTSIZE', position: 'value', argsRea: ['FlightInfo.tags'], assert: { type: 'number', value: 3 } },
  { rea: 'LISTMAX', position: 'value', argsRea: ['FlightInfo.tags'], assert: { type: 'number', value: 3 } },
  { rea: 'LISTMIN', position: 'value', argsRea: ['FlightInfo.tags'], assert: { type: 'number', value: 1 } },
  { rea: 'LISTCONTAINS', position: 'condition', argsRea: ['FlightInfo.tags', '1'], assert: { type: 'string', value: 'FIRED' } },
  { rea: 'LISTEMPTY', position: 'condition', argsRea: ['FlightInfo.tags'], facts: { FlightInfo: { tags: [] } }, assert: { type: 'string', value: 'FIRED' } },
  // sort 的升序词面是 "正序"（引擎只认 1/true/正序，详见语义锁定用例）
  { rea: 'LISTSORT', position: 'value', argsRea: ['Order.items', 'amount', '"正序"'], assert: { type: 'listFirstAmount', value: 100 } },
  { rea: 'LISTRETRIVE', position: 'value', argsRea: ['Order.items', 'amount'], assert: { type: 'listSize', value: 3 } },
  { rea: 'MAPGET', position: 'value', argsRea: ['FlightInfo.extra', '"k"'], assert: { type: 'string', value: 'v' } },
  { rea: 'MAPSIZE', position: 'value', argsRea: ['FlightInfo.extra'], assert: { type: 'number', value: 1 } },
  { rea: 'MAPHAS', position: 'condition', argsRea: ['FlightInfo.extra', '"k"'], assert: { type: 'string', value: 'FIRED' } },

  // ── 动作函数（看事实后置状态）──
  { rea: 'LISTADD', position: 'action', argsRea: ['FlightInfo.tags', '99'], path: 'FlightInfo.tags', assert: { type: 'listSize', value: 4 } },
  // 字面量实参是字符串，删 Integer 元素必须用变量传（引擎 remove(Object) 不做类型转换）
  { rea: 'LISTREMOVE', position: 'action', argsRea: ['FlightInfo.tags', 'FlightInfo.tag_to_remove'], path: 'FlightInfo.tags', assert: { type: 'listSize', value: 2 } },
  { rea: 'MAPPUT', position: 'action', argsRea: ['FlightInfo.extra', '"k2"', '"v2"'], path: 'FlightInfo.extra', assert: { type: 'mapSize', value: 2 } },
  { rea: 'MAPREMOVE', position: 'action', argsRea: ['FlightInfo.extra', '"k"'], path: 'FlightInfo.extra', assert: { type: 'mapSize', value: 0 } },

  // ── 6.5 聚合 ──
  { rea: 'COUNT', position: 'value', argsRea: ['Order.items'], assert: { type: 'number', value: 3 } },
  { rea: 'SUM', position: 'value', argsRea: ['Order.items', 'amount'], assert: { type: 'number', value: 600 } },
  { rea: 'AVG', position: 'value', argsRea: ['Order.items', 'amount'], assert: { type: 'number', value: 200 } },
  { rea: 'MAXOF', position: 'value', argsRea: ['Order.items', 'amount'], assert: { type: 'amountOf', value: 300 } },
  { rea: 'MINOF', position: 'value', argsRea: ['Order.items', 'amount'], assert: { type: 'amountOf', value: 100 } },

  // ── 自定义动作库 bean（返回类型编译期未知，布尔要显式 == TRUE，不能裸用）──
  { rea: 'RISKSERVICE.SCORE', position: 'value', argsRea: ['FlightInfo.name', '10'], assert: { type: 'number', value: 80 } },
  { rea: 'RISKSERVICE.ISHIGH', position: 'condition', argsRea: ['FlightInfo.name'], compareTrue: true, assert: { type: 'string', value: 'FIRED' } },
  { rea: 'RISKSERVICE.ADDTAG', position: 'action', argsRea: ['FlightInfo.tags', '"X"'], path: 'FlightInfo.tags', assert: { type: 'listSize', value: 4 } },
];

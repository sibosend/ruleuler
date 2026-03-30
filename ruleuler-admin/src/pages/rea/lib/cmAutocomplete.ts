/**
 * REA 自动补全扩展
 */
import { autocompletion, type CompletionContext, type CompletionResult, type Completion } from '@codemirror/autocomplete';
import type { Extension } from '@codemirror/state';
import type { LibraryData } from './expressionParser';
import { ALL_TEXT_OPERATORS } from './operatorMap';

/** 补全候选项（纯函数，可测试） */
export function buildCompletions(
  type: 'condition' | 'action' | 'else',
  libs: LibraryData,
): Completion[] {
  const items: Completion[] = [];

  // 变量类别名
  for (const cat of libs.variables) {
    items.push({ label: cat.name, type: 'class', detail: '变量类别' });
    // 变量名
    for (const v of cat.variables) {
      items.push({
        label: `${cat.name}.${v.name}`,
        type: 'variable',
        detail: v.type || 'String',
      });
    }
  }

  // 参数（裸名，不带 参数. 前缀）
  for (const p of libs.parameters) {
    items.push({
      label: p.name,
      type: 'variable',
      detail: `参数 ${p.type || 'String'}`,
    });
  }

  // condition 类型额外提供操作符关键字
  if (type === 'condition') {
    for (const op of ALL_TEXT_OPERATORS) {
      items.push({ label: op, type: 'keyword', detail: '操作符' });
    }
    // AND / OR
    items.push({ label: 'AND', type: 'keyword', detail: '逻辑连接' });
    items.push({ label: 'OR', type: 'keyword', detail: '逻辑连接' });
    // Boolean 字面量
    items.push({ label: 'true', type: 'constant', detail: '布尔值' });
    items.push({ label: 'false', type: 'constant', detail: '布尔值' });
  }

  return items;
}

/** 根据 `.` 前的类别名过滤出该类别下的变量 */
function dotCompletions(
  categoryName: string,
  libs: LibraryData,
): Completion[] {
  // 变量类别
  const cat = libs.variables.find((c) => c.name === categoryName);
  if (cat) {
    return cat.variables.map((v) => ({
      label: v.name,
      type: 'variable',
      detail: v.type || 'String',
    }));
  }
  return [];
}

/** 导出：REA 自动补全扩展 */
export function reaAutocompletion(
  type: 'condition' | 'action' | 'else',
  libs: LibraryData,
): Extension {
  const allItems = buildCompletions(type, libs);

  function completionSource(ctx: CompletionContext): CompletionResult | null {
    // 输入 `.` 后触发：取 `.` 前的标识符作为类别名
    const dotMatch = ctx.matchBefore(/[\u4e00-\u9fffa-zA-Z_][\u4e00-\u9fffa-zA-Z0-9_]*\.$/);
    if (dotMatch) {
      const catName = dotMatch.text.slice(0, -1); // 去掉末尾 `.`
      const items = dotCompletions(catName, libs);
      if (items.length > 0) {
        return { from: ctx.pos, options: items };
      }
    }

    // 通用补全：匹配标识符前缀
    const wordMatch = ctx.matchBefore(/[\u4e00-\u9fffa-zA-Z_][\u4e00-\u9fffa-zA-Z0-9_.]*$/);
    if (wordMatch) {
      return { from: wordMatch.from, options: allItems, validFor: /^[\u4e00-\u9fffa-zA-Z0-9_.]*$/ };
    }

    // 显式触发（Ctrl-Space / Alt-/）
    if (ctx.explicit) {
      return { from: ctx.pos, options: allItems };
    }

    return null;
  }

  return autocompletion({
    override: [completionSource],
    activateOnTyping: true,
  });
}

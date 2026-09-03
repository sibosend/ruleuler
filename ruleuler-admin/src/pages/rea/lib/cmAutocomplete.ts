/**
 * REA 自动补全扩展
 */
import { autocompletion, type CompletionContext, type CompletionResult, type Completion } from '@codemirror/autocomplete';
import type { EditorView } from '@codemirror/view';
import type { Extension } from '@codemirror/state';
import type { LibraryData } from './expressionParser';
import { ALL_TEXT_OPERATORS } from './operatorMap';
import { allFuncs, funcsInNamespace, isNamespace, NAMESPACES, type BuiltinFunc } from './functionMap';

export interface AutocompleteOptions {
  allowFunctions?: boolean;
}

function funcDetail(fn: BuiltinFunc): string {
  const n = fn.engine === 'commonfunction' ? (fn.needProperty ? 2 : 1) : (fn.params?.length ?? 0);
  const args = Array.from({ length: n }, (_, i) => `A${i + 1}`).join(', ');
  return fn.kind === 'action' ? `${args || '—'} → VOID` : `${args || '—'} → ${fn.returnType.toUpperCase()}`;
}

function applyCall(label: string) {
  return (view: EditorView, _completion: Completion, from: number, to: number) => {
    const insert = `${label}()`;
    view.dispatch({
      changes: { from, to, insert },
      selection: { anchor: from + label.length + 1 },
    });
  };
}

function funcCompletion(fn: BuiltinFunc, useNsName = false): Completion {
  const label = useNsName ? (fn.nsName ?? fn.rea) : fn.rea;
  return {
    label,
    type: 'function',
    detail: funcDetail(fn),
    apply: applyCall(label),
  };
}

export function buildCompletions(
  type: 'condition' | 'action' | 'else',
  libs: LibraryData,
  options?: AutocompleteOptions,
): Completion[] {
  const items: Completion[] = [];
  const allowFunctions = options?.allowFunctions !== false;

  for (const cat of libs.variables) {
    items.push({ label: cat.name, type: 'class', detail: '变量类别' });
    for (const v of cat.variables) {
      items.push({
        label: `${cat.name}.${v.name}`,
        type: 'variable',
        detail: v.type || 'String',
      });
    }
  }

  for (const p of libs.parameters) {
    items.push({
      label: p.name,
      type: 'variable',
      detail: `参数 ${p.type || 'String'}`,
    });
  }

  if (type === 'condition') {
    for (const op of ALL_TEXT_OPERATORS) {
      items.push({ label: op, type: 'keyword', detail: '操作符' });
    }
    items.push({ label: 'AND', type: 'keyword', detail: '逻辑连接' });
    items.push({ label: 'OR', type: 'keyword', detail: '逻辑连接' });
    items.push({ label: 'TRUE', type: 'constant', detail: '布尔值' });
    items.push({ label: 'FALSE', type: 'constant', detail: '布尔值' });
  }

  if (allowFunctions) {
    for (const fn of allFuncs()) {
      if (type === 'condition' && fn.kind === 'action') continue;
      items.push(funcCompletion(fn));
    }
    for (const ns of NAMESPACES) {
      items.push({ label: ns, type: 'class', detail: '函数命名空间' });
    }
    for (const bean of libs.actions ?? []) {
      const beanRea = bean.id.toUpperCase();
      items.push({ label: beanRea, type: 'class', detail: bean.name || '动作库' });
      for (const method of bean.methods) {
        const label = `${beanRea}.${method.methodName.toUpperCase()}`;
        items.push({
          label,
          type: 'function',
          detail: method.name || '自定义',
          apply: applyCall(label),
        });
      }
    }
  }

  return items;
}

function dotCompletions(
  categoryName: string,
  libs: LibraryData,
  type: 'condition' | 'action' | 'else',
  allowFunctions: boolean,
): Completion[] {
  if (allowFunctions && isNamespace(categoryName)) {
    return funcsInNamespace(categoryName)
      .filter((fn) => !(type === 'condition' && fn.kind === 'action'))
      .map((fn) => funcCompletion(fn, true));
  }
  if (allowFunctions) {
    const bean = (libs.actions ?? []).find((b) => b.id.toUpperCase() === categoryName);
    if (bean) {
      return bean.methods.map((method) => {
        const label = method.methodName.toUpperCase();
        return {
          label,
          type: 'function' as const,
          detail: method.name || '自定义',
          apply: applyCall(label),
        };
      });
    }
  }
  const cat = libs.variables.find((c) => c.name === categoryName);
  if (cat) {
    return cat.variables.map((v) => ({
      label: v.name,
      type: 'variable' as const,
      detail: v.type || 'String',
    }));
  }
  return [];
}

export function reaAutocompletion(
  type: 'condition' | 'action' | 'else',
  libs: LibraryData,
  options?: AutocompleteOptions,
): Extension {
  const allowFunctions = options?.allowFunctions !== false;
  const allItems = buildCompletions(type, libs, options);

  function completionSource(ctx: CompletionContext): CompletionResult | null {
    const dotMatch = ctx.matchBefore(/[\u4e00-\u9fffa-zA-Z_][\u4e00-\u9fffa-zA-Z0-9_]*\.$/);
    if (dotMatch) {
      const catName = dotMatch.text.slice(0, -1);
      const items = dotCompletions(catName, libs, type, allowFunctions);
      if (items.length > 0) {
        return { from: ctx.pos, options: items };
      }
    }

    const wordMatch = ctx.matchBefore(/[\u4e00-\u9fffa-zA-Z_][\u4e00-\u9fffa-zA-Z0-9_.]*$/);
    if (wordMatch) {
      return { from: wordMatch.from, options: allItems, validFor: /^[\u4e00-\u9fffa-zA-Z0-9_.]*$/ };
    }

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

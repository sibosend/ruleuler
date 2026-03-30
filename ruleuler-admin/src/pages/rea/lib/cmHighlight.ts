/**
 * REA 语法高亮扩展 — 基于 StreamLanguage
 */
import { StreamLanguage, HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags } from '@lezer/highlight';
import type { Extension } from '@codemirror/state';
import type { StringStream } from '@codemirror/language';
import { ALL_TEXT_OPERATORS } from './operatorMap';

/** 文字操作符集合（用于快速查找） */
const WORD_OPS = new Set(ALL_TEXT_OPERATORS.filter((op) => /^[a-zA-Z]/.test(op)));

interface ReaState {
  /** 是否在双引号字符串内 */
  inString: boolean;
}

const reaStreamParser = {
  startState(): ReaState {
    return { inString: false };
  },

  token(stream: StringStream, state: ReaState): string | null {
    // 字符串续行
    if (state.inString) {
      while (!stream.eol()) {
        if (stream.next() === '"') {
          state.inString = false;
          return 'string';
        }
      }
      return 'string';
    }

    // 跳过空白
    if (stream.eatSpace()) return null;

    const ch = stream.peek()!;

    // 双引号字符串
    if (ch === '"') {
      stream.next();
      while (!stream.eol()) {
        const c = stream.next();
        if (c === '\\') {
          stream.next(); // 跳过转义
        } else if (c === '"') {
          return 'string';
        }
      }
      state.inString = true;
      return 'string';
    }

    // 数字（含负号开头）
    if (/[0-9]/.test(ch) || (ch === '-' && /[0-9]/.test(stream.string.charAt(stream.pos + 1)))) {
      if (ch === '-') stream.next();
      stream.match(/^[0-9]*\.?[0-9]*/);
      return 'number';
    }

    // 符号操作符：>=, <=, !=, ==, >, <
    if (stream.match(/^(?:>=|<=|!=|==|>|<)/)) {
      return 'operator';
    }

    // 赋值 =
    if (ch === '=') {
      stream.next();
      return 'operator';
    }

    // 点号、分号、括号、逗号 — 标点
    if ('.;(),'.includes(ch)) {
      stream.next();
      return 'punctuation';
    }

    // 标识符 / 关键字
    if (stream.match(/^[a-zA-Z_\u4e00-\u9fff][a-zA-Z0-9_\u4e00-\u9fff]*/)) {
      const word = stream.current();
      if (word === 'AND' || word === 'OR') return 'keyword';
      if (WORD_OPS.has(word)) return 'operator';
      return 'variableName';
    }

    // 未知字符，跳过
    stream.next();
    return null;
  },
};

const reaLanguage = StreamLanguage.define<ReaState>(reaStreamParser);

/** REA 高亮主题（VS Code Dark+ 风格） */
const reaHighlightStyle = HighlightStyle.define([
  { tag: tags.variableName, color: '#9cdcfe' },            // 浅蓝 — 标识符
  { tag: tags.operator, color: '#d4d4d4' },                // 浅灰 — 操作符
  { tag: tags.string, color: '#ce9178' },                  // 暖橙 — 字符串
  { tag: tags.keyword, color: '#c586c0', fontWeight: 'bold' }, // 粉紫 — AND/OR
  { tag: tags.number, color: '#b5cea8' },                  // 浅绿 — 数字
]);

/** 导出：REA 语法高亮扩展 */
export function reaSyntaxHighlighting(): Extension {
  return [reaLanguage, syntaxHighlighting(reaHighlightStyle)];
}

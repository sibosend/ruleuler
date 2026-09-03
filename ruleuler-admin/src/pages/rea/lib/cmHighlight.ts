/**
 * REA 语法高亮 + 函数怪癖悬浮
 */
import { StreamLanguage, HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags } from '@lezer/highlight';
import { hoverTooltip, type Tooltip } from '@codemirror/view';
import type { Extension } from '@codemirror/state';
import type { StringStream } from '@codemirror/language';
import { WORD_OPS } from './operatorMap';
import { FLAT_FUNC_NAMES, FUNC_HINTS, NAMESPACES } from './functionMap';

const WORD_OP_SET = new Set(WORD_OPS);
const FUNC_SET = new Set(FLAT_FUNC_NAMES);
const NS_SET = new Set<string>(NAMESPACES);

interface ReaState {
  inString: boolean;
}

const reaStreamParser = {
  startState(): ReaState {
    return { inString: false };
  },

  token(stream: StringStream, state: ReaState): string | null {
    if (state.inString) {
      while (!stream.eol()) {
        if (stream.next() === '"') {
          state.inString = false;
          return 'string';
        }
      }
      return 'string';
    }

    if (stream.eatSpace()) return null;

    const ch = stream.peek()!;

    if (ch === '"') {
      stream.next();
      while (!stream.eol()) {
        const c = stream.next();
        if (c === '\\') {
          stream.next();
        } else if (c === '"') {
          return 'string';
        }
      }
      state.inString = true;
      return 'string';
    }

    if (/[0-9]/.test(ch) || (ch === '-' && /[0-9]/.test(stream.string.charAt(stream.pos + 1)))) {
      if (ch === '-') stream.next();
      stream.match(/^[0-9]*\.?[0-9]*/);
      return 'number';
    }

    if (stream.match(/^(?:>=|<=|!=|==|>|<)/)) {
      return 'operator';
    }

    if (ch === '=') {
      stream.next();
      return 'operator';
    }

    if (';(),.'.includes(ch)) {
      stream.next();
      return 'punctuation';
    }

    if (stream.match(/^[a-zA-Z_\u4e00-\u9fff][a-zA-Z0-9_\u4e00-\u9fff]*/)) {
      const word = stream.current();
      if (word === 'AND' || word === 'OR') return 'keyword';
      if (word === 'TRUE' || word === 'FALSE') return 'atom';
      if (WORD_OP_SET.has(word)) return 'operator';
      if (FUNC_SET.has(word) || NS_SET.has(word)) return 'keyword';
      return 'variableName';
    }

    stream.next();
    return null;
  },
};

const reaLanguage = StreamLanguage.define<ReaState>(reaStreamParser);

const reaHighlightStyle = HighlightStyle.define([
  { tag: tags.variableName, color: '#9cdcfe' },
  { tag: tags.operator, color: '#d4d4d4' },
  { tag: tags.string, color: '#ce9178' },
  { tag: tags.keyword, color: '#c586c0', fontWeight: 'bold' },
  { tag: tags.number, color: '#b5cea8' },
  { tag: tags.atom, color: '#569cd6' },
]);

function reaHover(): Extension {
  return hoverTooltip((view, pos): Tooltip | null => {
    const word = view.state.sliceDoc(
      Math.max(0, pos - 32),
      Math.min(view.state.doc.length, pos + 32),
    );
    const offset = Math.min(pos, 32);
    const local = offset;
    const re = /[A-Z][A-Z0-9]*/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(word))) {
      if (local >= m.index && local <= m.index + m[0].length) {
        const hint = FUNC_HINTS.get(m[0]);
        if (!hint) return null;
        const from = pos - local + m.index;
        return {
          pos: from,
          end: from + m[0].length,
          create() {
            const dom = document.createElement('div');
            dom.textContent = hint;
            dom.style.padding = '4px 8px';
            dom.style.maxWidth = '360px';
            return { dom };
          },
        };
      }
    }
    return null;
  });
}

export function reaSyntaxHighlighting(): Extension {
  return [reaLanguage, syntaxHighlighting(reaHighlightStyle), reaHover()];
}

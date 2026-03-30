/**
 * REA 实时语法校验扩展 — 基于 @codemirror/lint
 *
 * 用前端 expressionParser 做本地语法检查，不调后端接口。
 * 用户停止输入 500ms 后校验。
 */
import { linter, type Diagnostic } from '@codemirror/lint';
import type { Extension } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import { parseCondition, parseAssignment, ParseError, type LibraryData } from './expressionParser';

export type LintStatus = 'idle' | 'valid' | 'error';

export function reaLintExtension(
  type: 'condition' | 'action' | 'else',
  libs: LibraryData,
  onStatusChange?: (status: LintStatus) => void,
): Extension {
  return linter(
    (view: EditorView): Diagnostic[] => {
      const doc = view.state.doc.toString();
      if (!doc.trim()) {
        onStatusChange?.('idle');
        return [];
      }

      try {
        if (type === 'condition') {
          parseCondition(doc, libs);
        } else {
          parseAssignment(doc, libs);
        }
        onStatusChange?.('valid');
        return [];
      } catch (e) {
        onStatusChange?.('error');
        if (e instanceof ParseError) {
          const pos = Math.min(e.position, doc.length);
          const to = Math.min(pos + 10, doc.length);
          return [{ from: pos, to: Math.max(to, pos + 1), severity: 'error', message: e.message }];
        }
        return [{ from: 0, to: Math.min(1, doc.length), severity: 'error', message: String(e) }];
      }
    },
    { delay: 500 },
  );
}

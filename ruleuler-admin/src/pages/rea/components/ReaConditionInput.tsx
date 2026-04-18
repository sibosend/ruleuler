/**
 * ReaConditionInput — 灰度条件表达式输入框
 *
 * 复用 REA 编辑器的 CodeMirror 体系：
 *  - 暗色主题（VS Code Dark+ 风格）
 *  - 语法高亮
 *  - 变量/操作符自动补全
 *  - 实时语法校验
 */
import React, { useRef, useEffect, useCallback } from 'react';
import { EditorView, keymap, lineNumbers, highlightActiveLine, placeholder as cmPlaceholder } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { useTranslation } from 'react-i18next';
import type { LibraryData } from '../lib/expressionParser';
import { reaSyntaxHighlighting } from '../lib/cmHighlight';
import { reaAutocompletion } from '../lib/cmAutocomplete';
import { reaLintExtension, type LintStatus } from '../lib/cmLint';

export interface ReaConditionInputProps {
  value: string;
  onChange: (value: string) => void;
  libraries: LibraryData;
  placeholder?: string;
  onLintStatus?: (status: LintStatus) => void;
}

const ReaConditionInput: React.FC<ReaConditionInputProps> = ({
  value,
  onChange,
  libraries,
  placeholder,
  onLintStatus,
}) => {
  const { t } = useTranslation();
  const resolvedPlaceholder = placeholder ?? t('rea.conditionInputPlaceholder');
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const internalUpdate = useRef(false);

  const createView = useCallback(() => {
    if (!containerRef.current) return;

    const startState = EditorState.create({
      doc: value,
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        history(),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        reaSyntaxHighlighting(),
        reaAutocompletion('condition', libraries),
        reaLintExtension('condition', libraries, onLintStatus),
        cmPlaceholder(resolvedPlaceholder),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !internalUpdate.current) {
            onChangeRef.current(update.state.doc.toString());
          }
        }),
        EditorView.theme({
          '&': {
            fontSize: '13px',
            backgroundColor: '#1e1e1e',
            color: '#d4d4d4',
          },
          '.cm-content': { fontFamily: 'Menlo, Monaco, Consolas, monospace', caretColor: '#fff' },
          '.cm-gutters': { backgroundColor: '#252526', color: '#858585', borderRight: '1px solid #333' },
          '.cm-activeLineGutter': { backgroundColor: '#2a2d2e' },
          '.cm-activeLine': { backgroundColor: '#2a2d2e' },
          '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': { backgroundColor: '#264f78' },
          '.cm-cursor': { borderLeftColor: '#fff' },
          '.cm-placeholder': { color: '#6a6a6a' },
        }, { dark: true }),
      ],
    });

    const view = new EditorView({ state: startState, parent: containerRef.current });
    viewRef.current = view;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [libraries, resolvedPlaceholder]);

  useEffect(() => {
    createView();
    return () => {
      viewRef.current?.destroy();
      viewRef.current = null;
    };
  }, [createView]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current !== value) {
      internalUpdate.current = true;
      view.dispatch({
        changes: { from: 0, to: current.length, insert: value },
      });
      internalUpdate.current = false;
    }
  }, [value]);

  return (
    <div
      ref={containerRef}
      style={{
        border: '1px solid #333',
        borderRadius: 4,
        overflow: 'auto',
        resize: 'vertical',
        minHeight: 56,
        maxHeight: 200,
        backgroundColor: '#1e1e1e',
      }}
    />
  );
};

export default ReaConditionInput;

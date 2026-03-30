import React, { useRef, useEffect, useCallback, useState } from 'react';
import { EditorView, keymap, lineNumbers, highlightActiveLine, placeholder as cmPlaceholder } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import type { LibraryData } from '../lib/expressionParser';
import { reaSyntaxHighlighting } from '../lib/cmHighlight';
import { reaAutocompletion } from '../lib/cmAutocomplete';
import { reaLintExtension, type LintStatus } from '../lib/cmLint';

export interface ExpressionAreaProps {
  label: string;
  type: 'condition' | 'action' | 'else';
  value: string;
  hasError: boolean;
  onChange: (value: string) => void;
  libraries: LibraryData;
  /** 帮助文档 URL，显示为 label 旁的 ? 图标 */
  helpUrl?: string;
}

const PLACEHOLDERS: Record<ExpressionAreaProps['type'], string> = {
  condition: '例: A.score > 5 AND (B.type == "VIP" OR level == "high")',
  action: '例: score = 10; level = "high"  多个用 ; 分隔',
  else: '例: score = 0; level = "low"  多个用 ; 分隔（可选）',
};

const ExpressionArea: React.FC<ExpressionAreaProps> = ({
  label,
  type,
  value,
  hasError,
  onChange,
  libraries,
  helpUrl,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const [lintStatus, setLintStatus] = useState<LintStatus>('idle');
  // 用 ref 追踪最新 onChange，避免重建 EditorView
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // 标记内部更新，防止 value 同步回写时触发 onChange
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
        reaAutocompletion(type, libraries),
        reaLintExtension(type, libraries, setLintStatus),
        cmPlaceholder(PLACEHOLDERS[type]),
        EditorView.editable.of(!hasError),
        EditorState.readOnly.of(hasError),
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
  }, []); // 只创建一次

  // 初始化
  useEffect(() => {
    createView();
    return () => {
      viewRef.current?.destroy();
      viewRef.current = null;
    };
  }, [createView]);

  // value 外部变化时同步到 EditorView
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
    <div style={{ display: 'flex', marginBottom: 6 }}>
      <div
        style={{
          width: 40,
          flexShrink: 0,
          color: '#666',
          fontSize: 12,
          paddingTop: 4,
          textAlign: 'right',
          marginRight: 8,
        }}
      >
        {label}
        {helpUrl && (
          <a
            href={helpUrl}
            target="_blank"
            rel="noopener noreferrer"
            title="表达式语法帮助"
            style={{ marginLeft: 2, color: '#1677ff', textDecoration: 'none', fontSize: 11 }}
          >
            ?
          </a>
        )}
      </div>
      <div style={{ flex: 1, minWidth: 0, position: 'relative' }}>
        <div
          ref={containerRef}
          style={{
            border: `1px solid ${hasError ? '#ff4d4f' : '#333'}`,
            borderRadius: 4,
            overflow: 'auto',
            resize: 'vertical',
            minHeight: 72,
            maxHeight: 300,
            backgroundColor: '#1e1e1e',
          }}
        />
        {hasError && (
          <span
            style={{
              position: 'absolute',
              top: 4,
              right: 8,
              color: '#ff4d4f',
              fontSize: 12,
              lineHeight: 1,
              pointerEvents: 'none',
            }}
            title="不支持的表达式，请使用向导式编辑器"
          >
            ⚠ 只读
          </span>
        )}
        {!hasError && lintStatus === 'valid' && (
          <span
            style={{
              position: 'absolute',
              top: 4,
              right: 8,
              color: '#52c41a',
              fontSize: 16,
              fontWeight: 'bold',
              lineHeight: 1,
              pointerEvents: 'none',
            }}
            title="语法正确"
          >
            ✓
          </span>
        )}
        {/* AssistPanel 挂载点 - 后续任务实现 */}
        <div className="assist-panel-slot" />
      </div>
    </div>
  );
};

export default ExpressionArea;

import React, { useMemo } from 'react';
import MarkdownIt from 'markdown-it';
import rawMd from '../../../../docs/guide/rea-expression.md?raw';

const md = new MarkdownIt();

const ReaExpressionDoc: React.FC = () => {
  const html = useMemo(() => md.render(rawMd), []);

  return (
    <div
      style={{
        maxWidth: 820,
        margin: '40px auto',
        padding: '0 24px',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        lineHeight: 1.7,
        color: '#333',
      }}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
};

export default ReaExpressionDoc;

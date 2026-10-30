import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import MarkdownIt from 'markdown-it';
import rawMdZh from '../../../../docs/guide/rea-expression.md?raw';
import rawMdEn from '../../../../docs/en/guide/rea-expression.md?raw';

const md = new MarkdownIt();

const ReaExpressionDoc: React.FC = () => {
  const { i18n } = useTranslation();
  const isEn = i18n.language?.startsWith('en');
  const html = useMemo(() => md.render(isEn ? rawMdEn : rawMdZh), [isEn]);

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

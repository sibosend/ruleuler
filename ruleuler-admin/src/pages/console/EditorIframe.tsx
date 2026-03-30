import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Spin, Result, Button } from 'antd';

interface EditorIframeProps {
  editorSrc: string;
}

const EditorIframe: React.FC<EditorIframeProps> = ({ editorSrc }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [key, setKey] = useState(0);
  const [height, setHeight] = useState<string>('100%');
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const syncHeight = useCallback(() => {
    try {
      const doc = iframeRef.current?.contentDocument ?? iframeRef.current?.contentWindow?.document;
      if (doc?.body) {
        const h = Math.max(doc.body.scrollHeight, doc.documentElement.scrollHeight);
        if (h > 0) setHeight(`${h}px`);
      }
    } catch {
      // cross-origin, 保持 100%
    }
  }, []);

  const handleLoad = useCallback(() => {
    setLoading(false);
    syncHeight();
  }, [syncHeight]);

  // 定时同步高度（iframe 内容可能动态变化）
  useEffect(() => {
    if (loading) return;
    const timer = setInterval(syncHeight, 1000);
    return () => clearInterval(timer);
  }, [loading, syncHeight]);

  const handleError = useCallback(() => {
    setLoading(false);
    setError(true);
  }, []);

  const handleRetry = useCallback(() => {
    setError(false);
    setLoading(true);
    setHeight('100%');
    setKey(k => k + 1);
  }, []);

  if (error) {
    return (
      <Result
        status="error"
        title="编辑器加载失败"
        extra={<Button type="primary" onClick={handleRetry}>重试</Button>}
      />
    );
  }

  return (
    <Spin spinning={loading} style={{ width: '100%', minHeight: '100%' }}>
      <iframe
        ref={iframeRef}
        key={key}
        src={editorSrc}
        style={{ width: '100%', minHeight: 'calc(100vh - 112px)', height, border: 'none', display: 'block' }}
        onLoad={handleLoad}
        onError={handleError}
        title="Editor"
      />
    </Spin>
  );
};

export default EditorIframe;

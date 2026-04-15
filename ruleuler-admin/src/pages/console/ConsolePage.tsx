import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Result, Button, Empty } from 'antd';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { ApartmentOutlined, ProjectOutlined } from '@ant-design/icons';
import { useTabStore } from '@/stores/tabStore';
import ResourceTree from './ResourceTree';
import EditorIframe from './EditorIframe';
import DependencyDrawer from './DependencyDrawer';
import {
  buildEditorRoute,
  parseEditorRoute,
  buildEditorSrc,
} from './fileTypeMap';
import type { RepositoryFile } from '@/api/consoleApi';

// iframe 内的老编辑器通过 window.parent.componentEvent 访问事件总线，
// 在 ruleuler-admin 中注入一个兼容 stub，将 tree_node_click 转为 CustomEvent 供 React 监听
if (!(window as unknown as Record<string, unknown>).componentEvent) {
  type Listener = (...args: unknown[]) => void;
  const listeners: Record<string, Listener[]> = {};
  const emitter = {
    emit: (event: string, ...args: unknown[]) => {
      // 通过 CustomEvent 桥接到 React
      window.dispatchEvent(new CustomEvent('iframe-event', { detail: { event, args } }));
      (listeners[event] || []).forEach(fn => fn(...args));
    },
    on: (event: string, fn: Listener) => {
      (listeners[event] = listeners[event] || []).push(fn);
    },
    removeListener: (event: string, fn: Listener) => {
      listeners[event] = (listeners[event] || []).filter(f => f !== fn);
    },
    removeAllListeners: (event?: string) => {
      if (event) delete listeners[event]; else Object.keys(listeners).forEach(k => delete listeners[k]);
    },
  };
  (window as unknown as Record<string, unknown>).componentEvent = {
    eventEmitter: emitter,
    SHOW_LOADING: 'show_loading',
    HIDE_LOADING: 'hide_loading',
    OPEN_KNOWLEDGE_TREE_DIALOG: 'open_knowledge_tree_dialog',
    TREE_NODE_CLICK: 'tree_node_click',
    TREE_DIR_NODE_CLICK: 'tree_dir_node_click',
  };
}

const DEFAULT_TREE_WIDTH = 280;
const MIN_TREE_WIDTH = 160;
const MAX_TREE_WIDTH = 600;

const ConsolePage: React.FC = () => {
  const { project } = useParams<{ project: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const tabs = useTabStore((s) => s.tabs);
  const activeKey = useTabStore((s) => s.activeKey);
  const addTab = useTabStore((s) => s.addTab);

  // ─── 拖拽分栏宽度 ──────────────────────────────────────────────
  const [treeWidth, setTreeWidth] = useState(DEFAULT_TREE_WIDTH);
  const dragging = useRef(false);

  // ─── 依赖分析 Drawer ──────────────────────────────────────────
  const [depDrawerOpen, setDepDrawerOpen] = useState(false);
  const [depDrawerPath, setDepDrawerPath] = useState<string | null>(null);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragging.current = true;
    const startX = e.clientX;
    const startW = treeWidth;

    const onMouseMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      const delta = ev.clientX - startX;
      const newW = Math.min(MAX_TREE_WIDTH, Math.max(MIN_TREE_WIDTH, startW + delta));
      setTreeWidth(newW);
    };

    const onMouseUp = () => {
      dragging.current = false;
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
    };

    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup', onMouseUp);
  }, [treeWidth]);

  useEffect(() => {
    if (!project) {
      const timer = setTimeout(() => navigate('/projects'), 2000);
      return () => clearTimeout(timer);
    }
  }, [project, navigate]);

  if (!project) {
    return (
      <Result
        icon={<ProjectOutlined style={{ color: '#1890ff' }} />}
        title="请先选择一个项目"
        subTitle="2 秒后自动跳转到项目列表…"
        extra={
          <Button type="primary" onClick={() => navigate('/projects')}>
            前往项目列表
          </Button>
        }
      />
    );
  }

  // ─── 当前路由对应的 activeKey 同步（仅处理直接 URL 访问） ──────
  const currentPath = location.pathname;
  const initialSynced = useRef(false);
  useEffect(() => {
    // 只在首次加载时同步（直接 URL 访问），后续由 handleFileSelect 驱动
    if (initialSynced.current) return;
    const parsed = parseEditorRoute(currentPath);
    if (parsed && parsed.project === project) {
      initialSynced.current = true;
      const routeKey = buildEditorRoute(parsed.project, parsed.filePath);
      const existing = useTabStore.getState().tabs.find(t => t.key === routeKey);
      if (!existing) {
        const fileName = parsed.filePath === '__package__' ? '知识包' : (parsed.filePath.split('/').pop() ?? parsed.filePath);
        addTab({ key: routeKey, label: fileName, closable: true });
      } else {
        useTabStore.getState().setActiveKey(routeKey);
      }
    }
  }, []); // 只跑一次

  // ─── 监听 iframe 编辑器发出的 tree_node_click 事件 ──────────
  useEffect(() => {
    const handler = (e: Event) => {
      const { event, args } = (e as CustomEvent).detail;
      if (event === 'tree_node_click' && args[0]) {
        const data = args[0] as { fullPath: string; name: string; path: string };
        // fullPath 是不带 dbr: 前缀的路径，如 /airport_gate_allocation_db/file.rs.xml
        const fp = data.fullPath.startsWith('/') ? data.fullPath : `/${data.fullPath}`;
        const fileName = fp.split('/').pop() ?? fp;
        const route = buildEditorRoute(project!, fp);
        addTab({ key: route, label: fileName, closable: true });
        navigate(route);
      }
    };
    window.addEventListener('iframe-event', handler);
    return () => window.removeEventListener('iframe-event', handler);
  }, [project, addTab, navigate]);

  // ─── 文件点击回调 ──────────────────────────────────────────────
  const handleFileSelect = useCallback(
    (file: RepositoryFile) => {
      // 知识包：用特殊路由标记
      if (file.type === 'resourcePackage') {
        const route = `/console/${project}/edit/__package__`;
        addTab({ key: route, label: '知识包', closable: true });
        navigate(route);
        return;
      }
      const route = buildEditorRoute(project, file.fullPath);
      const fileName = file.name;
      addTab({ key: route, label: fileName, closable: true });
      navigate(route);
    },
    [project, addTab, navigate],
  );

  // ─── 过滤当前项目的编辑器 tab ─────────────────────────────────
  const editorPrefix = `/console/${project}/edit/`;
  const editorTabs = useMemo(
    () => tabs.filter((t) => t.key.startsWith(editorPrefix)),
    [tabs, editorPrefix],
  );

  // ─── 当前活跃文件路径（用于树高亮） ────────────────────────────
  const activeFilePath = useMemo(() => {
    const parsed = parseEditorRoute(activeKey);
    if (!parsed || parsed.project !== project) return undefined;
    // filePath 不带前导 /，fullPath 带前导 /
    const fp = parsed.filePath;
    return fp.startsWith('/') ? fp : `/${fp}`;
  }, [activeKey, project]);

  // ─── 渲染 ─────────────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 112px)' }}>
      {/* 左侧资源树 */}
      <div
        style={{
          width: treeWidth,
          flexShrink: 0,
          overflow: 'auto',
          borderRight: '1px solid #f0f0f0',
        }}
      >
        <ResourceTree projectName={project} activeFilePath={activeFilePath} onFileSelect={handleFileSelect} />
      </div>

      {/* 拖拽手柄 */}
      <div
        onMouseDown={onMouseDown}
        style={{
          width: 4,
          cursor: 'col-resize',
          flexShrink: 0,
          background: dragging.current ? '#1890ff' : 'transparent',
        }}
      />

      {/* 右侧编辑器区域 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* 编辑器工具栏 */}
        {editorTabs.length > 0 && activeFilePath && (() => {
          const activeTab = editorTabs.find((t) => t.key === activeKey);
          if (!activeTab) return null;
          const parsed = parseEditorRoute(activeTab.key);
          if (!parsed || parsed.filePath === '__package__') return null;
          return (
            <div style={{
              height: 36, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '0 12px', borderBottom: '1px solid #f0f0f0', background: '#fafafa',
            }}>
              <span style={{ fontSize: 13, color: '#595959' }}>{activeTab.label}</span>
              <Button
                size="small"
                icon={<ApartmentOutlined />}
                onClick={() => {
                  const fp = parsed.filePath.startsWith('/') ? parsed.filePath : `/${parsed.filePath}`;
                  setDepDrawerPath(fp);
                  setDepDrawerOpen(true);
                }}
              >
                依赖分析
              </Button>
            </div>
          );
        })()}
        <div style={{ flex: 1, overflow: 'auto' }}>
        {editorTabs.length === 0 ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
            }}
          >
            <Empty description="请从左侧资源树选择文件" />
          </div>
        ) : (
          editorTabs.map((tab) => {
            const parsed = parseEditorRoute(tab.key);
            if (!parsed) return null;
            const isActive = tab.key === activeKey;

            // 知识包特殊处理
            if (parsed.filePath === '__package__') {
              const src = `/urule/packageeditor?file=${encodeURIComponent(parsed.project)}`;
              return (
                <div key={tab.key} style={{ width: '100%', minHeight: '100%', display: isActive ? 'block' : 'none' }}>
                  <EditorIframe editorSrc={src} />
                </div>
              );
            }

            const fileName = parsed.filePath.split('/').pop() ?? parsed.filePath;
            const src = buildEditorSrc(fileName, parsed.filePath);
            if (!src) return null;
            return (
              <div
                key={tab.key}
                style={{
                  width: '100%',
                  minHeight: '100%',
                  display: isActive ? 'block' : 'none',
                }}
              >
                <EditorIframe editorSrc={src} />
              </div>
            );
          })
        )}
        </div>
      </div>

      {/* 依赖分析 Drawer */}
      <DependencyDrawer
        open={depDrawerOpen}
        filePath={depDrawerPath}
        onClose={() => setDepDrawerOpen(false)}
        onNavigate={(path) => {
          const fileName = path.split('/').pop() ?? path;
          const route = buildEditorRoute(project!, path);
          addTab({ key: route, label: fileName, closable: true });
          navigate(route);
        }}
      />
    </div>
  );
};

export default ConsolePage;

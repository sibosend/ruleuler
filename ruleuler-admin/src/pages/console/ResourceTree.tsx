import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { EditorView, lineNumbers as cmLineNumbers } from '@codemirror/view';
import { EditorState } from '@codemirror/state';
import { xml } from '@codemirror/lang-xml';
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language';
import {
  Tree,
  Dropdown,
  Modal,
  Input,
  Select,
  message,
  Result,
  Button,
  Spin,
} from 'antd';
import type { TreeDataNode, MenuProps } from 'antd';
import {
  FolderOutlined,
  FileOutlined,
  ReloadOutlined,
  DatabaseOutlined,
  AppstoreOutlined,
  HomeOutlined,
  BookOutlined,
  TableOutlined,
  ApartmentOutlined,
  FundOutlined,
  PartitionOutlined,
  CopyrightOutlined,
  ContainerOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
import * as consoleApi from '@/api/consoleApi';
import type { RepositoryFile } from '@/api/consoleApi';
import { FILE_TYPE_EDITOR_MAP } from './fileTypeMap';
import DependencyDrawer from './DependencyDrawer';

// ─── Props ───────────────────────────────────────────────────────────

interface ResourceTreeProps {
  projectName: string;
  /** 当前选中文件的 fullPath（不含 dbr: 前缀），用于高亮和自动展开 */
  activeFilePath?: string;
  onFileSelect: (file: RepositoryFile) => void;
}

// ─── 文件类型选项（新建文件用） ─────────────────────────────────────

const FILE_TYPE_OPTIONS = Object.keys(FILE_TYPE_EDITOR_MAP).map((suffix) => ({
  label: `.${suffix}`,
  value: suffix,
}));

// ─── 节点图标 & 颜色 ─────────────────────────────────────────────

import type { FileNodeType } from '@/api/consoleApi';

/** 根据节点 type 返回图标和颜色（文件夹类） */
const FOLDER_ICON_MAP: Partial<Record<FileNodeType, { icon: React.ReactNode; color: string }>> = {
  project:         { icon: <BookOutlined />,       color: '#1677ff' },
  resourcePackage: { icon: <FolderOutlined />,     color: '#d48806' },
  resource:        { icon: <HomeOutlined />,       color: '#d48806' },
  lib:             { icon: <DatabaseOutlined />,   color: '#cf1322' },
  ruleLib:         { icon: <ProfileOutlined />,    color: '#d46b08' },
  decisionTableLib:{ icon: <TableOutlined />,      color: '#d46b08' },
  decisionTreeLib: { icon: <ApartmentOutlined />,  color: '#d46b08' },
  scorecardLib:    { icon: <FundOutlined />,       color: '#d46b08' },
  flowLib:         { icon: <PartitionOutlined />,  color: '#d46b08' },
};

/** 根据文件后缀返回图标和颜色（文件类） */
function getFileStyle(name: string): { icon: React.ReactNode; color: string } {
  if (name.endsWith('.vl.xml'))    return { icon: <ProfileOutlined />,     color: '#531dab' };
  if (name.endsWith('.cl.xml'))    return { icon: <ContainerOutlined />,  color: '#389e0d' };
  if (name.endsWith('.pl.xml'))    return { icon: <CopyrightOutlined />,  color: '#1677ff' };
  if (name.endsWith('.al.xml'))    return { icon: <AppstoreOutlined />,   color: '#eb2f96' };
  if (name.endsWith('.rs.xml'))    return { icon: <ProfileOutlined />,    color: '#8c4a1b' };
  if (name.endsWith('.rea.xml'))   return { icon: <ProfileOutlined />,    color: '#8c4a1b' };
  if (name.endsWith('.dt.xml'))    return { icon: <TableOutlined />,      color: '#08979c' };
  if (name.endsWith('.dts.xml'))   return { icon: <TableOutlined />,      color: '#08979c' };
  if (name.endsWith('.dtree.xml')) return { icon: <ApartmentOutlined />,  color: '#7c3aed' };
  if (name.endsWith('.rl.xml'))    return { icon: <PartitionOutlined />,  color: '#1d39c4' };
  if (name.endsWith('.sc'))        return { icon: <FundOutlined />,       color: '#c41d7f' };
  if (name.endsWith('.ul'))        return { icon: <FileOutlined />,       color: '#595959' };
  return { icon: <FileOutlined />, color: '#595959' };
}

// ─── 树转换：RepositoryFile → antd TreeDataNode ─────────────────────

/** 判断节点是否为文件夹（有 children 数组） */
function isFolder(node: RepositoryFile): boolean {
  return Array.isArray(node.children);
}

/**
 * 将 RepositoryFile 树递归转换为 antd TreeDataNode 数组。
 * 导出供 property test 使用。
 */
export function toTreeData(files: RepositoryFile[]): TreeDataNode[] {
  return files.map((f) => {
    const folder = isFolder(f);
    let icon: React.ReactNode;
    let color: string;

    if (folder) {
      const mapped = FOLDER_ICON_MAP[f.type];
      icon = mapped?.icon ?? <FolderOutlined />;
      color = mapped?.color ?? '#d48806';
    } else {
      const style = getFileStyle(f.name);
      icon = style.icon;
      color = style.color;
    }

    // 使用 type:fullPath:name 作为稳定 key，避免 UUID 每次刷新变化导致展开状态丢失
    const stableKey = `${f.type}:${f.fullPath}:${f.name}`;

    const node: TreeDataNode = {
      key: stableKey,
      title: <span style={{ color, whiteSpace: 'nowrap' }}>{icon} {f.name}</span>,
      isLeaf: !folder,
    };
    if (f.children && f.children.length > 0) {
      node.children = toTreeData(f.children);
    }
    return node;
  });
}

// ─── 辅助：根据 fullPath 在树中查找节点 ─────────────────────────────

/** 根据 stableKey (type:fullPath:name) 在树中查找节点 */
function findNode(
  files: RepositoryFile[],
  stableKey: string,
): RepositoryFile | null {
  for (const f of files) {
    const key = `${f.type}:${f.fullPath}:${f.name}`;
    if (key === stableKey) return f;
    if (f.children) {
      const found = findNode(f.children, stableKey);
      if (found) return found;
    }
  }
  return null;
}

// ─── 辅助：获取父路径 ───────────────────────────────────────────────

function parentPath(fullPath: string): string {
  const idx = fullPath.lastIndexOf('/');
  return idx > 0 ? fullPath.slice(0, idx) : fullPath;
}

// ─── 组件 ────────────────────────────────────────────────────────────

const ResourceTree: React.FC<ResourceTreeProps> = ({
  projectName,
  activeFilePath,
  onFileSelect,
}) => {
  const { t } = useTranslation();
  const [treeFiles, setTreeFiles] = useState<RepositoryFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 右键菜单目标节点
  const [contextNode, setContextNode] = useState<RepositoryFile | null>(null);

  // 新建文件 Modal
  const [createFileOpen, setCreateFileOpen] = useState(false);
  const [newFileName, setNewFileName] = useState('');
  const [newFileType, setNewFileType] = useState(FILE_TYPE_OPTIONS[0]?.value ?? '');
  const [fileTypeLocked, setFileTypeLocked] = useState(false);

  // 新建文件夹 Modal
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');

  // 重命名 Modal
  const [renameOpen, setRenameOpen] = useState(false);
  const [renameName, setRenameName] = useState('');
  const [renameExt, setRenameExt] = useState(''); // 扩展名（含点号），文件夹为空

  // 查看源码 Modal
  const [sourceOpen, setSourceOpen] = useState(false);
  const [sourceContent, setSourceContent] = useState('');
  const sourceViewRef = useRef<EditorView | null>(null);

  const sourceEditorRef = useCallback((node: HTMLDivElement | null) => {
    if (sourceViewRef.current) {
      sourceViewRef.current.destroy();
      sourceViewRef.current = null;
    }
    if (!node) return;
    const view = new EditorView({
      state: EditorState.create({
        doc: sourceContent,
        extensions: [
          xml(),
          cmLineNumbers(),
          syntaxHighlighting(defaultHighlightStyle),
          EditorView.editable.of(false),
          EditorView.theme({ '&': { fontSize: '12px', maxHeight: 'calc(80vh - 100px)', overflow: 'auto' } }),
        ],
      }),
      parent: node,
    });
    sourceViewRef.current = view;
  }, [sourceContent]);

  // 查看版本信息 Modal
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versionList, setVersionList] = useState<consoleApi.VersionFile[]>([]);

  // 搜索
  const [searchValue, setSearchValue] = useState('');

  // 依赖分析 Drawer
  const [dependencyOpen, setDependencyOpen] = useState(false);
  const [dependencyPath, setDependencyPath] = useState<string | null>(null);

  // 剪切/复制缓存
  const clipboardRef = useRef<{ file: RepositoryFile; mode: 'copy' | 'cut' } | null>(null);

  // ─── 加载树数据 ─────────────────────────────────────────────────

  const loadTree = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await consoleApi.loadProjects(projectName);
      const root = resp.repo.rootFile;
      // 单项目模式：直接取 project 节点作为根
      const projectNode = (root.children ?? []).find(c => c.type === 'project');
      setTreeFiles(projectNode ? [projectNode] : root.children ?? []);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : t('console.loadTreeFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [projectName, t]);

  useEffect(() => {
    loadTree();
  }, [projectName]); // eslint-disable-line react-hooks/exhaustive-deps

  // ─── antd TreeDataNode ─────────────────────────────────────────

  const treeData = useMemo(() => toTreeData(treeFiles), [treeFiles]);

  // ─── 搜索过滤：只保留匹配节点及其祖先 ─────────────────────────

  const filteredTreeData = useMemo(() => {
    if (!searchValue) return treeData;
    const kw = searchValue.toLowerCase();
    function filterNodes(nodes: TreeDataNode[]): TreeDataNode[] {
      const result: TreeDataNode[] = [];
      for (const node of nodes) {
        // 从 title 中提取文本：title 是 <span>...<icon> name</span>
        const titleText = (() => {
          const key = String(node.key);
          // stableKey 格式 type:fullPath:name，取最后的 name
          const parts = key.split(':');
          return parts.length >= 3 ? parts.slice(2).join(':') : key;
        })();
        const selfMatch = titleText.toLowerCase().includes(kw);
        const filteredChildren = node.children ? filterNodes(node.children) : [];
        if (selfMatch || filteredChildren.length > 0) {
          result.push({
            ...node,
            ...(node.children
              ? { children: filteredChildren.length > 0 ? filteredChildren : undefined, isLeaf: filteredChildren.length === 0 }
              : {}),
          } as TreeDataNode);
        }
      }
      return result;
    }
    return filterNodes(treeData);
  }, [treeData, searchValue]);

  // ─── 搜索过滤 & 当前文件展开 ─────────────────────────────────────

  /** 根据 fullPath 找到节点 stableKey 及其所有祖先 stableKey */
  const findAncestorKeys = useCallback((targetPath: string | undefined, files: RepositoryFile[]): string[] => {
    if (!targetPath) return [];
    const result: string[] = [];
    const walk = (nodes: RepositoryFile[], ancestors: string[]): boolean => {
      for (const f of nodes) {
        const key = `${f.type}:${f.fullPath}:${f.name}`;
        if (f.fullPath === targetPath && !isFolder(f)) {
          result.push(...ancestors, key);
          return true;
        }
        if (f.children && walk(f.children, [...ancestors, key])) return true;
      }
      return false;
    };
    walk(files, []);
    return result;
  }, []);

  /** 当前选中文件的节点 stableKey */
  const selectedKeys = useMemo(() => {
    if (!activeFilePath) return undefined;
    const keys = findAncestorKeys(activeFilePath, treeFiles);
    return keys.length > 0 ? [keys[keys.length - 1] as string] : undefined;
  }, [activeFilePath, treeFiles, findAncestorKeys]);

  /** 受控展开 keys */
  const [controlledExpandedKeys, setControlledExpandedKeys] = useState<React.Key[]>([]);

  // 树数据首次加载后，默认展开到分类节点层级（project → resource）
  const projectNameRef = useRef(projectName);
  const defaultExpanded = useRef(false);
  useEffect(() => {
    if (projectName !== projectNameRef.current) {
      projectNameRef.current = projectName;
      defaultExpanded.current = false;
    }
    if (defaultExpanded.current || treeFiles.length === 0) return;
    defaultExpanded.current = true;
    const keys: string[] = [];
    const collectKeys = (nodes: RepositoryFile[], depth: number) => {
      for (const f of nodes) {
        if (!Array.isArray(f.children)) continue;
        keys.push(`${f.type}:${f.fullPath}:${f.name}`);
        if (depth < 1 && f.children) {
          collectKeys(f.children, depth + 1);
        }
      }
    };
    collectKeys(treeFiles, 0);
    setControlledExpandedKeys(keys);
  }, [treeFiles]);

  // 搜索或 activeFilePath 变化时，自动合并需要展开的 key
  useEffect(() => {
    const keys: string[] = [];
    if (activeFilePath) {
      keys.push(...findAncestorKeys(activeFilePath, treeFiles));
    }
    if (searchValue) {
      const walk = (files: RepositoryFile[], parentKeys: string[]): boolean => {
        let hasMatch = false;
        for (const f of files) {
          const key = `${f.type}:${f.fullPath}:${f.name}`;
          const match = f.name.toLowerCase().includes(searchValue.toLowerCase());
          let childMatch = false;
          if (f.children) {
            childMatch = walk(f.children, [...parentKeys, key]);
          }
          if (match || childMatch) {
            hasMatch = true;
            keys.push(...parentKeys, key);
          }
        }
        return hasMatch;
      };
      walk(treeFiles, []);
    }
    if (keys.length > 0) {
      setControlledExpandedKeys(prev => [...new Set([...prev, ...keys])]);
    }
  }, [searchValue, activeFilePath, treeFiles, findAncestorKeys]);

  const handleExpand = useCallback((keys: React.Key[]) => {
    setControlledExpandedKeys(keys);
  }, []);

  // ─── 点击节点 ──────────────────────────────────────────────────

  const handleSelect = useCallback(
    (_: unknown, info: { node: TreeDataNode }) => {
      const key = String(info.node.key);
      const file = findNode(treeFiles, key);
      if (file && !isFolder(file)) {
        onFileSelect(file);
      }
    },
    [treeFiles, onFileSelect],
  );

  // ─── 双击节点：文件夹切换展开/折叠 ────────────────────────────

  const handleDoubleClick = useCallback(
    (_: React.MouseEvent, node: TreeDataNode) => {
      if (node.isLeaf) return;
      const key = node.key as React.Key;
      setControlledExpandedKeys(prev =>
        prev.includes(key) ? prev.filter(k => k !== key) : [...prev, key],
      );
    },
    [],
  );

  // ─── 文件操作后刷新 ───────────────────────────────────────────

  const afterOp = useCallback(
    async (op: () => Promise<void>, successMsg: string, expandKey?: string) => {
      try {
        await op();
        message.success(successMsg);
        await loadTree();
        if (expandKey) {
          setControlledExpandedKeys(prev => [...new Set([...prev, expandKey])]);
        }
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : t('console.loadTreeFailed');
        message.error(msg);
      }
    },
    [loadTree, t],
  );

  // ─── 新建文件 ─────────────────────────────────────────────────

  const handleCreateFile = useCallback(() => {
    if (!contextNode || !newFileName.trim() || !newFileType) return;
    const path = `${contextNode.fullPath}/${newFileName.trim()}.${newFileType}`;
    afterOp(() => consoleApi.createFile(path, newFileType), t('console.fileCreated'));
    setCreateFileOpen(false);
    setNewFileName('');
  }, [contextNode, newFileName, newFileType, afterOp, t]);

  // ─── 新建文件夹 ───────────────────────────────────────────────

  const handleCreateFolder = useCallback(() => {
    if (!contextNode || !newFolderName.trim()) return;
    const fullFolderName = `${contextNode.fullPath}/${newFolderName.trim()}`;
    const parentKey = `${contextNode.type}:${contextNode.fullPath}:${contextNode.name}`;
    // 推断 folderType：如果在分类 lib 上创建，type 就是 folderType；如果在已有 folder 上创建，继承其 folderType
    const folderType = contextNode.type === 'folder' ? (contextNode.folderType ?? contextNode.type) : contextNode.type;
    afterOp(() => consoleApi.createFolder(fullFolderName, folderType), t('console.folderCreated'), parentKey);
    setCreateFolderOpen(false);
    setNewFolderName('');
  }, [contextNode, newFolderName, afterOp, t]);

  // ─── 删除 ─────────────────────────────────────────────────────

  const handleDelete = useCallback(() => {
    if (!contextNode) return;
    const folder = isFolder(contextNode);
    Modal.confirm({
      title: `${t('common.confirm')}${t('common.delete')} "${contextNode.name}"？`,
      content: folder ? t('console.deleteFolderContent') : undefined,
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: () =>
        afterOp(
          () => consoleApi.deleteFile(contextNode.fullPath),
          t('console.deleted'),
        ),
    });
  }, [contextNode, afterOp, t]);

  // ─── 重命名 ───────────────────────────────────────────────────

  const handleRename = useCallback(() => {
    if (!contextNode || !renameName.trim()) return;
    const parent = parentPath(contextNode.fullPath);
    const newPath = `${parent}/${renameName.trim()}${renameExt}`;
    afterOp(
      () => consoleApi.fileRename(contextNode.fullPath, newPath),
      t('console.renamed'),
    );
    setRenameOpen(false);
    setRenameName('');
    setRenameExt('');
  }, [contextNode, renameName, renameExt, afterOp, t]);

  // ─── 右键菜单（按节点 type 区分，与原 iframe action.js 一致） ────

  /** 文件节点通用菜单 (buildFileContextMenu)，与 ruleuler-console-js 一致 */
  function buildFileMenu(): MenuProps['items'] {
    return [
      { key: 'viewSource', label: t('console.viewSource') },
      { key: 'viewVersions', label: t('console.versionInfo') },
      { key: 'dependency', label: t('console.dependencyAnalysis') },
      { type: 'divider' },
      { key: 'delete', label: t('console.menuDeleteFile'), danger: true },
      { key: 'rename', label: t('console.menuRenameFile') },
      { key: 'copy', label: t('console.menuCopyFile') },
      { key: 'cut', label: t('console.menuCutFile') },
      { key: 'lock', label: t('console.menuLockFile') },
      { key: 'unlock', label: t('console.menuUnlockFile') },
    ];
  }

  /** folder/all 节点菜单 (buildFullContextMenu)，根据 folderType 决定可添加的文件类型 */
  function buildFullMenu(isFolderNode: boolean, folderType: string | null): MenuProps['items'] {
    const ft = folderType ?? 'all';
    const items: MenuProps['items'] = [
      { key: 'createFolder', label: isFolderNode ? t('console.menuAddSubDir') : t('console.menuAddDir') },
    ];
    if (ft === 'all' || ft === 'lib') {
      items.push(
        { key: 'createFile:vl.xml', label: t('console.menuAddVarLib') },
        { key: 'createFile:cl.xml', label: t('console.menuAddConstLib') },
        { key: 'createFile:pl.xml', label: t('console.menuAddParamLib') },
        { key: 'createFile:al.xml', label: t('console.menuAddActionLib') },
      );
    }
    if (ft === 'all' || ft === 'ruleLib') {
      items.push(
        { key: 'createFile:rs.xml', label: t('console.menuAddRuleSet') },
        { key: 'createFile:ul', label: t('console.menuAddScriptRuleSet') },
        { key: 'createFile:rea.xml', label: t('console.menuAddRea') },
      );
    }
    if (ft === 'all' || ft === 'decisionTableLib') {
      items.push({ key: 'createFile:dt.xml', label: t('console.menuAddDecisionTable') });
    }
    if (ft === 'all' || ft === 'decisionTreeLib') {
      items.push({ key: 'createFile:dtree.xml', label: t('console.menuAddDecisionTree') });
    }
    if (ft === 'all' || ft === 'scorecardLib') {
      items.push({ key: 'createFile:sc', label: t('console.menuAddScorecard') });
    }
    if (ft === 'all' || ft === 'flowLib') {
      items.push({ key: 'createFile:rl.xml', label: t('console.menuAddFlow') });
    }
    if (isFolderNode) {
      items.push(
        { type: 'divider' },
        { key: 'delete', label: t('common.delete'), danger: true },
        { key: 'rename', label: t('console.menuRenameDir') },
        { key: 'lock', label: t('console.menuLockDir') },
        { key: 'unlock', label: t('console.menuUnlockDir') },
      );
    }
    items.push({ key: 'paste', label: t('console.menuPasteFile') });
    return items;
  }

  /** 根据节点 type 构建右键菜单 */
  function getContextMenu(node: RepositoryFile): MenuProps['items'] {
    switch (node.type) {
      case 'root':
        return []; // 单项目模式下不需要 root 菜单
      case 'project':
        return [
          { key: 'rename', label: t('console.menuRenameProject') },
          { key: 'delete', label: t('console.menuDeleteProject'), danger: true },
        ];
      case 'resource':
        return []; // 无菜单
      case 'resourcePackage':
        return []; // 知识包无菜单
      case 'lib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:vl.xml', label: t('console.menuAddVarLib') },
          { key: 'createFile:cl.xml', label: t('console.menuAddConstLib') },
          { key: 'createFile:pl.xml', label: t('console.menuAddParamLib') },
          { key: 'createFile:al.xml', label: t('console.menuAddActionLib') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'ruleLib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:rs.xml', label: t('console.menuAddRuleSet') },
          { key: 'createFile:ul', label: t('console.menuAddScriptRuleSet') },
          { key: 'createFile:rea.xml', label: t('console.menuAddRea') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'decisionTableLib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:dt.xml', label: t('console.menuAddDecisionTable') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'decisionTreeLib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:dtree.xml', label: t('console.menuAddDecisionTree') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'flowLib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:rl.xml', label: t('console.menuAddFlow') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'scorecardLib':
        return [
          { key: 'createFolder', label: t('console.menuAddDir') },
          { key: 'createFile:sc', label: t('console.menuAddScorecard') },
          { key: 'paste', label: t('console.menuPasteFile') },
        ];
      case 'all':
        return buildFullMenu(false, null);
      case 'folder':
        return buildFullMenu(true, node.folderType);
      case 'ul':
        // ul 文件：去掉"查看源码"（原来 splice(0,1)），与 ruleuler-console-js 一致
        return [
          { key: 'viewVersions', label: t('console.versionInfo') },
          { key: 'delete', label: t('console.menuDeleteFile'), danger: true },
          { key: 'rename', label: t('console.menuRenameFile') },
          { key: 'copy', label: t('console.menuCopyFile') },
          { key: 'cut', label: t('console.menuCutFile') },
          { key: 'lock', label: t('console.menuLockFile') },
          { key: 'unlock', label: t('console.menuUnlockFile') },
        ];
      default:
        // 所有其他文件类型：rule, rea, action, parameter, constant, variable,
        // decisionTable, scriptDecisionTable, decisionTree, flow, scorecard
        return buildFileMenu();
    }
  }

  const handleMenuClick = useCallback(
    (info: { key: string }) => {
      // 处理 createFile:xxx 格式（直接指定文件类型）
      if (info.key.startsWith('createFile:')) {
        const fileType = info.key.slice('createFile:'.length);
        setNewFileName('');
        setNewFileType(fileType);
        setFileTypeLocked(true);
        setCreateFileOpen(true);
        return;
      }
      switch (info.key) {
        case 'createFile':
          setNewFileName('');
          setNewFileType(FILE_TYPE_OPTIONS[0]?.value ?? '');
          setFileTypeLocked(false);
          setCreateFileOpen(true);
          break;
        case 'createFolder':
          setNewFolderName('');
          setCreateFolderOpen(true);
          break;
        case 'delete':
          handleDelete();
          break;
        case 'rename': {
          const name = contextNode?.name ?? '';
          const dotIdx = name.indexOf('.');
          if (dotIdx === -1 || isFolder(contextNode!)) {
            // 文件夹 / 项目 / 无扩展名：编辑全名
            setRenameName(name);
            setRenameExt('');
          } else {
            // 文件：分离文件名和扩展名，与 ruleuler-console-js 一致
            setRenameName(name.substring(0, dotIdx));
            setRenameExt(name.substring(dotIdx));
          }
          setRenameOpen(true);
          break;
        }
        case 'copy':
          clipboardRef.current = contextNode ? { file: contextNode, mode: 'copy' } : null;
          message.info(t('console.copied'));
          break;
        case 'cut':
          clipboardRef.current = contextNode ? { file: contextNode, mode: 'cut' } : null;
          message.info(t('console.cut'));
          break;
        case 'lock':
          if (contextNode) {
            afterOp(() => consoleApi.lockFile(contextNode.fullPath), t('console.locked'));
          }
          break;
        case 'unlock':
          if (contextNode) {
            afterOp(() => consoleApi.unlockFile(contextNode.fullPath), t('console.unlocked'));
          }
          break;
        case 'viewSource':
          if (contextNode) {
            consoleApi.fileSource(contextNode.fullPath).then((content) => {
              setSourceContent(content);
              setSourceOpen(true);
            }).catch(() => message.error(t('console.getSourceFailed')));
          }
          break;
        case 'viewVersions':
          if (contextNode) {
            consoleApi.fileVersions(contextNode.fullPath).then((list) => {
              setVersionList(list);
              setVersionsOpen(true);
            }).catch(() => message.error(t('console.getVersionFailed')));
          }
          break;
        case 'dependency':
          if (contextNode) {
            setDependencyPath(contextNode.fullPath);
            setDependencyOpen(true);
          }
          break;
        case 'paste': {
          const clip = clipboardRef.current;
          if (!clip) {
            message.warning(t('console.noFileToPaste'));
            break;
          }
          if (!contextNode) break;
          const targetDir = contextNode.fullPath;
          const newFullPath = `${targetDir}/${clip.file.name}`;
          if (clip.file.fullPath === newFullPath) {
            message.warning(t('console.noFileToPaste'));
            break;
          }
          const action = clip.mode === 'cut' ? t('console.confirmMove') : t('console.copiedDone');
          Modal.confirm({
            title: `${t('common.confirm')}${action}`,
            content: `${action}${clip.file.name} → ${targetDir}？`,
            okText: t('common.confirm'),
            cancelText: t('common.cancel'),
            onOk: () => {
              const op = clip.mode === 'cut'
                ? () => consoleApi.fileRename(clip.file.fullPath, newFullPath)
                : () => consoleApi.copyFile(clip.file.fullPath, newFullPath);
              clipboardRef.current = null;
              afterOp(op, clip.mode === 'cut' ? t('console.moved') : t('console.copiedDone'));
            },
          });
          break;
        }
      }
    },
    [contextNode, handleDelete, afterOp, t],
  );

  // ─── 渲染 ─────────────────────────────────────────────────────

  if (error) {
    return (
      <Result
        status="error"
        title={t('console.loadTreeFailed')}
        subTitle={error}
        extra={
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={loadTree}
          >
            {t('common.retry')}
          </Button>
        }
      />
    );
  }

  return (
    <Spin spinning={loading}>
      <style>{`
        .compact-tree .ant-tree-treenode { padding: 1px 0 !important; margin: 0 !important; }
        .compact-tree .ant-tree-node-content-wrapper { line-height: 26px !important; min-height: 26px !important; padding: 0 2px !important; }
        .compact-tree .ant-tree-switcher { width: 20px !important; height: 26px !important; line-height: 26px !important; }
        .compact-tree .ant-tree-indent-unit { width: 20px !important; }
        .compact-tree { font-size: 14px !important; font-family: "Microsoft YaHei UI", "Microsoft YaHei", sans-serif !important; }
        .compact-tree .ant-tree-list-holder-inner { gap: 0 !important; }
      `}</style>
      <Input.Search
        placeholder={t('console.searchFiles')}
        allowClear
        onChange={(e) => setSearchValue(e.target.value)}
        style={{ marginBottom: 8, padding: '0 4px' }}
        size="small"
      />
      <Tree
        showLine={{ showLeafIcon: false }}
        blockNode
        treeData={filteredTreeData}
        expandedKeys={controlledExpandedKeys}
        onExpand={handleExpand}
        {...(selectedKeys ? { selectedKeys } : {})}
        onSelect={handleSelect}
        onDoubleClick={handleDoubleClick}
        className="compact-tree"
        titleRender={(nodeData) => {
          const key = String(nodeData.key);
          const file = findNode(treeFiles, key);
          const menuItems = file ? getContextMenu(file) : [];
          if (!menuItems || menuItems.length === 0) {
            return <span style={{ userSelect: 'none' }}>{nodeData.title as React.ReactNode}</span>;
          }
          return (
            <Dropdown
              menu={{
                items: menuItems,
                onClick: handleMenuClick,
              }}
              trigger={['contextMenu']}
              onOpenChange={(open) => {
                if (open && file) setContextNode(file);
              }}
            >
              <span style={{ userSelect: 'none' }}>{nodeData.title as React.ReactNode}</span>
            </Dropdown>
          );
        }}
      />

      {/* 新建文件 Modal */}
      <Modal
        title={t('console.newFile')}
        open={createFileOpen}
        onOk={handleCreateFile}
        onCancel={() => setCreateFileOpen(false)}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Select
            value={newFileType}
            onChange={setNewFileType}
            options={FILE_TYPE_OPTIONS}
            style={{ width: '100%' }}
            placeholder={t('console.selectFileType')}
            disabled={fileTypeLocked}
          />
          <Input
            value={newFileName}
            onChange={(e) => setNewFileName(e.target.value)}
            placeholder={t('console.enterFileName')}
            onPressEnter={handleCreateFile}
          />
        </div>
      </Modal>

      {/* 新建文件夹 Modal */}
      <Modal
        title={t('console.newFolder')}
        open={createFolderOpen}
        onOk={handleCreateFolder}
        onCancel={() => setCreateFolderOpen(false)}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <Input
          value={newFolderName}
          onChange={(e) => setNewFolderName(e.target.value)}
          placeholder={t('console.enterFolderName')}
          onPressEnter={handleCreateFolder}
        />
      </Modal>

      {/* 重命名 Modal */}
      <Modal
        title={t('console.rename')}
        open={renameOpen}
        onOk={handleRename}
        onCancel={() => setRenameOpen(false)}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <label>{t('common.name')}</label>
          <Input
            value={renameName}
            onChange={(e) => setRenameName(e.target.value)}
            placeholder={t('console.enterNewName')}
            onPressEnter={handleRename}
            addonAfter={renameExt || undefined}
          />
        </div>
      </Modal>

      {/* 查看源码 Modal */}
      <Modal
        title={t('console.viewSource')}
        open={sourceOpen}
        onCancel={() => setSourceOpen(false)}
        footer={null}
        width={720}
        destroyOnClose
      >
        <div ref={sourceEditorRef} />
      </Modal>

      {/* 查看版本信息 Modal */}
      <Modal
        title={t('console.versionInfo')}
        open={versionsOpen}
        onCancel={() => setVersionsOpen(false)}
        footer={null}
        width={600}
      >
        {versionList.length === 0 ? (
          <p>{t('console.noVersionInfo')}</p>
        ) : (
          <div style={{ maxHeight: 400, overflow: 'auto' }}>
            {versionList.map((v, i) => (
              <div key={i} style={{ padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <div>{v.name}</div>
                <div style={{ color: '#999', fontSize: 12 }}>{v.createDate} {v.comment && `- ${v.comment}`}</div>
              </div>
            ))}
          </div>
        )}
      </Modal>

      {/* 依赖分析 Drawer */}
      <DependencyDrawer
        open={dependencyOpen}
        filePath={dependencyPath}
        onClose={() => setDependencyOpen(false)}
        onNavigate={(path) => onFileSelect({ fullPath: path, name: path.split('/').pop() ?? path, type: 'all' } as RepositoryFile)}
      />
    </Spin>
  );
};

export default ResourceTree;

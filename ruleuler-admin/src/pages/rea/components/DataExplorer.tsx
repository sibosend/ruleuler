import React from 'react';
import { Tree, Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';
import type { DataNode } from 'antd/es/tree';
import type { LibraryData } from '../lib/expressionParser';

export interface DataExplorerProps {
  libraries: LibraryData;
  onInsert: (text: string) => void;
}

/** 构建引用文本：变量 → `类别.变量名`，参数 → `参数.参数名` */
export function buildRefText(
  kind: 'variable' | 'parameter',
  category: string,
  name: string,
): string {
  if (kind === 'parameter') return `${i18n.t('rea.paramLabel')}.${name}`;
  return `${category}.${name}`;
}

/** 将 LibraryData 转为 Ant Design Tree 的 DataNode 数组 */
export function buildTreeData(libs: LibraryData): DataNode[] {
  const nodes: DataNode[] = [];

  // 变量库
  for (const cat of libs.variables) {
    nodes.push({
      key: `var-${cat.name}`,
      title: cat.name,
      selectable: false,
      children: cat.variables.map((v) => ({
        key: `var-${cat.name}-${v.name}`,
        title: `${v.label || v.name} (${v.type || 'String'})`,
        isLeaf: true,
        // 存储元数据供点击使用
        _meta: { kind: 'variable' as const, category: cat.name, name: v.name },
      })) as DataNode[],
    });
  }

  // 参数库
  if (libs.parameters.length > 0) {
    nodes.push({
      key: 'param-root',
      title: i18n.t('rea.paramLabel'),
      selectable: false,
      children: libs.parameters.map((p) => ({
        key: `param-${p.name}`,
        title: `${p.label || p.name} (${p.type || 'String'})`,
        isLeaf: true,
        _meta: { kind: 'parameter' as const, category: i18n.t('rea.paramLabel'), name: p.name },
      })) as DataNode[],
    });
  }

  return nodes;
};


const DataExplorer: React.FC<DataExplorerProps> = ({ libraries, onInsert }) => {
  const { t } = useTranslation();
  const treeData = buildTreeData(libraries);

  return (
    <Tree
      treeData={treeData}
      defaultExpandAll
      blockNode
      titleRender={(node) => {
        const n = node as DataNode & { _meta?: { kind: 'variable' | 'parameter'; category: string; name: string } };
        if (!n._meta) return <>{node.title as string}</>;
        return (
          <span className="data-explorer-leaf">
            {node.title as string}
            <Button
              className="data-explorer-insert-btn"
              type="link"
              size="small"
              icon={<PlusOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                const meta = n._meta!;
                onInsert(buildRefText(meta.kind, meta.category, meta.name));
              }}
            >
              {t('rea.insert')}
            </Button>
          </span>
        );
      }}
      style={{ fontSize: 12 }}
    />
  );
};

export default DataExplorer;

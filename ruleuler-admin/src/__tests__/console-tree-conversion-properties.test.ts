/**
 * Feature: console-tree-refactor, Property 1: 树转换保持节点数量和层级
 * Validates: Requirements 1.2
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import type { TreeDataNode } from 'antd';
import { toTreeData } from '@/pages/console/ResourceTree';
import type { RepositoryFile, FileNodeType } from '@/api/consoleApi';

// ─── FileNodeType 枚举值 ─────────────────────────────────────────────

const FILE_NODE_TYPES: FileNodeType[] = [
  'root', 'project', 'resource', 'resourcePackage',
  'lib', 'action', 'parameter', 'constant', 'variable',
  'ruleLib', 'decisionTableLib', 'decisionTreeLib', 'scorecardLib', 'flowLib',
  'scorecard', 'rule', 'rea', 'ul',
  'decisionTable', 'scriptDecisionTable', 'decisionTree', 'flow',
  'all', 'folder',
];

// ─── Arbitrary: 生成合法的 RepositoryFile 树 ─────────────────────────

const fileNodeTypeArb = fc.constantFrom(...FILE_NODE_TYPES);

const repoFileArb: fc.Arbitrary<RepositoryFile> = fc.letrec((tie) => ({
  tree: fc.oneof(
    // 文件节点（叶子，children 为 null）
    fc.record({
      id: fc.uuid(),
      name: fc.stringMatching(/^[a-zA-Z0-9\u4e00-\u9fa5_-]{1,10}\.[a-z]{1,4}$/),
      fullPath: fc.stringMatching(/^\/[a-zA-Z0-9\u4e00-\u9fa5/_-]{1,30}$/),
      type: fileNodeTypeArb,
      folderType: fc.constant(null),
      lock: fc.boolean(),
      lockInfo: fc.constant(null),
      storageType: fc.constant(null),
      children: fc.constant(null),
    }),
    // 文件夹节点（children 为数组）
    fc.record({
      id: fc.uuid(),
      name: fc.stringMatching(/^[a-zA-Z0-9\u4e00-\u9fa5_-]{1,10}$/),
      fullPath: fc.stringMatching(/^\/[a-zA-Z0-9\u4e00-\u9fa5/_-]{1,30}$/),
      type: fileNodeTypeArb,
      folderType: fc.oneof(fileNodeTypeArb, fc.constant(null)),
      lock: fc.boolean(),
      lockInfo: fc.constant(null),
      storageType: fc.constant(null),
      children: fc.array(tie('tree') as fc.Arbitrary<RepositoryFile>, { maxLength: 3 }),
    }),
  ),
})).tree as fc.Arbitrary<RepositoryFile>;

const repoFileArrayArb = fc.array(repoFileArb, { minLength: 0, maxLength: 5 });

// ─── 辅助函数 ────────────────────────────────────────────────────────

function countNodes(files: RepositoryFile[]): number {
  let count = 0;
  for (const f of files) {
    count += 1;
    if (f.children) {
      count += countNodes(f.children);
    }
  }
  return count;
}

function countTreeDataNodes(nodes: TreeDataNode[]): number {
  let count = 0;
  for (const n of nodes) {
    count += 1;
    if (n.children) {
      count += countTreeDataNodes(n.children);
    }
  }
  return count;
}

/** 收集每个节点的直接子节点数（按遍历顺序） */
function collectChildCounts(files: RepositoryFile[]): number[] {
  const counts: number[] = [];
  for (const f of files) {
    counts.push(f.children ? f.children.length : 0);
    if (f.children) {
      counts.push(...collectChildCounts(f.children));
    }
  }
  return counts;
}

function collectTreeDataChildCounts(nodes: TreeDataNode[]): number[] {
  const counts: number[] = [];
  for (const n of nodes) {
    counts.push(n.children ? n.children.length : 0);
    if (n.children) {
      counts.push(...collectTreeDataChildCounts(n.children));
    }
  }
  return counts;
}

// ─── 属性测试 ────────────────────────────────────────────────────────

describe('Property 1: 树转换保持节点数量和层级', () => {
  it('转换后总节点数等于原始树的总节点数', () => {
    fc.assert(
      fc.property(repoFileArrayArb, (files) => {
        const treeData = toTreeData(files);
        expect(countTreeDataNodes(treeData)).toBe(countNodes(files));
      }),
      { numRuns: 100 },
    );
  });

  it('每个节点的子节点数量与原始节点一致', () => {
    fc.assert(
      fc.property(repoFileArrayArb, (files) => {
        const treeData = toTreeData(files);
        const originalCounts = collectChildCounts(files);
        const convertedCounts = collectTreeDataChildCounts(treeData);
        expect(convertedCounts).toEqual(originalCounts);
      }),
      { numRuns: 100 },
    );
  });
});

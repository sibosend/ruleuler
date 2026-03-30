/**
 * Feature: ruleset-jql-editor
 * Property 4: DataExplorer 树结构完整性
 * Property 5: 引用文本格式正确性
 * Validates: Requirements 4.4, 4.5, 4.6, 4.7, 4.9
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { buildTreeData, buildRefText } from '@/pages/rea/components/DataExplorer';
import type { LibraryData } from '@/pages/rea/lib/expressionParser';

// ─── 生成器 ───

/** 中文+英文混合标识符 */
const identArb = fc
  .array(
    fc.oneof(
      fc.integer({ min: 0x4e00, max: 0x9fff }).map((c) => String.fromCharCode(c)),
      fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
    ),
    { minLength: 1, maxLength: 6 },
  )
  .map((arr) => arr.join(''));

const datatypeArb = fc.constantFrom('String', 'Integer', 'Double', 'Boolean', 'Date');

/** 单个变量 */
const variableArb = fc.record({
  name: identArb,
  label: identArb,
  type: datatypeArb,
});

/** 变量类别（1-5 个变量） */
const variableCategoryArb = fc.record({
  name: identArb,
  variables: fc.array(variableArb, { minLength: 1, maxLength: 5 }),
});

/** 参数 */
const parameterArb = fc.record({
  name: identArb,
  label: identArb,
  type: datatypeArb,
});

/** LibraryData：1-5 个变量类别，0-5 个参数 */
const libraryDataArb: fc.Arbitrary<LibraryData> = fc.record({
  variables: fc.array(variableCategoryArb, { minLength: 1, maxLength: 5 }),
  parameters: fc.array(parameterArb, { minLength: 0, maxLength: 5 }),
});

// ============================================================
// Property 4: DataExplorer 树结构完整性
// Validates: Requirements 4.4, 4.5, 4.6, 4.7
// ============================================================
describe('Property 4: DataExplorer 树结构完整性', () => {
  it('一级节点数 === 变量类别数 + (参数数>0 ? 1 : 0)', () => {
    fc.assert(
      fc.property(libraryDataArb, (libs) => {
        const tree = buildTreeData(libs);
        const expectedTopLevel =
          libs.variables.length + (libs.parameters.length > 0 ? 1 : 0);
        expect(tree.length).toBe(expectedTopLevel);
      }),
      { numRuns: 100 },
    );
  });

  it('每个变量类别的子节点数 === 该类别的变量数', () => {
    fc.assert(
      fc.property(libraryDataArb, (libs) => {
        const tree = buildTreeData(libs);
        for (let i = 0; i < libs.variables.length; i++) {
          const cat = libs.variables[i];
          const node = tree[i];
          expect(node.children?.length).toBe(cat.variables.length);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('参数节点的子节点数 === 参数数（当参数存在时）', () => {
    fc.assert(
      fc.property(libraryDataArb, (libs) => {
        const tree = buildTreeData(libs);
        if (libs.parameters.length > 0) {
          const paramNode = tree[tree.length - 1];
          expect(paramNode.children?.length).toBe(libs.parameters.length);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('无参数时不生成参数节点', () => {
    const noParamLibs: fc.Arbitrary<LibraryData> = fc.record({
      variables: fc.array(variableCategoryArb, { minLength: 1, maxLength: 5 }),
      parameters: fc.constant([]),
    });

    fc.assert(
      fc.property(noParamLibs, (libs) => {
        const tree = buildTreeData(libs);
        expect(tree.length).toBe(libs.variables.length);
        // 确认没有 key 为 param-root 的节点
        expect(tree.every((n) => n.key !== 'param-root')).toBe(true);
      }),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Property 5: 引用文本格式正确性
// Validates: Requirements 4.9
// ============================================================
describe('Property 5: 引用文本格式正确性', () => {
  it('变量引用格式: ${category}.${name}', () => {
    fc.assert(
      fc.property(identArb, identArb, (cat, name) => {
        const ref = buildRefText('variable', cat, name);
        expect(ref).toBe(`${cat}.${name}`);
      }),
      { numRuns: 100 },
    );
  });

  it('参数引用格式: 参数.${name}', () => {
    fc.assert(
      fc.property(identArb, (name) => {
        const ref = buildRefText('parameter', '参数', name);
        expect(ref).toBe(`参数.${name}`);
      }),
      { numRuns: 100 },
    );
  });
});

/**
 * Feature: console-tree-refactor, Property 2: 文件类型到编辑器 URL 映射正确性
 * Validates: Requirements 3.1, 3.3
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  FILE_TYPE_EDITOR_MAP,
  getEditorUrl,
  buildEditorSrc,
} from '@/pages/console/fileTypeMap';

const KNOWN_SUFFIXES = Object.keys(FILE_TYPE_EDITOR_MAP);

/** 生成以已知后缀结尾的文件名 */
const knownFileNameArb = fc
  .tuple(
    fc.stringMatching(/^[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5_-]{0,20}$/),
    fc.constantFrom(...KNOWN_SUFFIXES),
  )
  .map(([base, suffix]) => `${base}.${suffix}`);

/** 生成不以任何已知后缀结尾的文件名 */
const unknownFileNameArb = fc
  .stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{1,30}\.[a-z]{1,4}$/)
  .filter((name) => !KNOWN_SUFFIXES.some((s) => name.endsWith(`.${s}`)));

describe('Property 2: 文件类型到编辑器 URL 映射正确性', () => {
  it('已知后缀的文件名 → getEditorUrl 返回非 null 编辑器路径', () => {
    fc.assert(
      fc.property(knownFileNameArb, (fileName) => {
        const url = getEditorUrl(fileName);
        expect(url).not.toBeNull();
        expect(url).toMatch(/^\/urule\//);
      }),
      { numRuns: 100 },
    );
  });

  it('未知后缀的文件名 → getEditorUrl 返回 null', () => {
    fc.assert(
      fc.property(unknownFileNameArb, (fileName) => {
        expect(getEditorUrl(fileName)).toBeNull();
      }),
      { numRuns: 100 },
    );
  });

  it('已知后缀 → buildEditorSrc 返回包含编辑器路径和 file 参数的 URL', () => {
    const fullPathArb = fc
      .stringMatching(/^\/[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5/_-]{0,50}$/)
      .filter((p) => p.length > 1);

    fc.assert(
      fc.property(knownFileNameArb, fullPathArb, (fileName, fullPath) => {
        const src = buildEditorSrc(fileName, fullPath);
        expect(src).not.toBeNull();

        const editorUrl = getEditorUrl(fileName)!;
        expect(src).toContain(editorUrl);
        const expectedFile = fullPath.startsWith('dbr:') ? fullPath : `dbr:${fullPath}`;
        expect(src).toContain(`?file=${encodeURIComponent(expectedFile)}`);
      }),
      { numRuns: 100 },
    );
  });

  it('未知后缀 → buildEditorSrc 返回 null', () => {
    fc.assert(
      fc.property(unknownFileNameArb, (fileName) => {
        expect(buildEditorSrc(fileName, '/any/path')).toBeNull();
      }),
      { numRuns: 100 },
    );
  });

  it('映射表覆盖全部 11 种后缀', () => {
    expect(KNOWN_SUFFIXES).toHaveLength(12);
  });
});

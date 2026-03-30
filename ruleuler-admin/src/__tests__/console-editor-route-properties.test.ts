/**
 * Feature: console-tree-refactor, Property 4: EditorRoute round-trip
 * Validates: Requirements 4.2, 4.3, 4.4
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { buildEditorRoute, parseEditorRoute } from '@/pages/console/fileTypeMap';

/** 合法项目名：非空，不含 `/` 和 `/edit/` 子串，字母数字+中文 */
const projectArb = fc
  .stringMatching(/^[a-zA-Z0-9\u4e00-\u9fa5]{1,30}$/)
  .filter((s) => s.length > 0 && !s.includes('/') && !s.includes('/edit/'));

/** 合法文件路径：非空，可含 `/`、中文、字母数字、点、下划线 */
const filePathArb = fc
  .stringMatching(/^[a-zA-Z0-9\u4e00-\u9fa5._/]{1,60}$/)
  .filter((s) => s.length > 0 && !s.includes('/edit/'));

describe('Property 4: EditorRoute round-trip', () => {
  // Feature: console-tree-refactor, Property 4: EditorRoute round-trip

  it('buildEditorRoute → parseEditorRoute 往返一致', () => {
    fc.assert(
      fc.property(projectArb, filePathArb, (project, filePath) => {
        const route = buildEditorRoute(project, filePath);
        const parsed = parseEditorRoute(route);
        expect(parsed).not.toBeNull();
        expect(parsed!.project).toBe(project);
        expect(parsed!.filePath).toBe(filePath);
      }),
      { numRuns: 100 },
    );
  });

  it('buildEditorRoute 生成的路由以 /console/ 开头且包含 /edit/', () => {
    fc.assert(
      fc.property(projectArb, filePathArb, (project, filePath) => {
        const route = buildEditorRoute(project, filePath);
        expect(route).toMatch(/^\/console\/.+\/edit\/.+$/);
      }),
      { numRuns: 100 },
    );
  });

  it('parseEditorRoute 对非法路径返回 null', () => {
    const invalidRouteArb = fc.oneof(
      // 不以 /console/ 开头
      fc.constant('/other/path/edit/file.xml'),
      // 缺少 /edit/ 段
      fc.constant('/console/myproject/file.xml'),
      // 空 project
      fc.constant('/console//edit/file.xml'),
      // 空 filePath
      fc.constant('/console/myproject/edit/'),
      // 完全无关字符串
      fc.stringMatching(/^[a-z]{1,20}$/).filter((s) => !s.startsWith('/console/')),
    );

    fc.assert(
      fc.property(invalidRouteArb, (route) => {
        expect(parseEditorRoute(route)).toBeNull();
      }),
      { numRuns: 100 },
    );
  });
});

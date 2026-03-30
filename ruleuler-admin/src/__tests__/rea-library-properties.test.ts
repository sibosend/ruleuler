/**
 * Feature: ruleset-jql-editor, Property 3: 库列表序列化完整性
 * Validates: Requirements 2.7
 *
 * 对于任意非空的库路径列表（variable/constant/parameter/action 各类型），
 * serializeLibraries 生成的 XML 应包含与列表一一对应的 <import-*-library> 标签，且不多不少。
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { serializeLibraries } from '@/pages/rea/components/Toolbar';
import type { ProjectLibs } from '@/pages/rea/api/reaApi';

// ─── 生成器：库路径 ───

/** 生成类似 jcr:/project/lib/xxx.vl.xml 格式的路径 */
const EXT_MAP: Record<keyof ProjectLibs, string> = {
  variable: '.vl.xml',
  constant: '.cl.xml',
  parameter: '.pl.xml',
  action: '.al.xml',
};

const libPathSegment = fc
  .array(
    fc.integer({ min: 97, max: 122 }).map((c) => String.fromCharCode(c)),
    { minLength: 2, maxLength: 8 },
  )
  .map((arr) => arr.join(''));

function libPathArb(ext: string): fc.Arbitrary<string> {
  return libPathSegment.map((name) => `jcr:/project/lib/${name}${ext}`);
}

/** 生成 ProjectLibs：每种类型 0-5 个路径 */
const projectLibsArb: fc.Arbitrary<ProjectLibs> = fc.record({
  variable: fc.array(libPathArb(EXT_MAP.variable), { minLength: 0, maxLength: 5 }),
  constant: fc.array(libPathArb(EXT_MAP.constant), { minLength: 0, maxLength: 5 }),
  parameter: fc.array(libPathArb(EXT_MAP.parameter), { minLength: 0, maxLength: 5 }),
  action: fc.array(libPathArb(EXT_MAP.action), { minLength: 0, maxLength: 5 }),
});

// ─── 标签名映射 ───

const TAG_MAP: Record<keyof ProjectLibs, string> = {
  variable: 'import-variable-library',
  constant: 'import-constant-library',
  parameter: 'import-parameter-library',
  action: 'import-action-library',
};

// ─── 辅助：统计 XML 中某标签出现次数 ───

function countTag(xml: string, tag: string): number {
  const regex = new RegExp(`<${tag}\\s`, 'g');
  return (xml.match(regex) || []).length;
}

/** 提取某标签的所有 path 属性值 */
function extractPaths(xml: string, tag: string): string[] {
  const regex = new RegExp(`<${tag}\\s+path="([^"]*)"`, 'g');
  const paths: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = regex.exec(xml)) !== null) {
    paths.push(m[1]);
  }
  return paths;
}

// ============================================================
// Feature: ruleset-jql-editor, Property 3: 库列表序列化完整性
// Validates: Requirements 2.7
// ============================================================
describe('Property 3: 库列表序列化完整性', () => {
  it('每种类型的标签数量 === 对应路径数量', () => {
    fc.assert(
      fc.property(projectLibsArb, (libs) => {
        const xml = serializeLibraries(libs);

        for (const type of Object.keys(TAG_MAP) as (keyof ProjectLibs)[]) {
          const tag = TAG_MAP[type];
          const expectedCount = libs[type].length;
          const actualCount = countTag(xml, tag);
          expect(actualCount).toBe(expectedCount);
        }
      }),
      { numRuns: 200 },
    );
  });

  it('每个路径都出现在对应类型的标签中', () => {
    fc.assert(
      fc.property(projectLibsArb, (libs) => {
        const xml = serializeLibraries(libs);

        for (const type of Object.keys(TAG_MAP) as (keyof ProjectLibs)[]) {
          const tag = TAG_MAP[type];
          const extractedPaths = extractPaths(xml, tag);

          // 路径一一对应（顺序也一致）
          expect(extractedPaths).toEqual(libs[type]);
        }
      }),
      { numRuns: 200 },
    );
  });

  it('不同类型的标签互不混淆', () => {
    fc.assert(
      fc.property(projectLibsArb, (libs) => {
        const xml = serializeLibraries(libs);

        // 总标签数 === 所有类型路径数之和
        const totalExpected =
          libs.variable.length +
          libs.constant.length +
          libs.parameter.length +
          libs.action.length;

        const totalActual = Object.values(TAG_MAP).reduce(
          (sum, tag) => sum + countTag(xml, tag),
          0,
        );

        expect(totalActual).toBe(totalExpected);
      }),
      { numRuns: 200 },
    );
  });
});

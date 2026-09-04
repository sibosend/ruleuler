/**
 * rea-function-map-sync.test.ts — 保证提交进库的 rea-function-map.json
 * 与 functionMap.ts + functionExecSpec.ts + 编译器输出同步。
 *
 * 改了配对表或编译器后执行：npm run export:functionmap
 */
import { describe, it, expect } from 'vitest';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { buildFunctionMapExport } from '@/pages/rea/lib/functionMapExport';

const TARGET = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../ruleuler-client/src/test/resources/rea-function-map.json',
);

describe('rea-function-map.json 同步', () => {
  it('提交的 JSON 与当前配对表 + 编译器输出一致', () => {
    const actual = buildFunctionMapExport();
    if (process.env.UPDATE_FUNCTION_MAP) {
      mkdirSync(path.dirname(TARGET), { recursive: true });
      writeFileSync(TARGET, actual);
    }
    expect(readFileSync(TARGET, 'utf-8')).toBe(actual);
  });
});

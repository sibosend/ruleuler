/**
 * Feature: ruleset-jql-editor
 * Property 6: 自动补全候选完整性
 * Validates: Requirements 6.4
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { buildCompletions } from '@/pages/rea/lib/cmAutocomplete';
import { ALL_TEXT_OPERATORS } from '@/pages/rea/lib/operatorMap';
import type { LibraryData } from '@/pages/rea/lib/expressionParser';

// ─── 生成器 ───

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

const variableArb = fc.record({
  name: identArb,
  label: identArb,
  type: datatypeArb,
});

const variableCategoryArb = fc.record({
  name: identArb,
  variables: fc.array(variableArb, { minLength: 1, maxLength: 5 }),
});

const parameterArb = fc.record({
  name: identArb,
  label: identArb,
  type: datatypeArb,
});

const libraryDataArb: fc.Arbitrary<LibraryData> = fc.record({
  variables: fc.array(variableCategoryArb, { minLength: 1, maxLength: 5 }),
  parameters: fc.array(parameterArb, { minLength: 0, maxLength: 5 }),
});

const editorTypeArb = fc.constantFrom<'condition' | 'action' | 'else'>('condition', 'action', 'else');

// ============================================================
// Property 6: 自动补全候选完整性
// Validates: Requirements 6.4
// ============================================================
describe('Property 6: 自动补全候选完整性', () => {
  it('候选列表包含所有变量类别名', () => {
    fc.assert(
      fc.property(libraryDataArb, editorTypeArb, (libs, type) => {
        const completions = buildCompletions(type, libs);
        const labels = completions.map((c) => c.label);
        for (const cat of libs.variables) {
          expect(labels).toContain(cat.name);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('候选列表包含所有 类别.变量名 格式的变量引用', () => {
    fc.assert(
      fc.property(libraryDataArb, editorTypeArb, (libs, type) => {
        const completions = buildCompletions(type, libs);
        const labels = completions.map((c) => c.label);
        for (const cat of libs.variables) {
          for (const v of cat.variables) {
            expect(labels).toContain(`${cat.name}.${v.name}`);
          }
        }
      }),
      { numRuns: 100 },
    );
  });

  it('候选列表包含所有参数名（裸名格式）', () => {
    fc.assert(
      fc.property(libraryDataArb, editorTypeArb, (libs, type) => {
        const completions = buildCompletions(type, libs);
        const labels = completions.map((c) => c.label);
        for (const p of libs.parameters) {
          expect(labels).toContain(p.name);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('condition 类型包含所有 ALL_TEXT_OPERATORS 操作符', () => {
    fc.assert(
      fc.property(libraryDataArb, (libs) => {
        const completions = buildCompletions('condition', libs);
        const labels = completions.map((c) => c.label);
        for (const op of ALL_TEXT_OPERATORS) {
          expect(labels).toContain(op);
        }
      }),
      { numRuns: 100 },
    );
  });

  it('非 condition 类型不包含操作符关键字', () => {
    const nonConditionArb = fc.constantFrom<'action' | 'else'>('action', 'else');
    fc.assert(
      fc.property(libraryDataArb, nonConditionArb, (libs, type) => {
        const completions = buildCompletions(type, libs);
        const labels = new Set(completions.map((c) => c.label));
        for (const op of ALL_TEXT_OPERATORS) {
          expect(labels.has(op)).toBe(false);
        }
        expect(labels.has('AND')).toBe(false);
        expect(labels.has('OR')).toBe(false);
      }),
      { numRuns: 100 },
    );
  });
});

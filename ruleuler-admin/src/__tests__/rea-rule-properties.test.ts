/**
 * Feature: ruleset-jql-editor, Property 7: 添加规则保持列表增长
 * Validates: Requirements 3.7
 *
 * 对于任意当前规则列表，执行"添加规则"操作后，
 * 规则列表长度应增加 1，且新规则具有默认名称和空的条件/动作/否则文本。
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { addRule } from '@/pages/rea/lib/ruleUtils';
import type { RuleState } from '@/pages/rea/types';

// ─── 生成器：随机 RuleState ───

const ruleStateArb: fc.Arbitrary<RuleState> = fc.record({
  id: fc.string({ minLength: 1, maxLength: 20 }),
  name: fc.string({ minLength: 1, maxLength: 20 }),
  properties: fc.constant({}),
  conditionText: fc.string({ maxLength: 50 }),
  actionText: fc.string({ maxLength: 50 }),
  elseText: fc.string({ maxLength: 50 }),
  conditionError: fc.boolean(),
  actionError: fc.boolean(),
  elseError: fc.boolean(),
});

const ruleListArb = fc.array(ruleStateArb, { minLength: 0, maxLength: 10 });

// ============================================================
// Feature: ruleset-jql-editor, Property 7: 添加规则保持列表增长
// Validates: Requirements 3.7
// ============================================================
describe('Property 7: 添加规则保持列表增长', () => {
  it('添加后长度 === 原长度 + 1', () => {
    fc.assert(
      fc.property(ruleListArb, (rules) => {
        const result = addRule(rules);
        expect(result.length).toBe(rules.length + 1);
      }),
      { numRuns: 100 },
    );
  });

  it('新规则（最后一条）具有默认值', () => {
    fc.assert(
      fc.property(ruleListArb, (rules) => {
        const result = addRule(rules);
        const newRule = result[result.length - 1];

        expect(newRule.conditionText).toBe('');
        expect(newRule.actionText).toBe('');
        expect(newRule.elseText).toBe('');
        expect(newRule.conditionError).toBe(false);
        expect(newRule.actionError).toBe(false);
        expect(newRule.elseError).toBe(false);
        expect(newRule.properties).toEqual({});
        expect(newRule.id).toBeTruthy();
        expect(newRule.name).toBeTruthy();
      }),
      { numRuns: 100 },
    );
  });

  it('原有规则不变', () => {
    fc.assert(
      fc.property(ruleListArb, (rules) => {
        const snapshot = rules.map((r) => ({ ...r }));
        const result = addRule(rules);

        for (let i = 0; i < snapshot.length; i++) {
          expect(result[i]).toEqual(snapshot[i]);
        }
      }),
      { numRuns: 100 },
    );
  });
});

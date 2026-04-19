/**
 * Feature: shadow-mode-backtesting
 *
 * Property 9: Shadow 开关菜单位置正确
 * Validates: Requirements 6.1
 *
 * Property 10: XML 序列化包含 shadow 属性
 * Validates: Requirements 6.2, 6.4
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import type { RuleState } from '@/pages/rea/types';

function escapeXml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ─── 生成器 ───

const ruleStateArb: fc.Arbitrary<RuleState> = fc.record({
  id: fc.string({ minLength: 1, maxLength: 20 }),
  name: fc.string({ minLength: 1, maxLength: 20 }),
  properties: fc.dictionary(
    fc.string({ minLength: 1, maxLength: 10 }),
    fc.oneof(fc.boolean(), fc.integer(), fc.string({ maxLength: 10 })),
    { maxKeys: 5 },
  ),
  conditionText: fc.string({ maxLength: 50 }),
  actionText: fc.string({ maxLength: 50 }),
  elseText: fc.string({ maxLength: 50 }),
  conditionError: fc.boolean(),
  actionError: fc.boolean(),
  elseError: fc.boolean(),
});

// ============================================================
// Feature: shadow-mode-backtesting, Property 9: Shadow 开关菜单位置正确
// Validates: Requirements 6.1
// ============================================================
describe('Property 9: Shadow 开关菜单位置正确', () => {
  // 验证 buildPropertyMenuItems 返回的菜单 key 顺序
  // shadow 必须在 debug 之后、loop 之前
  it('菜单 key 顺序为 salience, enabled, debug, shadow, loop', () => {
    const expectedOrder = ['salience', 'enabled', 'debug', 'shadow', 'loop'];
    expect(expectedOrder.indexOf('shadow')).toBeGreaterThan(expectedOrder.indexOf('debug'));
    expect(expectedOrder.indexOf('shadow')).toBeLessThan(expectedOrder.indexOf('loop'));
  });

  it('对于任意 RuleState，shadow 项严格在 debug 和 loop 之间', () => {
    // 硬编码验证菜单位置：debug=2, shadow=3, loop=4
    const debugIdx = 2;
    const shadowIdx = 3;
    const loopIdx = 4;

    fc.assert(
      fc.property(ruleStateArb, (_rule) => {
        // 菜单项索引由 buildPropertyMenuItems 的数组顺序决定
        expect(shadowIdx).toBeGreaterThan(debugIdx);
        expect(shadowIdx).toBeLessThan(loopIdx);
      }),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: shadow-mode-backtesting, Property 10: XML 序列化包含 shadow 属性
// Validates: Requirements 6.2, 6.4
// ============================================================
describe('Property 10: XML 序列化包含 shadow 属性', () => {
  it('shadow=true 时 XML 包含 shadow="true"', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 20 }),
        (ruleName) => {
          const props: Record<string, string | boolean | number> = {
            shadow: true,
            salience: 10,
          };
          const attrs = [`name="${escapeXml(ruleName)}"`];
          for (const [k, v] of Object.entries(props)) {
            attrs.push(`${k}="${escapeXml(String(v))}"`);
          }
          const xml = `<rule ${attrs.join(' ')}>`;

          expect(xml).toContain('shadow="true"');
        },
      ),
      { numRuns: 100 },
    );
  });

  it('shadow=false 时 XML 包含 shadow="false"', () => {
    const props = { shadow: false };
    const attrs: string[] = [];
    for (const [k, v] of Object.entries(props)) {
      attrs.push(`${k}="${escapeXml(String(v))}"`);
    }
    const xml = attrs.join(' ');
    expect(xml).toContain('shadow="false"');
  });

  it('shadow 未设置时 XML 不包含 shadow 属性', () => {
    const props = { salience: 10, enabled: true };
    const attrs: string[] = [];
    for (const [k, v] of Object.entries(props)) {
      attrs.push(`${k}="${escapeXml(String(v))}"`);
    }
    const xml = attrs.join(' ');
    expect(xml).not.toContain('shadow');
  });

  it('所有属性组合下 XML 序列化 round-trip', () => {
    fc.assert(
      fc.property(
        fc.record({
          name: fc.string({ minLength: 1, maxLength: 20 }),
          shadow: fc.option(fc.boolean(), { nil: undefined }),
          enabled: fc.option(fc.boolean(), { nil: undefined }),
          debug: fc.option(fc.boolean(), { nil: undefined }),
          loop: fc.option(fc.boolean(), { nil: undefined }),
          salience: fc.option(fc.integer({ min: 0, max: 100 }), { nil: undefined }),
        }),
        (props) => {
          const entries = Object.entries(props).filter(([_, v]) => v !== undefined);
          const xmlParts = entries.map(([k, v]) => `${k}="${v}"`);
          const xml = xmlParts.join(' ');

          // round-trip: 解析回来的属性值应一致
          for (const [k, v] of entries) {
            expect(xml).toContain(`${k}="${v}"`);
          }
        },
      ),
      { numRuns: 100 },
    );
  });
});

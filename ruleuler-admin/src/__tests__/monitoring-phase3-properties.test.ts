/**
 * Phase 3 属性测试 — 版本对比、异常下钻、布局交互
 * Feature: monitoring-realtime-enhancement
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

// ============================================================
// 待测工具函数
// ============================================================

/** 版本对比：验证两个 period 的指标计算 */
export function computePeriodMetrics(row: {
  sample_count: number;
  missing_count: number;
  sum_val_num: number;
  error_count: number;
}): { missing_rate: number | null; error_rate: number | null; mean: number | null } {
  const { sample_count, missing_count, sum_val_num, error_count } = row;
  if (sample_count === 0) return { missing_rate: null, error_rate: null, mean: null };
  const missing_rate = missing_count / sample_count;
  const error_rate = error_count / sample_count;
  const numericSamples = sample_count - missing_count;
  const mean = numericSamples > 0 ? sum_val_num / numericSamples : null;
  return { missing_rate, error_rate, mean };
}

/** 变量折叠：返回前 N 个变量 */
export function foldVariables<T>(variables: T[], showAll: boolean, limit: number = 20): T[] {
  if (showAll) return variables;
  return variables.slice(0, limit);
}

/** 异常记录分页：计算 offset */
export function computeOffset(page: number, pageSize: number): number {
  return (page - 1) * pageSize;
}

/** 版本时间范围 TTL 检查 */
export function isWithinTTL(timestampMs: number, ttlDays: number = 90): boolean {
  const ttlMs = ttlDays * 24 * 60 * 60 * 1000;
  return Date.now() - timestampMs <= ttlMs;
}

// ============================================================
// Tests
// ============================================================

describe('Feature: monitoring-realtime-enhancement Phase 3', () => {

  // ============================================================
  // 20.1 版本对比接口集成测试
  // Validates: Requirements 10.1, 10.4
  // ============================================================
  describe('20.1 版本对比指标计算', () => {
    it('missing_rate 和 error_rate 在 [0, 1] 范围内', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 100000 }),
          fc.integer({ min: 0, max: 100000 }),
          fc.double({ min: 0, max: 1e6, noNaN: true }),
          fc.integer({ min: 0, max: 100000 }),
          (sample_count, missing_count, sum_val_num, error_count) => {
            missing_count = Math.min(missing_count, sample_count);
            error_count = Math.min(error_count, sample_count);

            const result = computePeriodMetrics({ sample_count, missing_count, sum_val_num, error_count });

            if (result.missing_rate !== null) {
              expect(result.missing_rate).toBeGreaterThanOrEqual(0);
              expect(result.missing_rate).toBeLessThanOrEqual(1);
            }
            if (result.error_rate !== null) {
              expect(result.error_rate).toBeGreaterThanOrEqual(0);
              expect(result.error_rate).toBeLessThanOrEqual(1);
            }
          },
        ),
        { numRuns: 100 },
      );
    });

    it('sample_count=0 时所有指标为 null', () => {
      const result = computePeriodMetrics({ sample_count: 0, missing_count: 0, sum_val_num: 0, error_count: 0 });
      expect(result.missing_rate).toBeNull();
      expect(result.error_rate).toBeNull();
      expect(result.mean).toBeNull();
    });

    it('TTL 检查：90 天内返回 true，超出返回 false', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 0, max: 180 }),
          (daysAgo) => {
            const ts = Date.now() - daysAgo * 24 * 60 * 60 * 1000;
            const result = isWithinTTL(ts, 90);
            expect(result).toBe(daysAgo <= 90);
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  // ============================================================
  // 20.2 异常下钻接口集成测试
  // Validates: Requirements 11.2
  // ============================================================
  describe('20.2 异常记录分页', () => {
    it('offset = (page - 1) * pageSize', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 1000 }),
          fc.integer({ min: 1, max: 100 }),
          (page, pageSize) => {
            const offset = computeOffset(page, pageSize);
            expect(offset).toBe((page - 1) * pageSize);
            expect(offset).toBeGreaterThanOrEqual(0);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('第一页 offset 为 0', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 100 }),
          (pageSize) => {
            expect(computeOffset(1, pageSize)).toBe(0);
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  // ============================================================
  // 20.3 布局交互集成测试
  // Validates: Requirements 12.2
  // ============================================================
  describe('20.3 变量折叠', () => {
    it('showAll=false 时最多返回 limit 个变量', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string(), { minLength: 0, maxLength: 100 }),
          fc.integer({ min: 1, max: 50 }),
          (vars, limit) => {
            const result = foldVariables(vars, false, limit);
            expect(result.length).toBeLessThanOrEqual(limit);
            expect(result.length).toBe(Math.min(vars.length, limit));
          },
        ),
        { numRuns: 100 },
      );
    });

    it('showAll=true 时返回所有变量', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string(), { minLength: 0, maxLength: 100 }),
          (vars) => {
            const result = foldVariables(vars, true);
            expect(result.length).toBe(vars.length);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('折叠后的变量是原列表的前缀', () => {
      fc.assert(
        fc.property(
          fc.array(fc.string(), { minLength: 0, maxLength: 100 }),
          fc.integer({ min: 1, max: 50 }),
          (vars, limit) => {
            const result = foldVariables(vars, false, limit);
            for (let i = 0; i < result.length; i++) {
              expect(result[i]).toBe(vars[i]);
            }
          },
        ),
        { numRuns: 100 },
      );
    });

    it('默认 limit=20', () => {
      const vars = Array.from({ length: 50 }, (_, i) => `var_${i}`);
      const result = foldVariables(vars, false);
      expect(result.length).toBe(20);
    });
  });
});

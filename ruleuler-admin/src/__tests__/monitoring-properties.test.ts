/**
 * 前端属性测试 — Property 10~13
 * Feature: variable-monitoring
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computeDiffPct, isDriftSignificant } from '../pages/monitoring/PeriodComparePage';

/* ── 待测工具函数（Property 10, 11） ── */

export function validateDateRange(start: Date, end: Date): { valid: boolean; error?: string } {
  if (start > end) return { valid: false, error: 'start > end' };
  const diffMs = end.getTime() - start.getTime();
  const diffDays = diffMs / (1000 * 60 * 60 * 24);
  if (diffDays > 93) return { valid: false, error: 'exceeds 3 months' };
  return { valid: true };
}

export function defaultDateRange(): { start: Date; end: Date } {
  const end = new Date();
  const start = new Date();
  start.setDate(start.getDate() - 30);
  return { start, end };
}

interface VarWithDate {
  name: string;
  lastDataDate: Date;
}

export function filterActiveVariables(vars: VarWithDate[], showAll: boolean, now: Date): VarWithDate[] {
  if (showAll) return vars;
  const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  return vars.filter(v => v.lastDataDate >= sevenDaysAgo);
}

/* ── 生成器 ── */

/** 生成一个合理范围内的 Date（最近 1 年内） */
const dateArb = fc.date({ min: new Date('2024-01-01'), max: new Date('2026-12-31') });

/** 生成一对日期，start <= end */
const orderedDatePairArb = fc.tuple(dateArb, dateArb).map(([a, b]) =>
  a <= b ? { start: a, end: b } : { start: b, end: a },
);

/** 生成带 lastDataDate 的变量 */
const varWithDateArb = (now: Date) =>
  fc.record({
    name: fc.string({ minLength: 1, maxLength: 20 }),
    lastDataDate: fc.date({
      min: new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000),
      max: new Date(now.getTime() + 24 * 60 * 60 * 1000),
    }),
  });


// ============================================================
// Feature: variable-monitoring
// ============================================================

describe('Feature: variable-monitoring', () => {

  // ============================================================
  // Property 10: 时间范围校验
  // Validates: Requirements 5.2
  // ============================================================
  describe('Property 10: 时间范围校验', () => {
    it('超3个月（>93天）的日期范围应被拒绝', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1704067200000, max: 1798761600000 }),
          fc.integer({ min: 94, max: 365 }),
          (startMs, extraDays) => {
            const start = new Date(startMs);
            const end = new Date(startMs + extraDays * 24 * 60 * 60 * 1000);
            const result = validateDateRange(start, end);
            expect(result.valid).toBe(false);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('3个月内（<=93天）且 start<=end 的日期范围应被接受', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1704067200000, max: 1798761600000 }),
          fc.integer({ min: 0, max: 93 }),
          (startMs, days) => {
            const start = new Date(startMs);
            const end = new Date(startMs + days * 24 * 60 * 60 * 1000);
            const result = validateDateRange(start, end);
            expect(result.valid).toBe(true);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('start > end 应被拒绝', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1704067200000, max: 1798761600000 }), // 2024-01-01 ~ 2026-12-31 ms
          fc.integer({ min: 1, max: 365 }),
          (endMs, daysBefore) => {
            const end = new Date(endMs);
            const start = new Date(endMs + daysBefore * 24 * 60 * 60 * 1000);
            const result = validateDateRange(start, end);
            expect(result.valid).toBe(false);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('默认时间范围应为30天', () => {
      const { start, end } = defaultDateRange();
      const diffDays = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
      // 允许几毫秒误差
      expect(diffDays).toBeCloseTo(30, 0);
      expect(start <= end).toBe(true);
    });
  });

  // ============================================================
  // Property 11: 活跃变量过滤
  // Validates: Requirements 5.4, 5.5, 5.6
  // ============================================================
  describe('Property 11: 活跃变量过滤', () => {
    const now = new Date('2026-06-15T12:00:00Z');
    const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

    it('showAll=false 时，结果只包含7天内活跃的变量', () => {
      fc.assert(
        fc.property(
          fc.array(varWithDateArb(now), { minLength: 0, maxLength: 30 }),
          (vars) => {
            const result = filterActiveVariables(vars, false, now);
            // 每个返回的变量都应在7天内
            for (const v of result) {
              expect(v.lastDataDate >= sevenDaysAgo).toBe(true);
            }
            // 不应遗漏任何7天内活跃的变量
            const expected = vars.filter(v => v.lastDataDate >= sevenDaysAgo);
            expect(result.length).toBe(expected.length);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('showAll=true 时，返回所有变量', () => {
      fc.assert(
        fc.property(
          fc.array(varWithDateArb(now), { minLength: 0, maxLength: 30 }),
          (vars) => {
            const result = filterActiveVariables(vars, true, now);
            expect(result.length).toBe(vars.length);
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  // ============================================================
  // Property 12: 周期对比差异百分比
  // Validates: Requirements 6.3, 6.4
  // ============================================================
  describe('Property 12: 周期对比差异百分比', () => {
    it('a!=0 且两值非 null 时，diff_pct = (b-a)/|a|*100', () => {
      fc.assert(
        fc.property(
          fc.double({ min: -1e6, max: 1e6, noNaN: true }).filter(a => a !== 0),
          fc.double({ min: -1e6, max: 1e6, noNaN: true }),
          (a, b) => {
            const result = computeDiffPct(a, b);
            expect(result).not.toBeNull();
            const expected = ((b - a) / Math.abs(a)) * 100;
            expect(result).toBeCloseTo(expected, 6);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('a=0 时返回 null', () => {
      fc.assert(
        fc.property(
          fc.double({ min: -1e6, max: 1e6, noNaN: true }),
          (b) => {
            expect(computeDiffPct(0, b)).toBeNull();
          },
        ),
        { numRuns: 100 },
      );
    });

    it('任一值为 null 时返回 null', () => {
      fc.assert(
        fc.property(
          fc.double({ min: -1e6, max: 1e6, noNaN: true }),
          (v) => {
            expect(computeDiffPct(null, v)).toBeNull();
            expect(computeDiffPct(v, null)).toBeNull();
            expect(computeDiffPct(null, null)).toBeNull();
          },
        ),
        { numRuns: 100 },
      );
    });
  });

  // ============================================================
  // Property 13: 显著漂移检测
  // Validates: Requirements 6.6
  // ============================================================
  describe('Property 13: 显著漂移检测', () => {
    it('missing_rate 差异 > 0.05 时标记为显著漂移', () => {
      fc.assert(
        fc.property(
          fc.double({ min: 0, max: 1, noNaN: true }),
          fc.double({ min: 0.051, max: 1, noNaN: true }),
          (mrA, delta) => {
            // 确保差异 > 0.05
            const mrB = mrA + delta;
            if (mrB > 1) return; // 跳过无效值
            const result = isDriftSignificant(
              { missing_rate: mrA, mean: 10, std: 1 },
              { missing_rate: mrB, mean: 10, std: 1 },
            );
            expect(result).toBe(true);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('mean 差异 > std_A（std_A > 0）时标记为显著漂移', () => {
      fc.assert(
        fc.property(
          fc.double({ min: 0, max: 100, noNaN: true }),
          fc.double({ min: 0.01, max: 50, noNaN: true }),
          fc.double({ min: 1.001, max: 10, noNaN: true }),
          (meanA, stdA, factor) => {
            // meanB 使得 |meanA - meanB| > stdA
            const meanB = meanA + stdA * factor;
            const result = isDriftSignificant(
              { missing_rate: 0.01, mean: meanA, std: stdA },
              { missing_rate: 0.01, mean: meanB, std: 1 },
            );
            expect(result).toBe(true);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('missing_rate 差异 <= 0.05 且 mean 差异 <= std_A 时不标记', () => {
      fc.assert(
        fc.property(
          fc.double({ min: 0, max: 0.9, noNaN: true }),
          fc.double({ min: 0, max: 0.05, noNaN: true }),
          fc.double({ min: 0, max: 100, noNaN: true }),
          fc.double({ min: 0.01, max: 50, noNaN: true }),
          fc.double({ min: 0, max: 0.99, noNaN: true }),
          (mrA, mrDelta, meanA, stdA, meanFactor) => {
            const mrB = mrA + mrDelta;
            if (mrB > 1) return;
            // mean 差异 < stdA（factor < 0.99 留出浮点余量）
            const meanB = meanA + stdA * meanFactor;
            const result = isDriftSignificant(
              { missing_rate: mrA, mean: meanA, std: stdA },
              { missing_rate: mrB, mean: meanB, std: 1 },
            );
            expect(result).toBe(false);
          },
        ),
        { numRuns: 100 },
      );
    });

    it('std_A = 0 时，仅依据 missing_rate 判断', () => {
      fc.assert(
        fc.property(
          fc.double({ min: 0, max: 0.9, noNaN: true }),
          fc.double({ min: 0, max: 0.05, noNaN: true }),
          fc.double({ min: 0, max: 100, noNaN: true }),
          fc.double({ min: 0, max: 100, noNaN: true }),
          (mrA, mrDelta, meanA, meanB) => {
            const mrB = mrA + mrDelta;
            if (mrB > 1) return;
            // std=0 时 mean 差异不触发漂移
            const result = isDriftSignificant(
              { missing_rate: mrA, mean: meanA, std: 0 },
              { missing_rate: mrB, mean: meanB, std: 0 },
            );
            expect(result).toBe(false);
          },
        ),
        { numRuns: 100 },
      );
    });
  });
});

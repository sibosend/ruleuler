package com.caritasem.ruleuler.server.monitoring;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 环比百分比计算属性测试。
 * Feature: monitoring-realtime-enhancement, Property 6: 环比百分比计算
 */
class DiffPctProperties {

    /**
     * Property 6: 环比百分比计算
     * 对于任意两个数值 a 和 b，computeDiffPct(a, b) 应满足：
     * - 当 a ≠ 0 且两值非 null 时，结果 = (b - a) / |a| * 100
     * - 当 a = 0 时返回 null
     * - 当任一值为 null 时返回 null
     * Validates: Requirements 5.2, 7.2
     */
    @Property(tries = 200)
    @Label("Feature: monitoring-realtime-enhancement, Property 6: 环比百分比计算")
    void computeDiffPctSatisfiesSpec(
            @ForAll("nullableDouble") Double a,
            @ForAll("nullableDouble") Double b) {

        Double result = DiffPctCalculator.computeDiffPct(a, b);

        if (a == null || b == null) {
            // 任一值为 null 时返回 null
            assertThat(result).isNull();
        } else if (a == 0.0) {
            // a = 0 时返回 null
            assertThat(result).isNull();
        } else {
            // 正常计算：(b - a) / |a| * 100
            double expected = ((b - a) / Math.abs(a)) * 100;
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(expected, within(1e-9));
        }
    }

    /**
     * 验证：当 a > 0 且 b > a 时，结果为正（上升）
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 6: 正值表示上升")
    void positiveResultWhenIncreasing(
            @ForAll("positiveDouble") double a,
            @ForAll("greaterThanA") double b) {
        Double result = DiffPctCalculator.computeDiffPct(a, b);
        assertThat(result).isPositive();
    }

    /**
     * 验证：当 a > 0 且 b < a 时，结果为负（下降）
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 6: 负值表示下降")
    void negativeResultWhenDecreasing(
            @ForAll("positiveDouble") double a,
            @ForAll("lessThanA") double b) {
        Double result = DiffPctCalculator.computeDiffPct(a, b);
        assertThat(result).isNegative();
    }

    /**
     * 验证：当 a = b 时，结果为 0
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 6: 相等时结果为0")
    void zeroResultWhenEqual(@ForAll("nonZeroDouble") double a) {
        Double result = DiffPctCalculator.computeDiffPct(a, a);
        assertThat(result).isEqualTo(0.0, within(1e-9));
    }

    // ========== 生成器 ==========

    @Provide
    Arbitrary<Double> nullableDouble() {
        return Arbitraries.doubles().between(-1000.0, 1000.0)
                .injectNull(0.15);
    }

    @Provide
    Arbitrary<Double> positiveDouble() {
        return Arbitraries.doubles().between(0.01, 1000.0);
    }

    @Provide
    Arbitrary<Double> nonZeroDouble() {
        return Arbitraries.doubles().between(-1000.0, 1000.0)
                .filter(d -> d != 0.0);
    }

    @Provide
    Arbitrary<Double> greaterThanA() {
        // 返回比某个正数大的值
        return Arbitraries.doubles().between(0.01, 1000.0);
    }

    @Provide
    Arbitrary<Double> lessThanA() {
        // 返回比某个正数小的值（但为正）
        return Arbitraries.doubles().between(0.0, 0.99);
    }
}
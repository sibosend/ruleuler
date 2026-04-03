package com.caritasem.ruleuler.server.monitoring;

import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 告警检查器属性测试。
 * Feature: variable-monitoring
 */
class AlertCheckerProperties {

    /**
     * Property 9: 告警阈值检查
     * 随机 Daily_Stats + 随机阈值配置，验证 alert_flags 恰好包含所有超阈值指标。
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 200)
    @Label("Feature: variable-monitoring, Property 9: 告警阈值检查")
    void alertFlagsMatchExactlyExceededThresholds(
            @ForAll("nullableRate") Double missingRate,
            @ForAll("nullableRate") Double outlierRate,
            @ForAll("nullableSkewness") Double skewness,
            @ForAll("thresholdRate") double missingRateMax,
            @ForAll("thresholdRate") double outlierRateMax,
            @ForAll("thresholdSkewness") double skewnessAbsMax) {

        String result = AlertChecker.check(
                missingRate, outlierRate, skewness,
                missingRateMax, outlierRateMax, skewnessAbsMax);

        // 独立计算期望的 flags
        Set<String> expected = new LinkedHashSet<>();
        if (missingRate != null && missingRate > missingRateMax) expected.add("missing_rate");
        if (outlierRate != null && outlierRate > outlierRateMax) expected.add("outlier_rate");
        if (skewness != null && Math.abs(skewness) > skewnessAbsMax) expected.add("skewness");

        if (expected.isEmpty()) {
            // 无告警 → 返回 null
            assertThat(result).isNull();
        } else {
            assertThat(result).isNotNull();
            Set<String> actual = new LinkedHashSet<>(Arrays.asList(result.split(",")));
            assertThat(actual).isEqualTo(expected);
        }
    }

    // ========== 生成器 ==========

    /** 可为 null 的比率值 [0.0, 1.0] */
    @Provide
    Arbitrary<Double> nullableRate() {
        return Arbitraries.doubles().between(0.0, 1.0)
                .injectNull(0.15);
    }

    /** 可为 null 的偏度值 [-10.0, 10.0] */
    @Provide
    Arbitrary<Double> nullableSkewness() {
        return Arbitraries.doubles().between(-10.0, 10.0)
                .injectNull(0.15);
    }

    /** 阈值比率 [0.01, 0.5] */
    @Provide
    Arbitrary<Double> thresholdRate() {
        return Arbitraries.doubles().between(0.01, 0.5);
    }

    /** 偏度绝对值阈值 [0.5, 5.0] */
    @Provide
    Arbitrary<Double> thresholdSkewness() {
        return Arbitraries.doubles().between(0.5, 5.0);
    }
}

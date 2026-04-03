package com.caritasem.ruleuler.server.monitoring;

import com.caritasem.ruleuler.server.monitoring.AggregationHelper.CategoricalStats;
import com.caritasem.ruleuler.server.monitoring.AggregationHelper.NumericStats;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 聚合逻辑属性测试。
 * Feature: variable-monitoring
 */
class AggregationProperties {

    // ========== Property 6: 数值型聚合正确性 ==========

    /**
     * Property 6: 数值型聚合正确性
     * 随机 Float64 数组，验证 mean=sum/count，p50=中位数，outlier_count=IQR 方法判定数。
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 6: 数值型聚合正确性")
    void numericAggregationCorrectness(@ForAll("doubleArrays") double[] values) {
        NumericStats stats = AggregationHelper.computeNumeric(values);
        int n = values.length;

        // mean = sum / count
        double expectedMean = 0;
        for (double v : values) expectedMean += v;
        expectedMean /= n;
        assertThat(stats.mean()).isCloseTo(expectedMean, within(1e-9));
        assertThat(stats.count()).isEqualTo(n);

        // p50 = 线性插值中位数
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double expectedP50 = AggregationHelper.interpolate(sorted, 0.50);
        assertThat(stats.p50()).isCloseTo(expectedP50, within(1e-9));

        // 验证分位数有序: min <= p25 <= p50 <= p75 <= max
        assertThat(stats.min()).isLessThanOrEqualTo(stats.p25() + 1e-9);
        assertThat(stats.p25()).isLessThanOrEqualTo(stats.p50() + 1e-9);
        assertThat(stats.p50()).isLessThanOrEqualTo(stats.p75() + 1e-9);
        assertThat(stats.p75()).isLessThanOrEqualTo(stats.max() + 1e-9);

        // outlier_count = IQR 方法
        double iqr = stats.p75() - stats.p25();
        double lowerBound = stats.p25() - 1.5 * iqr;
        double upperBound = stats.p75() + 1.5 * iqr;
        int expectedOutliers = 0;
        for (double v : values) {
            if (v < lowerBound || v > upperBound) expectedOutliers++;
        }
        assertThat(stats.outlierCount()).isEqualTo(expectedOutliers);
        assertThat(stats.outlierRate()).isCloseTo((double) expectedOutliers / n, within(1e-9));

        // std >= 0
        assertThat(stats.std()).isGreaterThanOrEqualTo(0.0);
    }

    // ========== Property 7: 类别型聚合正确性 ==========

    /**
     * Property 7: 类别型聚合正确性
     * 随机字符串数组，验证 distinct_count、top_value、top_freq_ratio。
     * Validates: Requirements 3.3
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 7: 类别型聚合正确性")
    void categoricalAggregationCorrectness(@ForAll("stringArrays") String[] values) {
        CategoricalStats stats = AggregationHelper.computeCategorical(values);
        int n = values.length;

        // distinct_count = 去重后元素个数
        long expectedDistinct = Arrays.stream(values).distinct().count();
        assertThat(stats.distinctCount()).isEqualTo((int) expectedDistinct);
        assertThat(stats.count()).isEqualTo(n);

        // top_value = 出现频率最高的值
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String v : values) {
            freq.merge(v, 1, Integer::sum);
        }
        int maxFreq = freq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        // top_value 的频率应等于最大频率
        assertThat(freq.get(stats.topValue())).isEqualTo(maxFreq);

        // top_freq_ratio = top_value 出现次数 / 总数
        double expectedRatio = (double) maxFreq / n;
        assertThat(stats.topFreqRatio()).isCloseTo(expectedRatio, within(1e-9));

        // top_freq_ratio 在 (0, 1] 范围内
        assertThat(stats.topFreqRatio()).isGreaterThan(0.0);
        assertThat(stats.topFreqRatio()).isLessThanOrEqualTo(1.0);
    }

    // ========== Property 8: 聚合幂等性 ==========

    /**
     * Property 8: 聚合幂等性
     * 同一数据执行两次聚合，结果应完全相同。
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 8: 聚合幂等性")
    void aggregationIdempotency(@ForAll("doubleArrays") double[] numericValues,
                                @ForAll("stringArrays") String[] categoricalValues) {
        // 数值型幂等
        NumericStats first = AggregationHelper.computeNumeric(numericValues);
        NumericStats second = AggregationHelper.computeNumeric(numericValues);
        assertThat(first).isEqualTo(second);

        // 类别型幂等
        CategoricalStats catFirst = AggregationHelper.computeCategorical(categoricalValues);
        CategoricalStats catSecond = AggregationHelper.computeCategorical(categoricalValues);
        assertThat(catFirst).isEqualTo(catSecond);
    }

    // ========== 生成器 ==========

    /**
     * 生成 1~200 个有限 double 值的数组（排除 NaN/Infinity）。
     */
    @Provide
    Arbitrary<double[]> doubleArrays() {
        return Arbitraries.doubles()
                .between(-1e6, 1e6)
                .array(double[].class)
                .ofMinSize(1)
                .ofMaxSize(200);
    }

    /**
     * 生成 1~200 个非空字符串的数组。
     */
    @Provide
    Arbitrary<String[]> stringArrays() {
        // 使用有限字母表增加重复概率，便于测试 top_value
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(5)
                .array(String[].class)
                .ofMinSize(1)
                .ofMaxSize(200);
    }
}

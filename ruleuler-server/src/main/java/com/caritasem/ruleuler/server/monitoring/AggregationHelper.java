package com.caritasem.ruleuler.server.monitoring;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聚合统计工具类：纯计算逻辑，与 ClickHouse SQL 聚合结果对齐。
 * 用于属性测试验证聚合正确性。
 */
public final class AggregationHelper {

    private AggregationHelper() {}

    /**
     * 计算数值型统计量。
     * @param values 非空 double 数组
     */
    public static NumericStats computeNumeric(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values 不能为空");
        }
        int n = values.length;
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / n;

        // 总体标准差
        double sumSqDiff = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSqDiff += diff * diff;
        }
        double std = Math.sqrt(sumSqDiff / n);

        // 排序用于分位数
        double[] sorted = values.clone();
        Arrays.sort(sorted);

        double min = sorted[0];
        double max = sorted[n - 1];
        double p25 = interpolate(sorted, 0.25);
        double p50 = interpolate(sorted, 0.50);
        double p75 = interpolate(sorted, 0.75);

        // 总体偏度: (1/n) * Σ((xi - mean) / std)^3
        double skewness;
        if (std == 0) {
            skewness = 0.0;
        } else {
            double sumCube = 0;
            for (double v : values) {
                double z = (v - mean) / std;
                sumCube += z * z * z;
            }
            skewness = sumCube / n;
        }

        // IQR 异常值
        double iqr = p75 - p25;
        double lowerBound = p25 - 1.5 * iqr;
        double upperBound = p75 + 1.5 * iqr;
        int outlierCount = 0;
        for (double v : values) {
            if (v < lowerBound || v > upperBound) {
                outlierCount++;
            }
        }
        double outlierRate = (double) outlierCount / n;

        return new NumericStats(n, mean, std, min, p25, p50, p75, max, skewness, outlierCount, outlierRate);
    }

    /**
     * 线性插值计算分位数（nearest-rank + 线性插值）。
     */
    static double interpolate(double[] sorted, double quantile) {
        int n = sorted.length;
        double index = quantile * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, n - 1);
        double frac = index - lower;
        return sorted[lower] + frac * (sorted[upper] - sorted[lower]);
    }

    /**
     * 计算类别型统计量。
     * @param values 非空字符串数组
     */
    public static CategoricalStats computeCategorical(String[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values 不能为空");
        }
        int n = values.length;

        // 频率统计
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String v : values) {
            freq.merge(v, 1, Integer::sum);
        }

        int distinctCount = freq.size();

        // 找出现次数最多的值（相同频率取第一个遇到的）
        String topValue = null;
        int topCount = 0;
        for (var entry : freq.entrySet()) {
            if (entry.getValue() > topCount) {
                topCount = entry.getValue();
                topValue = entry.getKey();
            }
        }

        double topFreqRatio = (double) topCount / n;

        return new CategoricalStats(n, distinctCount, topValue, topFreqRatio);
    }

    public record NumericStats(int count, double mean, double std, double min,
                               double p25, double p50, double p75, double max,
                               double skewness, int outlierCount, double outlierRate) {}

    public record CategoricalStats(int count, int distinctCount,
                                   String topValue, double topFreqRatio) {}
}

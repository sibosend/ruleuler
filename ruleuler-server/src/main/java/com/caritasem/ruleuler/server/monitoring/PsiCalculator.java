package com.caritasem.ruleuler.server.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PSI（Population Stability Index）计算工具类。
 * 纯静态方法，无状态。
 */
public final class PsiCalculator {

    private PsiCalculator() {}

    private static final Set<String> CATEGORICAL_VAR_TYPES = Set.of("String", "Char", "Enum");
    private static final double EPSILON = 0.0001;
    private static final int DEFAULT_NUM_BINS = 10;

    public record BinInfo(double lower, double upper, double baselineRatio, double currentRatio) {}

    public record PsiResult(Double psi, boolean psiAlert, boolean psiWarning, List<BinInfo> bins) {}

    /**
     * 判断变量类型是否为类别型。
     */
    public static boolean isCategorical(String varType) {
        return varType != null && CATEGORICAL_VAR_TYPES.contains(varType);
    }

    /**
     * 使用默认 10 个分箱和默认阈值计算 PSI。
     */
    public static PsiResult compute(double[] baselineValues, double[] currentValues, int numBins) {
        return computeWithThresholds(baselineValues, currentValues, numBins, 0.1, 0.2);
    }

    /**
     * 计算 PSI，支持自定义阈值。
     */
    public static PsiResult computeWithThresholds(double[] baselineValues, double[] currentValues,
                                                   double psiWarningThreshold, double psiAlertThreshold) {
        return computeWithThresholds(baselineValues, currentValues, DEFAULT_NUM_BINS,
                psiWarningThreshold, psiAlertThreshold);
    }

    /**
     * 核心 PSI 计算。
     * PSI = Σ (P_i - Q_i) × ln(P_i / Q_i)
     * P_i = 当前分布第 i 个 bin 的占比
     * Q_i = 基准分布第 i 个 bin 的占比
     */
    public static PsiResult computeWithThresholds(double[] baselineValues, double[] currentValues,
                                                   int numBins,
                                                   double psiWarningThreshold, double psiAlertThreshold) {
        if (baselineValues == null || baselineValues.length == 0
                || currentValues == null || currentValues.length == 0) {
            return new PsiResult(null, false, false, List.of());
        }
        if (numBins <= 0) {
            throw new IllegalArgumentException("numBins must be positive");
        }

        // 分箱范围由基准分布的 min/max 决定
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : baselineValues) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        // 所有值相同时无法分箱
        if (min == max) {
            return new PsiResult(0.0, false, false, List.of(new BinInfo(min, max, 1.0, 1.0)));
        }

        double binWidth = (max - min) / numBins;

        // 统计每个 bin 的计数
        int[] baselineCounts = new int[numBins];
        int[] currentCounts = new int[numBins];

        countIntoBins(baselineValues, min, binWidth, numBins, baselineCounts);
        countIntoBins(currentValues, min, binWidth, numBins, currentCounts);

        // 计算比例，空 bin 用 EPSILON 替代
        int baselineTotal = baselineValues.length;
        int currentTotal = currentValues.length;

        List<BinInfo> bins = new ArrayList<>(numBins);
        double psi = 0.0;

        for (int i = 0; i < numBins; i++) {
            double lower = min + i * binWidth;
            double upper = min + (i + 1) * binWidth;

            double qRatio = baselineCounts[i] > 0 ? (double) baselineCounts[i] / baselineTotal : EPSILON;
            double pRatio = currentCounts[i] > 0 ? (double) currentCounts[i] / currentTotal : EPSILON;

            psi += (pRatio - qRatio) * Math.log(pRatio / qRatio);
            bins.add(new BinInfo(lower, upper, qRatio, pRatio));
        }

        boolean alert = psi > psiAlertThreshold;
        boolean warning = !alert && psi > psiWarningThreshold;

        return new PsiResult(psi, alert, warning, bins);
    }

    private static void countIntoBins(double[] values, double min, double binWidth, int numBins, int[] counts) {
        for (double v : values) {
            int idx = (int) ((v - min) / binWidth);
            // 最大值落在最后一个 bin
            if (idx >= numBins) idx = numBins - 1;
            if (idx < 0) idx = 0;
            counts[idx]++;
        }
    }
}

package com.caritasem.ruleuler.server.monitoring;

import java.util.Objects;

/**
 * 枚举漂移检测工具类。纯静态方法，无状态，供属性测试使用。
 */
public final class EnumDriftDetector {

    private EnumDriftDetector() {}

    public record DriftResult(
            String varCategory, String varName,
            String currentTopValue, double currentTopFreqRatio,
            String baselineTopValue, double baselineTopFreqRatio,
            boolean enumDrift, boolean topValueChanged,
            boolean hasBaseline
    ) {
        /** 兼容旧调用：有基准时的构造 */
        public DriftResult(String varCategory, String varName,
                           String currentTopValue, double currentTopFreqRatio,
                           String baselineTopValue, double baselineTopFreqRatio,
                           boolean enumDrift, boolean topValueChanged) {
            this(varCategory, varName, currentTopValue, currentTopFreqRatio,
                    baselineTopValue, baselineTopFreqRatio, enumDrift, topValueChanged, true);
        }
    }

    /**
     * 检测枚举漂移。
     * enum_drift = |currentTopFreqRatio - baselineTopFreqRatio| > threshold
     * top_value_changed = !Objects.equals(currentTopValue, baselineTopValue)
     */
    public static DriftResult detect(
            String varCategory, String varName,
            String currentTopValue, double currentTopFreqRatio,
            String baselineTopValue, double baselineTopFreqRatio,
            double threshold) {
        boolean drift = Math.abs(currentTopFreqRatio - baselineTopFreqRatio) > threshold;
        boolean changed = !Objects.equals(currentTopValue, baselineTopValue);
        return new DriftResult(varCategory, varName,
                currentTopValue, currentTopFreqRatio,
                baselineTopValue, baselineTopFreqRatio,
                drift, changed);
    }
}

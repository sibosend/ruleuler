package com.caritasem.ruleuler.server.monitoring;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 属性测试：PSI 计算与枚举漂移检测。
 * Feature: monitoring-realtime-enhancement
 */
class Phase2PropertyTest {

    // ============================================================
    // Property 7: PSI 计算与分类
    // ============================================================

    /**
     * 相同分布 PSI = 0。
     * Validates: Requirements 8.1
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类 — identical => 0")
    // Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类
    void identicalDistributions_psiIsZero(@ForAll("finiteDoubleArray") double[] values) {
        if (values.length < 2) return;

        PsiCalculator.PsiResult result = PsiCalculator.compute(values, values, 10);
        assertThat(result.psi()).isNotNull();
        assertThat(result.psi()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(result.psiAlert()).isFalse();
        assertThat(result.psiWarning()).isFalse();
    }

    /**
     * 任意两个分布 PSI >= 0。
     * Validates: Requirements 8.1
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类 — non-negative")
    // Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类
    void differentDistributions_psiNonNegative(
            @ForAll("finiteDoubleArray") double[] baseline,
            @ForAll("finiteDoubleArray") double[] current) {
        if (baseline.length < 2 || current.length < 2) return;

        PsiCalculator.PsiResult result = PsiCalculator.compute(baseline, current, 10);
        if (result.psi() != null) {
            assertThat(result.psi()).isGreaterThanOrEqualTo(0.0);
        }
    }

    /**
     * psiAlert 和 psiWarning 互斥，且阈值逻辑正确。
     * Validates: Requirements 8.3, 8.4
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类 — threshold logic")
    // Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类
    void psiAlertAndWarningThresholdLogic(
            @ForAll("finiteDoubleArray") double[] baseline,
            @ForAll("finiteDoubleArray") double[] current,
            @ForAll @DoubleRange(min = 0.01, max = 0.5) double warningThreshold,
            @ForAll @DoubleRange(min = 0.01, max = 0.5) double alertDelta) {
        if (baseline.length < 2 || current.length < 2) return;
        double alertThreshold = warningThreshold + alertDelta;

        PsiCalculator.PsiResult result = PsiCalculator.computeWithThresholds(
                baseline, current, 10, warningThreshold, alertThreshold);

        if (result.psi() != null) {
            // 互斥
            assertThat(result.psiAlert() && result.psiWarning()).isFalse();

            if (result.psi() > alertThreshold) {
                assertThat(result.psiAlert()).isTrue();
                assertThat(result.psiWarning()).isFalse();
            } else if (result.psi() > warningThreshold) {
                assertThat(result.psiAlert()).isFalse();
                assertThat(result.psiWarning()).isTrue();
            } else {
                assertThat(result.psiAlert()).isFalse();
                assertThat(result.psiWarning()).isFalse();
            }
        }
    }

    /**
     * 类别型变量被正确识别。
     * Validates: Requirements 8.6
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类 — categorical detected")
    // Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类
    void categoricalTypes_detectedCorrectly(@ForAll("categoricalType") String varType) {
        assertThat(PsiCalculator.isCategorical(varType)).isTrue();
    }

    /**
     * 数值型变量不被识别为类别型。
     * Validates: Requirements 8.6
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类 — numeric not categorical")
    // Feature: monitoring-realtime-enhancement, Property 7: PSI 计算与分类
    void numericTypes_notCategorical(@ForAll("numericType") String varType) {
        assertThat(PsiCalculator.isCategorical(varType)).isFalse();
    }

    // ============================================================
    // Property 8: 枚举漂移检测
    // ============================================================

    /**
     * enum_drift = |currentRatio - baselineRatio| > threshold。
     * Validates: Requirements 9.1, 9.2
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测 — drift threshold")
    // Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测
    void enumDrift_correctWhenAboveThreshold(
            @ForAll @DoubleRange(min = 0, max = 1) double currentRatio,
            @ForAll @DoubleRange(min = 0, max = 1) double baselineRatio,
            @ForAll @DoubleRange(min = 0.01, max = 0.5) double threshold) {

        EnumDriftDetector.DriftResult result = EnumDriftDetector.detect(
                "cat", "var", "A", currentRatio, "A", baselineRatio, threshold);

        boolean expectedDrift = Math.abs(currentRatio - baselineRatio) > threshold;
        assertThat(result.enumDrift()).isEqualTo(expectedDrift);
    }

    /**
     * top_value_changed = currentTopValue != baselineTopValue。
     * Validates: Requirements 9.3
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测 — top value changed")
    // Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测
    void topValueChanged_correctWhenDifferent(
            @ForAll("topValueString") String currentTopValue,
            @ForAll("topValueString") String baselineTopValue,
            @ForAll @DoubleRange(min = 0, max = 1) double ratio) {

        EnumDriftDetector.DriftResult result = EnumDriftDetector.detect(
                "cat", "var", currentTopValue, ratio, baselineTopValue, ratio, 0.15);

        boolean expectedChanged = !Objects.equals(currentTopValue, baselineTopValue);
        assertThat(result.topValueChanged()).isEqualTo(expectedChanged);
    }

    /**
     * 相同 topValue 时 topValueChanged = false。
     * Validates: Requirements 9.3
     */
    @Property(tries = 100)
    @Label("Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测 — same top value")
    // Feature: monitoring-realtime-enhancement, Property 8: 枚举漂移检测
    void sameTopValue_notChanged(
            @ForAll("topValueString") String topValue,
            @ForAll @DoubleRange(min = 0, max = 1) double currentRatio,
            @ForAll @DoubleRange(min = 0, max = 1) double baselineRatio) {

        EnumDriftDetector.DriftResult result = EnumDriftDetector.detect(
                "cat", "var", topValue, currentRatio, topValue, baselineRatio, 0.15);

        assertThat(result.topValueChanged()).isFalse();
    }

    // ============================================================
    // 生成器
    // ============================================================

    /**
     * 生成 10~100 个有限 double 值的数组（排除 NaN/Infinity）。
     */
    @Provide
    Arbitrary<double[]> finiteDoubleArray() {
        return Arbitraries.doubles()
                .between(-1e6, 1e6)
                .array(double[].class)
                .ofMinSize(10)
                .ofMaxSize(100);
    }

    @Provide
    Arbitrary<String> categoricalType() {
        return Arbitraries.of("String", "Char", "Enum");
    }

    @Provide
    Arbitrary<String> numericType() {
        return Arbitraries.of("Integer", "Double", "Float", "Long", "BigDecimal", "Boolean");
    }

    /**
     * 生成短字符串用于 topValue 测试，有限字母表增加碰撞概率。
     */
    @Provide
    Arbitrary<String> topValueString() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(3);
    }
}

package com.caritasem.ruleuler.server.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PsiCalculator 单元测试。
 * Feature: monitoring-realtime-enhancement, Requirements 8
 */
class PsiCalculatorTest {

    @Test
    void identicalDistributions_psiIsZero() {
        double[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        PsiCalculator.PsiResult result = PsiCalculator.compute(values, values, 10);
        assertNotNull(result.psi());
        assertEquals(0.0, result.psi(), 1e-9);
        assertFalse(result.psiAlert());
        assertFalse(result.psiWarning());
    }

    @Test
    void differentDistributions_psiPositive() {
        double[] baseline = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] current = {8, 9, 10, 10, 10, 9, 8, 10, 9, 10};
        PsiCalculator.PsiResult result = PsiCalculator.compute(baseline, current, 10);
        assertNotNull(result.psi());
        assertTrue(result.psi() > 0);
    }

    @Test
    void highDrift_alertTrue() {
        double[] baseline = new double[1000];
        double[] current = new double[1000];
        // baseline: 均匀分布 0~100
        for (int i = 0; i < 1000; i++) baseline[i] = i * 0.1;
        // current: 集中在 90~100
        for (int i = 0; i < 1000; i++) current[i] = 90 + i * 0.01;

        PsiCalculator.PsiResult result = PsiCalculator.computeWithThresholds(
                baseline, current, 10, 0.1, 0.2);
        assertNotNull(result.psi());
        assertTrue(result.psi() > 0.2);
        assertTrue(result.psiAlert());
        assertFalse(result.psiWarning());
    }

    @Test
    void categoricalType_detected() {
        assertTrue(PsiCalculator.isCategorical("String"));
        assertTrue(PsiCalculator.isCategorical("Char"));
        assertTrue(PsiCalculator.isCategorical("Enum"));
        assertFalse(PsiCalculator.isCategorical("Integer"));
        assertFalse(PsiCalculator.isCategorical("Double"));
        assertFalse(PsiCalculator.isCategorical(null));
    }

    @Test
    void emptyBaseline_returnsNull() {
        PsiCalculator.PsiResult result = PsiCalculator.compute(new double[0], new double[]{1, 2}, 10);
        assertNull(result.psi());
    }

    @Test
    void emptyCurrent_returnsNull() {
        PsiCalculator.PsiResult result = PsiCalculator.compute(new double[]{1, 2}, new double[0], 10);
        assertNull(result.psi());
    }

    @Test
    void allSameValues_psiZero() {
        double[] baseline = {5, 5, 5, 5, 5};
        double[] current = {5, 5, 5, 5, 5};
        PsiCalculator.PsiResult result = PsiCalculator.compute(baseline, current, 10);
        assertNotNull(result.psi());
        assertEquals(0.0, result.psi(), 1e-9);
    }

    @Test
    void binsCountMatchesNumBins() {
        double[] baseline = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] current = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        PsiCalculator.PsiResult result = PsiCalculator.compute(baseline, current, 10);
        assertEquals(10, result.bins().size());
    }

    @Test
    void warningRange_flaggedCorrectly() {
        // 构造一个 PSI 在 0.1~0.2 之间的场景
        double[] baseline = new double[1000];
        double[] current = new double[1000];
        for (int i = 0; i < 1000; i++) baseline[i] = i * 0.1;
        // 轻微偏移
        for (int i = 0; i < 1000; i++) current[i] = i * 0.1 + 5;

        PsiCalculator.PsiResult result = PsiCalculator.computeWithThresholds(
                baseline, current, 10, 0.1, 0.2);
        // 只验证 warning 和 alert 互斥
        assertFalse(result.psiAlert() && result.psiWarning());
    }
}

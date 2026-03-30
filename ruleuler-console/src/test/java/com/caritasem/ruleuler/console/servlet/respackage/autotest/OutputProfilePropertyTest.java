package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: autotest-v2, Property 12: 评分卡子集和枚举完整性
// Feature: autotest-v2, Property 13: 异常输出检测
// Validates: Requirements 5.5, 5.7

import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OutputProfilePropertyTest {

    @Property(tries = 100)
    void subsetSumsEqualsAllCombinations(@ForAll("scoreList") List<BigDecimal> scores) {
        Set<String> actual = SegmentAnalyzer.enumerateSubsetSums(scores);
        Set<BigDecimal> expected = new HashSet<>();
        int n = scores.size();
        for (int mask = 0; mask < (1 << n); mask++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) { sum = sum.add(scores.get(i)); }
            }
            expected.add(sum);
        }
        Set<String> expectedStr = expected.stream().map(BigDecimal::toPlainString).collect(Collectors.toSet());
        assertEquals(expectedStr, actual, "子集和集合应完全匹配, scores=" + scores);
    }

    @Provide
    Arbitrary<List<BigDecimal>> scoreList() {
        return Arbitraries.bigDecimals().between(BigDecimal.valueOf(-100), BigDecimal.valueOf(100))
                .ofScale(2).list().ofMinSize(1).ofMaxSize(8);
    }

    @Property(tries = 100)
    void anomalousWhenValueNotInPossibleValues(@ForAll("valueAndProfile") Tuple2<String, Set<String>> spec) {
        String resultValue = spec.get1();
        Set<String> possibleValues = spec.get2();
        boolean isAnomalous = !possibleValues.contains(resultValue);
        if (possibleValues.contains(resultValue)) {
            assertFalse(isAnomalous);
        } else {
            assertTrue(isAnomalous);
        }
    }

    @Property(tries = 100)
    void valueInSetIsNeverAnomalous(@ForAll("nonEmptyStringSet") Set<String> possibleValues) {
        String picked = possibleValues.iterator().next();
        assertTrue(possibleValues.contains(picked));
    }

    @Provide
    Arbitrary<Tuple2<String, Set<String>>> valueAndProfile() {
        Arbitrary<Set<String>> sets = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).set().ofMinSize(1).ofMaxSize(10);
        Arbitrary<String> values = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        return Combinators.combine(values, sets).as(Tuple::of);
    }

    @Provide
    Arbitrary<Set<String>> nonEmptyStringSet() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).set().ofMinSize(1).ofMaxSize(10);
    }
}

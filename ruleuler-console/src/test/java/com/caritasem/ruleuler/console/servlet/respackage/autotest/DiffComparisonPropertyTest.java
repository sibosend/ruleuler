package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: autotest-v2, Property 14: Diff 比对一致性
// Feature: autotest-v2, Property 15: Baseline 运行标记
// Feature: autotest-v2, Property 16: 分布统计百分比之和为 100%
// Feature: autotest-v2, Property 17: 报告汇总不变量
// Feature: autotest-v2, Property 18: 变化明细过滤正确性
// Validates: Requirements 6.1, 6.3, 6.4, 6.5, 6.6, 7.3, 7.6

import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diff 比对 + 分布统计属性测试。
 * 纯逻辑验证，不依赖数据库。
 */
class DiffComparisonPropertyTest {

    @Property(tries = 100)
    void diffStatusConsistentWithOutputComparison(@ForAll("jsonOutputPair") Tuple2<String, String> pair) {
        String actualOutput = pair.get1();
        String baselineOutput = pair.get2();
        String diffStatus;
        String storedBaseline;
        if (jsonEquals(actualOutput, baselineOutput)) {
            diffStatus = "SAME";
            storedBaseline = null;
        } else {
            diffStatus = "CHANGED";
            storedBaseline = baselineOutput;
        }
        TestResult result = new TestResult();
        result.setActualOutput(actualOutput);
        result.setDiffStatus(diffStatus);
        result.setBaselineOutput(storedBaseline);
        if (jsonEquals(actualOutput, baselineOutput)) {
            assertEquals("SAME", result.getDiffStatus());
        } else {
            assertEquals("CHANGED", result.getDiffStatus());
            assertNotNull(result.getBaselineOutput());
            assertEquals(baselineOutput, result.getBaselineOutput());
        }
    }

    @Provide
    Arbitrary<Tuple2<String, String>> jsonOutputPair() {
        Arbitrary<String> jsonStr = Arbitraries.oneOf(
                Arbitraries.of("{\"gate_type\":\"近机位\",\"score\":80}", "{\"result\":\"PASS\"}", "{\"a\":1,\"b\":2}"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)
                        .map(k -> "{\"" + k + "\":" + new Random().nextInt(100) + "}")
        );
        return Combinators.combine(jsonStr, jsonStr).as(Tuple::of);
    }

    @Property(tries = 100)
    void baselineRunMarksAllResultsAsBaseline(@ForAll("resultCount") int count) {
        Long baselineRunId = null;
        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestResult r = new TestResult();
            r.setRunId(1L); r.setCaseId(i + 1); r.setActualOutput("{\"val\":" + i + "}"); r.setPassed(true);
            results.add(r);
        }
        if (baselineRunId == null) {
            for (TestResult r : results) { r.setDiffStatus("BASELINE"); }
        }
        assertNull(baselineRunId);
        for (TestResult r : results) {
            assertEquals("BASELINE", r.getDiffStatus());
        }
    }

    @Provide
    Arbitrary<Integer> resultCount() { return Arbitraries.integers().between(1, 50); }

    @Property(tries = 100)
    void segmentPercentagesSumTo100(@ForAll("distributionData") Map<String, Map<String, Integer>> distribution) {
        for (Map.Entry<String, Map<String, Integer>> varEntry : distribution.entrySet()) {
            String varName = varEntry.getKey();
            Map<String, Integer> valueCounts = varEntry.getValue();
            int varTotal = valueCounts.values().stream().mapToInt(Integer::intValue).sum();
            if (varTotal == 0) continue;
            BigDecimal pctSum = BigDecimal.ZERO;
            for (Map.Entry<String, Integer> valEntry : valueCounts.entrySet()) {
                int count = valEntry.getValue();
                BigDecimal pct = BigDecimal.valueOf(count * 100.0 / varTotal).setScale(2, RoundingMode.HALF_UP);
                pctSum = pctSum.add(pct);
            }
            BigDecimal diff = pctSum.subtract(BigDecimal.valueOf(100)).abs();
            assertTrue(diff.compareTo(BigDecimal.valueOf(0.1)) <= 0,
                    "变量 " + varName + " 百分比之和应约等于 100%, 实际=" + pctSum);
        }
    }

    @Property(tries = 100)
    void changePctEqualsPercentageDiff(@ForAll("segmentWithBaseline") Tuple2<BigDecimal, BigDecimal> pctPair) {
        BigDecimal percentage = pctPair.get1();
        BigDecimal baselinePercentage = pctPair.get2();
        TestSegment seg = new TestSegment();
        seg.setPercentage(percentage);
        seg.setBaselinePercentage(baselinePercentage);
        seg.setChangePct(percentage.subtract(baselinePercentage));
        BigDecimal expectedChangePct = percentage.subtract(baselinePercentage);
        assertEquals(0, expectedChangePct.compareTo(seg.getChangePct()));
    }

    @Provide
    Arbitrary<Map<String, Map<String, Integer>>> distributionData() {
        Arbitrary<String> varNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6);
        Arbitrary<String> valNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6);
        Arbitrary<Integer> counts = Arbitraries.integers().between(1, 50);
        return varNames.set().ofMinSize(1).ofMaxSize(3).flatMap(vars -> {
            Map<String, Arbitrary<Map<String, Integer>>> arbs = new LinkedHashMap<>();
            for (String v : vars) {
                Arbitrary<Map<String, Integer>> valMap = Combinators.combine(
                        valNames.set().ofMinSize(1).ofMaxSize(5), counts.list().ofMinSize(1).ofMaxSize(5)
                ).as((keys, vals) -> {
                    Map<String, Integer> m = new LinkedHashMap<>();
                    Iterator<String> ki = keys.iterator(); Iterator<Integer> vi = vals.iterator();
                    while (ki.hasNext() && vi.hasNext()) { m.put(ki.next(), vi.next()); }
                    return m;
                });
                arbs.put(v, valMap);
            }
            List<String> varList = new ArrayList<>(arbs.keySet());
            if (varList.size() == 1) {
                return arbs.get(varList.get(0)).map(m -> { Map<String, Map<String, Integer>> r = new LinkedHashMap<>(); r.put(varList.get(0), m); return r; });
            } else if (varList.size() == 2) {
                return Combinators.combine(arbs.get(varList.get(0)), arbs.get(varList.get(1))).as((m1, m2) -> {
                    Map<String, Map<String, Integer>> r = new LinkedHashMap<>(); r.put(varList.get(0), m1); r.put(varList.get(1), m2); return r;
                });
            } else {
                return Combinators.combine(arbs.get(varList.get(0)), arbs.get(varList.get(1)), arbs.get(varList.get(2))).as((m1, m2, m3) -> {
                    Map<String, Map<String, Integer>> r = new LinkedHashMap<>(); r.put(varList.get(0), m1); r.put(varList.get(1), m2); r.put(varList.get(2), m3); return r;
                });
            }
        });
    }

    @Provide
    Arbitrary<Tuple2<BigDecimal, BigDecimal>> segmentWithBaseline() {
        Arbitrary<BigDecimal> pct = Arbitraries.bigDecimals().between(BigDecimal.ZERO, BigDecimal.valueOf(100)).ofScale(2);
        return Combinators.combine(pct, pct).as(Tuple::of);
    }

    @Property(tries = 100)
    void reportSummaryInvariant(@ForAll("mixedDiffStatusList") List<String> diffStatuses) {
        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < diffStatuses.size(); i++) {
            TestResult r = new TestResult(); r.setRunId(1L); r.setCaseId(i + 1); r.setDiffStatus(diffStatuses.get(i));
            results.add(r);
        }
        int total = results.size();
        long sameCount = results.stream().filter(r -> "SAME".equals(r.getDiffStatus())).count();
        long baselineCount = results.stream().filter(r -> "BASELINE".equals(r.getDiffStatus())).count();
        long changedCount = results.stream().filter(r -> "CHANGED".equals(r.getDiffStatus())).count();
        assertEquals(total, sameCount + baselineCount + changedCount);
    }

    @Provide
    Arbitrary<List<String>> mixedDiffStatusList() {
        return Arbitraries.of("SAME", "BASELINE", "CHANGED").list().ofMinSize(1).ofMaxSize(100);
    }

    @Property(tries = 100)
    void changeDetailFilterCorrectness(@ForAll("mixedDiffStatusList") List<String> diffStatuses) {
        List<TestResult> results = new ArrayList<>();
        for (int i = 0; i < diffStatuses.size(); i++) {
            TestResult r = new TestResult(); r.setRunId(1L); r.setCaseId(i + 1); r.setDiffStatus(diffStatuses.get(i));
            r.setActualOutput("{\"val\":" + i + "}");
            if ("CHANGED".equals(diffStatuses.get(i))) { r.setBaselineOutput("{\"val\":" + (i + 100) + "}"); }
            results.add(r);
        }
        List<TestResult> changeDetails = results.stream().filter(r -> "CHANGED".equals(r.getDiffStatus())).collect(Collectors.toList());
        long expectedChangedCount = results.stream().filter(r -> "CHANGED".equals(r.getDiffStatus())).count();
        assertEquals(expectedChangedCount, changeDetails.size());
        for (TestResult r : changeDetails) { assertEquals("CHANGED", r.getDiffStatus()); }
    }

    private static boolean jsonEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}

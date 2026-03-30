package com.caritasem.ruleuler.console.servlet.respackage.autotest;

// Feature: autotest-v2, Property 7-11
// Validates: Requirements 4.2, 4.3, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SegmentAnalyzerPropertyTest {

    private final SegmentAnalyzer analyzer = new SegmentAnalyzer();

    private static final Datatype[] NUMERIC_TYPES = {
            Datatype.Integer, Datatype.Double, Datatype.Long, Datatype.Float, Datatype.BigDecimal
    };

    @Property(tries = 100)
    void numericSegmentsCoverEntireAxis(@ForAll("numericCutPointSpec") Tuple2<List<BigDecimal>, Datatype> spec) {
        List<BigDecimal> cutPoints = spec.get1();
        Datatype datatype = spec.get2();
        int n = cutPoints.size();
        List<ConditionConstraint> constraints = cutPoints.stream()
                .map(cp -> makeConstraint("x", datatype, Op.GreaterThen, cp.toPlainString()))
                .collect(Collectors.toList());
        List<Segment> segments = analyzer.buildNumericSegments("x", datatype, constraints);
        assertEquals(n + 1, segments.size(), "N=" + n + " 个切分点应产生 " + (n + 1) + " 个区间");
        Segment first = segments.get(0);
        assertNull(first.getLowerOp(), "首区间无下界");
        assertNotNull(first.getUpperOp(), "首区间有上界");
        Segment last = segments.get(segments.size() - 1);
        assertNotNull(last.getLowerOp(), "末区间有下界");
        assertNull(last.getUpperOp(), "末区间无上界");
        for (int i = 0; i < segments.size() - 1; i++) {
            String upperVal = segments.get(i).getUpperValue();
            String lowerVal = segments.get(i + 1).getLowerValue();
            assertNotNull(upperVal); assertNotNull(lowerVal);
            assertEquals(new BigDecimal(upperVal).compareTo(new BigDecimal(lowerVal)), 0,
                    "相邻区间边界应连续: " + upperVal + " vs " + lowerVal);
        }
    }

    @Provide
    Arbitrary<Tuple2<List<BigDecimal>, Datatype>> numericCutPointSpec() {
        Arbitrary<Datatype> types = Arbitraries.of(NUMERIC_TYPES);
        Arbitrary<List<BigDecimal>> points = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(-1000), BigDecimal.valueOf(1000)).ofScale(2)
                .list().ofMinSize(1).ofMaxSize(8)
                .map(list -> list.stream().distinct().collect(Collectors.toList()))
                .filter(list -> !list.isEmpty());
        return Combinators.combine(points, types).as(Tuple::of);
    }

    @Property(tries = 100)
    void enumSegmentsContainOther(@ForAll("enumValueSet") Set<String> enumValues) {
        List<ConditionConstraint> constraints = enumValues.stream()
                .map(v -> makeConstraint("color", Datatype.String, Op.Equals, v)).collect(Collectors.toList());
        List<Segment> segments = analyzer.buildEnumSegments("color", Datatype.String, constraints);
        assertEquals(enumValues.size() + 1, segments.size());
        Set<String> segLabels = segments.stream().map(Segment::getLabel).collect(Collectors.toSet());
        for (String v : enumValues) { assertTrue(segLabels.contains(v)); }
        assertTrue(segLabels.contains("__OTHER__"));
        Segment otherSeg = segments.stream().filter(s -> "__OTHER__".equals(s.getLabel())).findFirst().orElseThrow();
        assertFalse(enumValues.contains(otherSeg.getRepresentative()));
    }

    @Provide
    Arbitrary<Set<String>> enumValueSet() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).set().ofMinSize(1).ofMaxSize(10);
    }

    @Property(tries = 100)
    void partitionCountEqualsProduct(@ForAll("segmentSizes") List<Integer> sizes) {
        Map<String, List<Segment>> segmentMap = new LinkedHashMap<>();
        for (int i = 0; i < sizes.size(); i++) {
            List<Segment> segs = new ArrayList<>();
            for (int j = 0; j < sizes.get(i); j++) {
                Segment s = new Segment(); s.setVariableName("var" + i); s.setLabel("seg" + j); s.setRepresentative(j);
                segs.add(s);
            }
            segmentMap.put("var" + i, segs);
        }
        long actual = analyzer.computePartitionCount(segmentMap);
        long expected = sizes.stream().mapToLong(Integer::longValue).reduce(1L, (a, b) -> a * b);
        assertEquals(expected, actual, "分区数应等于各变量区间数的乘积: " + sizes);
    }

    @Provide
    Arbitrary<List<Integer>> segmentSizes() {
        return Arbitraries.integers().between(1, 10).list().ofMinSize(1).ofMaxSize(5);
    }

    @Property(tries = 100)
    void strategyMatchesPartitionCount(@ForAll("positivePartitionCount") long n) {
        Strategy strategy = analyzer.chooseStrategy(n);
        if (n <= 100) { assertEquals(Strategy.FULL, strategy); }
        else if (n <= 10000) { assertEquals(Strategy.PAIRWISE, strategy); }
        else { assertEquals(Strategy.PAIRWISE_SAMPLED, strategy); }
    }

    @Provide
    Arbitrary<Long> positivePartitionCount() { return Arbitraries.longs().between(1, 1_000_000); }

    @Property(tries = 100)
    void representativeWithinInterval(@ForAll("numericInterval") Tuple2<BigDecimal, BigDecimal> interval) {
        BigDecimal lower = interval.get1();
        BigDecimal upper = interval.get2();
        Object rep = SegmentAnalyzer.computeRepresentative(lower, upper, Datatype.BigDecimal);
        assertNotNull(rep);
        BigDecimal repVal = (BigDecimal) rep;
        BigDecimal expectedMid = lower.add(upper).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        assertEquals(0, expectedMid.compareTo(repVal));
        assertTrue(repVal.compareTo(lower) >= 0);
        assertTrue(repVal.compareTo(upper) <= 0);
    }

    @Property(tries = 100)
    void representativeForHalfInfiniteInterval(@ForAll("boundValue") BigDecimal bound) {
        Object repUpper = SegmentAnalyzer.computeRepresentative(bound, null, Datatype.BigDecimal);
        assertEquals(0, bound.add(BigDecimal.ONE).compareTo((BigDecimal) repUpper));
        Object repLower = SegmentAnalyzer.computeRepresentative(null, bound, Datatype.BigDecimal);
        assertEquals(0, bound.subtract(BigDecimal.ONE).compareTo((BigDecimal) repLower));
    }

    @Provide
    Arbitrary<Tuple2<BigDecimal, BigDecimal>> numericInterval() {
        return Arbitraries.bigDecimals().between(BigDecimal.valueOf(-1000), BigDecimal.valueOf(1000)).ofScale(2)
                .tuple2().filter(t -> t.get1().compareTo(t.get2()) < 0);
    }

    @Provide
    Arbitrary<BigDecimal> boundValue() {
        return Arbitraries.bigDecimals().between(BigDecimal.valueOf(-1000), BigDecimal.valueOf(1000)).ofScale(2);
    }

    private static ConditionConstraint makeConstraint(String varName, Datatype dt, Op op, String value) {
        ConditionConstraint cc = new ConditionConstraint();
        cc.setVariableName(varName); cc.setDatatype(dt); cc.setOp(op); cc.setValue(value);
        return cc;
    }
}

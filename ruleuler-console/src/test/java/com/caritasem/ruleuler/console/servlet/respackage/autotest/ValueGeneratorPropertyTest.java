package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ValueGeneratorPropertyTest {

    private final ValueGenerator generator = new ValueGenerator();

    private static final Set<Op> NUMERIC_OPS = EnumSet.of(
            Op.Equals, Op.NotEquals, Op.GreaterThen, Op.GreaterThenEquals,
            Op.LessThen, Op.LessThenEquals, Op.Null, Op.NotNull, Op.In, Op.NotIn);

    private static final Set<Op> STRING_OPS = EnumSet.of(
            Op.Equals, Op.EqualsIgnoreCase, Op.NotEquals, Op.NotEqualsIgnoreCase,
            Op.Contain, Op.NotContain, Op.StartWith, Op.NotStartWith,
            Op.EndWith, Op.NotEndWith, Op.Match, Op.NotMatch,
            Op.In, Op.NotIn, Op.Null, Op.NotNull);

    private static final Set<Op> BOOLEAN_OPS = EnumSet.of(Op.Equals, Op.NotEquals, Op.Null, Op.NotNull);

    private static final Set<Op> DATE_OPS = EnumSet.of(
            Op.Equals, Op.NotEquals, Op.GreaterThen, Op.GreaterThenEquals,
            Op.LessThen, Op.LessThenEquals, Op.Null, Op.NotNull);

    private static final Datatype[] NUMERIC_TYPES = {
            Datatype.Integer, Datatype.Double, Datatype.Long, Datatype.Float, Datatype.BigDecimal};

    @Provide
    Arbitrary<ConditionConstraint> validConstraint() {
        return Arbitraries.of(
                Datatype.String, Datatype.Integer, Datatype.Double,
                Datatype.Long, Datatype.Float, Datatype.BigDecimal,
                Datatype.Boolean, Datatype.Date
        ).flatMap(dt -> {
            Set<Op> validOps = opsForDatatype(dt);
            return Arbitraries.of(validOps).map(op -> {
                ConditionConstraint cc = new ConditionConstraint();
                cc.setVariableName("testVar"); cc.setOp(op); cc.setDatatype(dt); cc.setValue(randomValue(dt));
                return cc;
            });
        });
    }

    private Set<Op> opsForDatatype(Datatype dt) {
        switch (dt) {
            case Integer: case Double: case Long: case Float: case BigDecimal: return NUMERIC_OPS;
            case Boolean: return BOOLEAN_OPS;
            case Date: return DATE_OPS;
            case String: return STRING_OPS;
            default: return EnumSet.of(Op.Equals);
        }
    }

    private String randomValue(Datatype dt) {
        Random rng = new Random();
        switch (dt) {
            case Integer: case Long: return String.valueOf(1 + rng.nextInt(100));
            case Double: case Float: case BigDecimal: return String.valueOf(1 + rng.nextInt(100));
            case Boolean: return rng.nextBoolean() ? "true" : "false";
            case Date: return "2024-06-15 12:00:00";
            case String: default:
                int len = 5 + rng.nextInt(6);
                StringBuilder sb = new StringBuilder(len);
                for (int i = 0; i < len; i++) { sb.append((char) ('a' + rng.nextInt(26))); }
                return sb.toString();
        }
    }

    // Feature: auto-test-generator, Property 5: 命中数据满足约束
    // Validates: Requirements 3.1, 3.3, 3.5, 3.6, 3.7

    @Property(tries = 100)
    void hitValueSatisfiesConstraint(@ForAll("validConstraint") ConditionConstraint cc) {
        Object hitValue = generator.generateHitValue(cc);
        assertTrue(satisfies(hitValue, cc.getOp(), cc.getValue(), cc.getDatatype()),
                "Hit value should satisfy constraint: op=" + cc.getOp() + " dt=" + cc.getDatatype()
                        + " threshold=" + cc.getValue() + " hitValue=" + hitValue);
    }

    // Feature: auto-test-generator, Property 6: 不命中数据精确翻转
    // Validates: Requirements 3.2, 3.4

    @Property(tries = 100)
    void missValueDoesNotSatisfyConstraint(@ForAll("validConstraint") ConditionConstraint cc) {
        Object missValue = generator.generateMissValue(cc);
        assertFalse(satisfies(missValue, cc.getOp(), cc.getValue(), cc.getDatatype()),
                "Miss value should NOT satisfy constraint: op=" + cc.getOp() + " dt=" + cc.getDatatype()
                        + " threshold=" + cc.getValue() + " missValue=" + missValue);
    }

    private boolean satisfies(Object value, Op op, String threshold, Datatype dt) {
        if (op == Op.Null) return value == null;
        if (op == Op.NotNull) return value != null;
        switch (dt) {
            case Integer: case Double: case Long: case Float: case BigDecimal: return numericSatisfies(value, op, threshold);
            case String: return stringSatisfies(value, op, threshold);
            case Boolean: return booleanSatisfies(value, op, threshold);
            case Date: return dateSatisfies(value, op, threshold);
            default: return false;
        }
    }

    private boolean numericSatisfies(Object value, Op op, String threshold) {
        BigDecimal actual = new BigDecimal(value.toString());
        switch (op) {
            case Equals: case EqualsIgnoreCase: return actual.compareTo(new BigDecimal(threshold.trim())) == 0;
            case NotEquals: case NotEqualsIgnoreCase: return actual.compareTo(new BigDecimal(threshold.trim())) != 0;
            case GreaterThen: return actual.compareTo(new BigDecimal(threshold.trim())) > 0;
            case GreaterThenEquals: return actual.compareTo(new BigDecimal(threshold.trim())) >= 0;
            case LessThen: return actual.compareTo(new BigDecimal(threshold.trim())) < 0;
            case LessThenEquals: return actual.compareTo(new BigDecimal(threshold.trim())) <= 0;
            case In: return actual.compareTo(new BigDecimal(threshold.split(",")[0].trim())) == 0;
            case NotIn: return actual.compareTo(new BigDecimal(threshold.split(",")[0].trim())) != 0;
            default: return false;
        }
    }

    private boolean stringSatisfies(Object value, Op op, String threshold) {
        String actual = (String) value;
        switch (op) {
            case Equals: return actual.equals(threshold);
            case EqualsIgnoreCase: return actual.equalsIgnoreCase(threshold);
            case NotEquals: return !actual.equals(threshold);
            case NotEqualsIgnoreCase: return !actual.equalsIgnoreCase(threshold);
            case Contain: return actual.contains(threshold);
            case NotContain: return !actual.contains(threshold);
            case StartWith: return actual.startsWith(threshold);
            case NotStartWith: return !actual.startsWith(threshold);
            case EndWith: return actual.endsWith(threshold);
            case NotEndWith: return !actual.endsWith(threshold);
            case Match: return actual.matches(threshold);
            case NotMatch: return !actual.matches(threshold);
            case In: return actual.equals(threshold.split(",")[0].trim());
            case NotIn: return !actual.equals(threshold.split(",")[0].trim());
            case GreaterThen: return actual.compareTo(threshold) > 0;
            case GreaterThenEquals: return actual.compareTo(threshold) >= 0;
            case LessThen: return actual.compareTo(threshold) < 0;
            case LessThenEquals: return actual.compareTo(threshold) <= 0;
            default: return false;
        }
    }

    private boolean booleanSatisfies(Object value, Op op, String threshold) {
        boolean actual = (Boolean) value;
        boolean expected = Boolean.parseBoolean(threshold);
        switch (op) {
            case Equals: return actual == expected;
            case NotEquals: return actual != expected;
            default: return false;
        }
    }

    private boolean dateSatisfies(Object value, Op op, String threshold) {
        Date actual = (Date) value;
        Date expected = parseDate(threshold);
        int cmp = actual.compareTo(expected);
        switch (op) {
            case Equals: return cmp == 0;
            case NotEquals: return cmp != 0;
            case GreaterThen: return cmp > 0;
            case GreaterThenEquals: return cmp >= 0;
            case LessThen: return cmp < 0;
            case LessThenEquals: return cmp <= 0;
            default: return false;
        }
    }

    private Date parseDate(String value) {
        try { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value.trim()); }
        catch (Exception e) {
            try { return new SimpleDateFormat("yyyy-MM-dd").parse(value.trim()); }
            catch (Exception e2) { throw new RuntimeException("Cannot parse date: " + value, e2); }
        }
    }
}

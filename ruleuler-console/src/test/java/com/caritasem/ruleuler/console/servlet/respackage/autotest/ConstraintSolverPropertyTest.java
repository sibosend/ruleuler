package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;

import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintSolverPropertyTest {

    private final ConstraintSolver solver = new ConstraintSolver();

    private static final Datatype[] NUMERIC_TYPES = {
            Datatype.Integer, Datatype.Double, Datatype.Long,
            Datatype.Float, Datatype.BigDecimal
    };

    private ConditionConstraint makeConstraint(String varName, Op op, String value, Datatype dt) {
        ConditionConstraint cc = new ConditionConstraint();
        cc.setVariableName(varName);
        cc.setOp(op);
        cc.setValue(value);
        cc.setDatatype(dt);
        return cc;
    }

    // Feature: auto-test-generator, Property 7: 多约束交集求解
    // Validates: Requirements 3.8

    @Provide
    Arbitrary<List<ConditionConstraint>> nonContradictoryPair() {
        Arbitrary<Datatype> dtArb = Arbitraries.of(NUMERIC_TYPES);
        Arbitrary<Integer> lowerValArb = Arbitraries.integers().between(1, 30);
        Arbitrary<Op> lowerOpArb = Arbitraries.of(Op.GreaterThen, Op.GreaterThenEquals);
        Arbitrary<Op> upperOpArb = Arbitraries.of(Op.LessThen, Op.LessThenEquals);

        return Combinators.combine(dtArb, lowerValArb, lowerOpArb, upperOpArb)
                .flatAs((dt, lowerVal, lowerOp, upperOp) -> {
                    return Arbitraries.integers().between(lowerVal + 2, 100)
                            .map(upperVal -> {
                                ConditionConstraint lower = makeConstraint("x", lowerOp, String.valueOf(lowerVal), dt);
                                ConditionConstraint upper = makeConstraint("x", upperOp, String.valueOf(upperVal), dt);
                                return Arrays.asList(lower, upper);
                            });
                });
    }

    @Property(tries = 100)
    void solvedValueSatisfiesBothConstraints(@ForAll("nonContradictoryPair") List<ConditionConstraint> constraints) {
        Optional<Object> result = solver.solve(constraints);
        assertTrue(result.isPresent(), "Non-contradictory constraints should yield a value");
        BigDecimal solved = new BigDecimal(result.get().toString());
        for (ConditionConstraint cc : constraints) {
            BigDecimal threshold = new BigDecimal(cc.getValue().trim());
            switch (cc.getOp()) {
                case GreaterThen: assertTrue(solved.compareTo(threshold) > 0); break;
                case GreaterThenEquals: assertTrue(solved.compareTo(threshold) >= 0); break;
                case LessThen: assertTrue(solved.compareTo(threshold) < 0); break;
                case LessThenEquals: assertTrue(solved.compareTo(threshold) <= 0); break;
                default: fail("Unexpected op: " + cc.getOp());
            }
        }
    }

    // Feature: auto-test-generator, Property 8: 矛盾约束检测
    // Validates: Requirements 3.9

    @Provide
    Arbitrary<List<ConditionConstraint>> contradictoryPair() {
        Arbitrary<Datatype> dtArb = Arbitraries.of(NUMERIC_TYPES);
        Arbitrary<Integer> xArb = Arbitraries.integers().between(50, 100);
        return Combinators.combine(dtArb, xArb)
                .flatAs((dt, x) -> {
                    return Arbitraries.integers().between(1, x)
                            .map(y -> {
                                ConditionConstraint gt = makeConstraint("x", Op.GreaterThen, String.valueOf(x), dt);
                                ConditionConstraint lt = makeConstraint("x", Op.LessThen, String.valueOf(y), dt);
                                return Arrays.asList(gt, lt);
                            });
                });
    }

    @Property(tries = 100)
    void contradictoryConstraintsReturnEmpty(@ForAll("contradictoryPair") List<ConditionConstraint> constraints) {
        Optional<Object> result = solver.solve(constraints);
        assertTrue(result.isEmpty(),
                "Contradictory constraints (GreaterThen " + constraints.get(0).getValue()
                        + " AND LessThen " + constraints.get(1).getValue() + ") should return empty");
    }
}

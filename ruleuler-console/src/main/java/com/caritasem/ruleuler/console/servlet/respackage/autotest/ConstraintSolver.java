package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;

import java.math.BigDecimal;
import java.util.*;

public class ConstraintSolver {

    private static final BigDecimal RANGE_MIN = BigDecimal.valueOf(Long.MIN_VALUE / 2);
    private static final BigDecimal RANGE_MAX = BigDecimal.valueOf(Long.MAX_VALUE / 2);
    private static final int MAX_SEARCH = 1000;

    private final ValueGenerator valueGenerator = new ValueGenerator();

    /**
     * 对同一变量的多个约束求交集，返回满足所有约束的值。
     * @return Optional.empty() 表示约束矛盾（不可达路径）
     */
    public Optional<Object> solve(List<ConditionConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return Optional.empty();
        }

        Datatype dt = constraints.get(0).getDatatype();

        if (isNumericType(dt)) {
            return solveNumeric(constraints, dt);
        } else {
            return solveNonNumeric(constraints);
        }
    }

    private Optional<Object> solveNumeric(List<ConditionConstraint> constraints, Datatype dt) {
        BigDecimal lower = RANGE_MIN;
        BigDecimal upper = RANGE_MAX;
        Set<BigDecimal> excluded = new HashSet<>();

        for (ConditionConstraint c : constraints) {
            BigDecimal val = new BigDecimal(c.getValue().trim());
            switch (c.getOp()) {
                case GreaterThen:
                    lower = lower.max(val.add(BigDecimal.ONE));
                    break;
                case GreaterThenEquals:
                    lower = lower.max(val);
                    break;
                case LessThen:
                    upper = upper.min(val.subtract(BigDecimal.ONE));
                    break;
                case LessThenEquals:
                    upper = upper.min(val);
                    break;
                case Equals:
                    lower = lower.max(val);
                    upper = upper.min(val);
                    break;
                case NotEquals:
                    excluded.add(val);
                    break;
                default:
                    break;
            }
        }

        if (lower.compareTo(upper) > 0) {
            return Optional.empty();
        }

        // 在 [lower, upper] 中找一个不在 excluded 中的值
        BigDecimal candidate = lower;
        int searched = 0;
        while (excluded.contains(candidate) && candidate.compareTo(upper) <= 0 && searched < MAX_SEARCH) {
            candidate = candidate.add(BigDecimal.ONE);
            searched++;
        }

        if (candidate.compareTo(upper) > 0 || excluded.contains(candidate)) {
            return Optional.empty();
        }

        return Optional.of(convertNumeric(candidate, dt));
    }

    private Optional<Object> solveNonNumeric(List<ConditionConstraint> constraints) {
        // 简化策略：用第一个约束生成命中值，检查是否满足所有约束
        Object candidate = valueGenerator.generateHitValue(constraints.get(0));

        for (int i = 1; i < constraints.size(); i++) {
            if (!satisfies(candidate, constraints.get(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }

    private boolean satisfies(Object value, ConditionConstraint constraint) {
        Op op = constraint.getOp();
        String expected = constraint.getValue();
        Datatype dt = constraint.getDatatype();

        if (op == Op.Null) return value == null;
        if (op == Op.NotNull) return value != null;
        if (value == null) return false;

        switch (dt) {
            case String:
                return satisfiesString(value.toString(), op, expected);
            case Boolean:
                return satisfiesBoolean((Boolean) value, op, expected);
            default:
                return false;
        }
    }

    private boolean satisfiesString(String val, Op op, String expected) {
        switch (op) {
            case Equals:             return val.equals(expected);
            case NotEquals:          return !val.equals(expected);
            case Contain:            return val.contains(expected);
            case NotContain:         return !val.contains(expected);
            case StartWith:          return val.startsWith(expected);
            case NotStartWith:       return !val.startsWith(expected);
            case EndWith:            return val.endsWith(expected);
            case NotEndWith:         return !val.endsWith(expected);
            default:                 return false;
        }
    }

    private boolean satisfiesBoolean(Boolean val, Op op, String expected) {
        boolean exp = java.lang.Boolean.parseBoolean(expected);
        switch (op) {
            case Equals:    return val == exp;
            case NotEquals: return val != exp;
            default:        return false;
        }
    }

    private Object convertNumeric(BigDecimal bd, Datatype dt) {
        switch (dt) {
            case Integer:    return bd.intValue();
            case Double:     return bd.doubleValue();
            case Long:       return bd.longValue();
            case Float:      return bd.floatValue();
            case BigDecimal: return bd;
            default:         return bd;
        }
    }

    private boolean isNumericType(Datatype dt) {
        return dt == Datatype.Integer || dt == Datatype.Double || dt == Datatype.Long
                || dt == Datatype.Float || dt == Datatype.BigDecimal;
    }
}

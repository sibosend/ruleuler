package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ValueGenerator {

    public Object generateHitValue(ConditionConstraint constraint) {
        Op op = constraint.getOp();
        Datatype dt = constraint.getDatatype();
        String value = constraint.getValue();

        if (op == Op.Null) return null;
        if (op == Op.NotNull) return defaultNonNull(dt);

        switch (dt) {
            case Integer:
            case Double:
            case Long:
            case Float:
            case BigDecimal:
                return convertNumeric(numericHit(op, value), dt);
            case String:
                return stringHit(op, value);
            case Boolean:
                return booleanHit(op, value);
            case Date:
                return dateHit(op, value);
            default:
                return value;
        }
    }

    public Object generateMissValue(ConditionConstraint constraint) {
        Op op = constraint.getOp();
        Datatype dt = constraint.getDatatype();
        String value = constraint.getValue();

        if (op == Op.Null) return defaultNonNull(dt);
        if (op == Op.NotNull) return null;

        switch (dt) {
            case Integer:
            case Double:
            case Long:
            case Float:
            case BigDecimal:
                return convertNumeric(numericMiss(op, value), dt);
            case String:
                return stringMiss(op, value);
            case Boolean:
                return booleanMiss(op, value);
            case Date:
                return dateMiss(op, value);
            default:
                return value;
        }
    }

    private Object defaultNonNull(Datatype dt) {
        switch (dt) {
            case Integer: return 0;
            case Double: return 0.0;
            case Long: return 0L;
            case Float: return 0.0f;
            case BigDecimal: return BigDecimal.ZERO;
            case Boolean: return false;
            case Date: return new Date();
            case String:
            default:
                return "";
        }
    }

    // ---- Numeric ----

    private BigDecimal numericHit(Op op, String value) {
        switch (op) {
            case GreaterThen:           return parseNumeric(value).add(BigDecimal.ONE);
            case GreaterThenEquals:      return parseNumeric(value);
            case LessThen:              return parseNumeric(value).subtract(BigDecimal.ONE);
            case LessThenEquals:         return parseNumeric(value);
            case Equals:
            case EqualsIgnoreCase:       return parseNumeric(value);
            case NotEquals:
            case NotEqualsIgnoreCase:    return parseNumeric(value).add(BigDecimal.ONE);
            case In:                     return parseNumeric(firstItem(value));
            case NotIn:                  return parseNumeric(firstItem(value)).add(BigDecimal.ONE);
            default:                     return parseNumeric(value);
        }
    }

    private BigDecimal numericMiss(Op op, String value) {
        switch (op) {
            case GreaterThen:           return parseNumeric(value);
            case GreaterThenEquals:      return parseNumeric(value).subtract(BigDecimal.ONE);
            case LessThen:              return parseNumeric(value);
            case LessThenEquals:         return parseNumeric(value).add(BigDecimal.ONE);
            case Equals:
            case EqualsIgnoreCase:       return parseNumeric(value).add(BigDecimal.ONE);
            case NotEquals:
            case NotEqualsIgnoreCase:    return parseNumeric(value);
            case In:                     return parseNumeric(firstItem(value)).add(BigDecimal.ONE);
            case NotIn:                  return parseNumeric(firstItem(value));
            default:                     return parseNumeric(value);
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

    private BigDecimal parseNumeric(String value) {
        return new BigDecimal(value.trim());
    }

    // ---- String ----

    private String stringHit(Op op, String value) {
        switch (op) {
            case Equals:
            case EqualsIgnoreCase:       return value;
            case NotEquals:
            case NotEqualsIgnoreCase:    return value + "_x";
            case Contain:                return "prefix" + value + "suffix";
            case NotContain:             return "nomatch";
            case StartWith:              return value + "suffix";
            case NotStartWith:           return "x" + value;
            case EndWith:                return "prefix" + value;
            case NotEndWith:             return value + "x";
            case Match:                  return value;
            case NotMatch:               return "";
            case In:                     return firstItem(value);
            case NotIn:                  return value + "_x";
            default:                     return value;
        }
    }

    private String stringMiss(Op op, String value) {
        switch (op) {
            case Equals:
            case EqualsIgnoreCase:       return value + "_x";
            case NotEquals:
            case NotEqualsIgnoreCase:    return value;
            case Contain:                return "nomatch";
            case NotContain:             return "prefix" + value + "suffix";
            case StartWith:              return "x" + value;
            case NotStartWith:           return value + "suffix";
            case EndWith:                return value + "x";
            case NotEndWith:             return "prefix" + value;
            case Match:                  return "";
            case NotMatch:               return value;
            case In:                     return value + "_x";
            case NotIn:                  return firstItem(value);
            default:                     return value;
        }
    }

    // ---- Boolean ----

    private Boolean booleanHit(Op op, String value) {
        boolean parsed = java.lang.Boolean.parseBoolean(value);
        switch (op) {
            case Equals:    return parsed;
            case NotEquals: return !parsed;
            default:        return parsed;
        }
    }

    private Boolean booleanMiss(Op op, String value) {
        boolean parsed = java.lang.Boolean.parseBoolean(value);
        switch (op) {
            case Equals:    return !parsed;
            case NotEquals: return parsed;
            default:        return !parsed;
        }
    }

    // ---- Date ----

    private Date dateHit(Op op, String value) {
        Date d = parseDate(value);
        switch (op) {
            case GreaterThen:        return addDays(d, 1);
            case GreaterThenEquals:   return d;
            case LessThen:           return addDays(d, -1);
            case LessThenEquals:      return d;
            case Equals:             return d;
            case NotEquals:          return addDays(d, 1);
            default:                 return d;
        }
    }

    private Date dateMiss(Op op, String value) {
        Date d = parseDate(value);
        switch (op) {
            case GreaterThen:        return d;
            case GreaterThenEquals:   return addDays(d, -1);
            case LessThen:           return d;
            case LessThenEquals:      return addDays(d, 1);
            case Equals:             return addDays(d, 1);
            case NotEquals:          return d;
            default:                 return d;
        }
    }

    private Date parseDate(String value) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value.trim());
        } catch (ParseException e) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd").parse(value.trim());
            } catch (ParseException e2) {
                throw new RuntimeException("无法解析日期: " + value, e2);
            }
        }
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    // ---- Util ----

    private String firstItem(String commaSeparated) {
        String[] parts = commaSeparated.split(",");
        return parts[0].trim();
    }
}

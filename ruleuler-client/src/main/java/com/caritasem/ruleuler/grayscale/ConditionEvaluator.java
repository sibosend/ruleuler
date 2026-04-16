package com.caritasem.ruleuler.grayscale;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 轻量条件求值器，复用 REA Op 枚举操作符。
 * 不依赖 AssertorEvaluator（需要完整 session 上下文）。
 */
public class ConditionEvaluator {

    public record ConditionItem(String left, String op, String right) {}

    /**
     * 评估所有条件（AND 关系）。任一不满足返回 false。
     * @param conditions 条件列表
     * @param body 入参 {categoryName: {field: value, ...}, ...}
     */
    public static boolean evaluate(List<ConditionItem> conditions, Map<String, Object> body) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (ConditionItem cond : conditions) {
            if (!evalSingle(cond, body)) return false;
        }
        return true;
    }

    private static boolean evalSingle(ConditionItem cond, Map<String, Object> body) {
        Object leftVal = resolveLeft(cond.left(), body);
        return switch (cond.op()) {
            case "Equals" -> eq(leftVal, cond.right());
            case "NotEquals" -> !eq(leftVal, cond.right());
            case "GreaterThen" -> compare(leftVal, cond.right()) > 0;
            case "GreaterThenEquals" -> compare(leftVal, cond.right()) >= 0;
            case "LessThen" -> compare(leftVal, cond.right()) < 0;
            case "LessThenEquals" -> compare(leftVal, cond.right()) <= 0;
            case "In" -> in(leftVal, cond.right());
            case "NotIn" -> !in(leftVal, cond.right());
            case "Contain" -> contains(leftVal, cond.right());
            case "NotContain" -> !contains(leftVal, cond.right());
            case "StartWith" -> startsWith(leftVal, cond.right());
            case "NotStartWith" -> !startsWith(leftVal, cond.right());
            case "EndWith" -> endsWith(leftVal, cond.right());
            case "NotEndWith" -> !endsWith(leftVal, cond.right());
            case "Null" -> leftVal == null;
            case "NotNull" -> leftVal != null;
            case "Match" -> leftVal != null && leftVal.toString().matches(cond.right());
            case "NotMatch" -> leftVal == null || !leftVal.toString().matches(cond.right());
            case "EqualsIgnoreCase" -> leftVal != null && leftVal.toString().equalsIgnoreCase(cond.right());
            case "NotEqualsIgnoreCase" -> leftVal == null || !leftVal.toString().equalsIgnoreCase(cond.right());
            default -> false;
        };
    }

    /** 从 body 中按 category.fieldName 取值 */
    @SuppressWarnings("unchecked")
    private static Object resolveLeft(String path, Map<String, Object> body) {
        String[] parts = path.split("\\.", 2);
        if (parts.length < 2) return body.get(path);
        Object category = body.get(parts[0]);
        if (category instanceof Map) return ((Map<String, Object>) category).get(parts[1]);
        return null;
    }

    private static boolean eq(Object left, String right) {
        if (left == null) return right == null || right.isEmpty();
        return left.toString().equals(right);
    }

    private static int compare(Object left, String right) {
        if (left == null) return -1;
        try {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right));
        } catch (NumberFormatException e) {
            return left.toString().compareTo(right);
        }
    }

    private static boolean in(Object left, String right) {
        if (left == null) return false;
        String leftStr = left.toString();
        for (String s : right.split(",")) {
            if (leftStr.equals(s.trim())) return true;
        }
        return false;
    }

    private static boolean contains(Object left, String right) {
        if (left == null) return false;
        return left.toString().contains(right);
    }

    private static boolean startsWith(Object left, String right) {
        if (left == null) return false;
        return left.toString().startsWith(right);
    }

    private static boolean endsWith(Object left, String right) {
        if (left == null) return false;
        return left.toString().endsWith(right);
    }
}

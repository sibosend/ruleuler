package com.caritasem.ruleuler.grayscale;

import java.math.BigDecimal;
import java.util.*;
/**
 * 轻量 REA script 条件求值器。
 * 输入格式为 REA 文本语法，例如：
 *   FlightInfo.level == "VIP"
 *   FlightInfo.score > 5 AND FlightInfo.type In ("A","B")
 *   FlightInfo.name Contain "test" OR FlightInfo.flag != false
 *
 * 支持 AND / OR 组合，支持括号分组。
 */
public class ConditionEvaluator {

    /** 一元操作符（不需要右侧值） */
    private static final Set<String> UNARY_OPS = Set.of("Null", "NotNull");

    /** 文本操作符 → 内部操作符名 */
    private static final Map<String, String> TEXT_OP_MAP = new HashMap<>();
    static {
        TEXT_OP_MAP.put("==", "Equals");
        TEXT_OP_MAP.put("!=", "NotEquals");
        TEXT_OP_MAP.put(">", "GreaterThen");
        TEXT_OP_MAP.put(">=", "GreaterThenEquals");
        TEXT_OP_MAP.put("<", "LessThen");
        TEXT_OP_MAP.put("<=", "LessThenEquals");
        TEXT_OP_MAP.put("Contain", "Contain");
        TEXT_OP_MAP.put("NotContain", "NotContain");
        TEXT_OP_MAP.put("In", "In");
        TEXT_OP_MAP.put("NotIn", "NotIn");
        TEXT_OP_MAP.put("Match", "Match");
        TEXT_OP_MAP.put("NotMatch", "NotMatch");
        TEXT_OP_MAP.put("StartWith", "StartWith");
        TEXT_OP_MAP.put("NotStartWith", "NotStartWith");
        TEXT_OP_MAP.put("EndWith", "EndWith");
        TEXT_OP_MAP.put("NotEndWith", "NotEndWith");
        TEXT_OP_MAP.put("EqualsIgnoreCase", "EqualsIgnoreCase");
        TEXT_OP_MAP.put("NotEqualsIgnoreCase", "NotEqualsIgnoreCase");
        TEXT_OP_MAP.put("Null", "Null");
        TEXT_OP_MAP.put("NotNull", "NotNull");
    }

    // ---- public API ----

    /**
     * 评估 REA 条件表达式。
     * @param expression REA 文本表达式，如 {@code FlightInfo.level == "VIP" AND FlightInfo.score > 5}
     * @param body       入参 {categoryName: {field: value, ...}, ...}
     * @return 条件是否满足
     */
    public static boolean evaluate(String expression, Map<String, Object> body) {
        if (expression == null || expression.isBlank()) return true;
        try {
            List<Token> tokens = tokenize(expression);
            return parseOr(tokens, new int[]{0}, body);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- tokenizer ----

    private static List<Token> tokenize(String expr) {
        return tokenizeImpl(expr);
    }

    private static List<Token> tokenizeImpl(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();
        while (i < len) {
            // 跳过空白
            while (i < len && Character.isWhitespace(expr.charAt(i))) i++;
            if (i >= len) break;

            char c = expr.charAt(i);

            // 括号 — 先检查是否是 In 列表
            if (c == '(') {
                // 回溯看前一个 token 是否是 In/NotIn
                Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
                if (prev != null && ("In".equals(prev.value) || "NotIn".equals(prev.value))) {
                    // 读取直到匹配的 )
                    int start = i + 1;
                    int depth = 1;
                    i++;
                    while (i < len && depth > 0) {
                        if (expr.charAt(i) == '(') depth++;
                        else if (expr.charAt(i) == ')') depth--;
                        i++;
                    }
                    String listContent = expr.substring(start, i - 1);
                    tokens.add(new Token(TokenType.LIST, listContent));
                } else {
                    tokens.add(new Token(TokenType.LPAREN, "("));
                    i++;
                }
                continue;
            }

            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
                continue;
            }

            // 比较操作符
            if (c == '=' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OP, "=="));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OP, "!="));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OP, ">="));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.OP, "<="));
                i += 2;
                continue;
            }
            if (c == '>') { tokens.add(new Token(TokenType.OP, ">")); i++; continue; }
            if (c == '<') { tokens.add(new Token(TokenType.OP, "<")); i++; continue; }

            // 字符串字面量
            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i + 1;
                i++;
                while (i < len && expr.charAt(i) != quote) i++;
                tokens.add(new Token(TokenType.STRING, expr.substring(start, i)));
                i++; // skip closing quote
                continue;
            }

            // 数字（含负号）
            if (Character.isDigit(c) || (c == '-' && i + 1 < len && Character.isDigit(expr.charAt(i + 1)))) {
                int start = i;
                if (c == '-') i++;
                while (i < len && Character.isDigit(expr.charAt(i))) i++;
                if (i < len && expr.charAt(i) == '.') {
                    i++;
                    while (i < len && Character.isDigit(expr.charAt(i))) i++;
                }
                tokens.add(new Token(TokenType.NUMBER, expr.substring(start, i)));
                continue;
            }

            // 标识符 / 关键字
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '_' || expr.charAt(i) == '.')) {
                    i++;
                }
                String word = expr.substring(start, i);
                if ("AND".equals(word)) {
                    tokens.add(new Token(TokenType.AND, word));
                } else if ("OR".equals(word)) {
                    tokens.add(new Token(TokenType.OR, word));
                } else if ("true".equals(word) || "false".equals(word)) {
                    tokens.add(new Token(TokenType.BOOLEAN, word));
                } else if (TEXT_OP_MAP.containsKey(word)) {
                    tokens.add(new Token(TokenType.OP, word));
                } else {
                    tokens.add(new Token(TokenType.VAR, word));
                }
                continue;
            }

            // 跳过未知字符
            i++;
        }
        return tokens;
    }

    // ---- recursive descent parser ----

    /** OR 层（最低优先级） */
    private static boolean parseOr(List<Token> tokens, int[] pos, Map<String, Object> body) {
        boolean result = parseAnd(tokens, pos, body);
        while (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.OR) {
            pos[0]++; // skip OR
            boolean right = parseAnd(tokens, pos, body);
            result = result || right;
        }
        return result;
    }

    /** AND 层 */
    private static boolean parseAnd(List<Token> tokens, int[] pos, Map<String, Object> body) {
        boolean result = parseAtom(tokens, pos, body);
        while (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.AND) {
            pos[0]++; // skip AND
            boolean right = parseAtom(tokens, pos, body);
            result = result && right;
        }
        return result;
    }

    /** 原子：括号分组 或 单个条件 */
    private static boolean parseAtom(List<Token> tokens, int[] pos, Map<String, Object> body) {
        if (pos[0] >= tokens.size()) return true;

        Token t = tokens.get(pos[0]);
        if (t.type == TokenType.LPAREN) {
            pos[0]++; // skip (
            boolean result = parseOr(tokens, pos, body);
            if (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.RPAREN) {
                pos[0]++; // skip )
            }
            return result;
        }

        // 期望: VAR OP (VALUE | LIST)
        if (t.type != TokenType.VAR) return true; // 容错
        String leftPath = t.value;
        pos[0]++;

        if (pos[0] >= tokens.size()) return true;
        Token opToken = tokens.get(pos[0]);
        if (opToken.type != TokenType.OP) return true; // 容错
        String opName = TEXT_OP_MAP.getOrDefault(opToken.value, opToken.value);
        pos[0]++;

        // 一元操作符
        if (UNARY_OPS.contains(opName)) {
            Object leftVal = resolveLeft(leftPath, body);
            return switch (opName) {
                case "Null" -> leftVal == null;
                case "NotNull" -> leftVal != null;
                default -> false;
            };
        }

        // 取右侧值
        if (pos[0] >= tokens.size()) return false;
        Token rightToken = tokens.get(pos[0]);
        pos[0]++;

        Object leftVal = resolveLeft(leftPath, body);

        if (rightToken.type == TokenType.LIST) {
            // In / NotIn 列表
            boolean inResult = inList(leftVal, rightToken.value);
            return "NotIn".equals(opName) != inResult;
        }

        String rightVal = rightToken.value;
        return switch (opName) {
            case "Equals" -> eq(leftVal, rightVal);
            case "NotEquals" -> !eq(leftVal, rightVal);
            case "GreaterThen" -> compare(leftVal, rightVal) > 0;
            case "GreaterThenEquals" -> compare(leftVal, rightVal) >= 0;
            case "LessThen" -> compare(leftVal, rightVal) < 0;
            case "LessThenEquals" -> compare(leftVal, rightVal) <= 0;
            case "Contain" -> contains(leftVal, rightVal);
            case "NotContain" -> !contains(leftVal, rightVal);
            case "StartWith" -> startsWith(leftVal, rightVal);
            case "NotStartWith" -> !startsWith(leftVal, rightVal);
            case "EndWith" -> endsWith(leftVal, rightVal);
            case "NotEndWith" -> !endsWith(leftVal, rightVal);
            case "Match" -> leftVal != null && leftVal.toString().matches(rightVal);
            case "NotMatch" -> leftVal == null || !leftVal.toString().matches(rightVal);
            case "EqualsIgnoreCase" -> leftVal != null && leftVal.toString().equalsIgnoreCase(rightVal);
            case "NotEqualsIgnoreCase" -> leftVal == null || !leftVal.toString().equalsIgnoreCase(rightVal);
            default -> false;
        };
    }

    // ---- value resolution ----

    /** 从 body 中按 category.fieldName 取值 */
    @SuppressWarnings("unchecked")
    private static Object resolveLeft(String path, Map<String, Object> body) {
        String[] parts = path.split("\\.", 2);
        if (parts.length < 2) return body.get(path);
        Object category = body.get(parts[0]);
        if (category instanceof Map) return ((Map<String, Object>) category).get(parts[1]);
        return null;
    }

    // ---- comparison helpers ----

    private static boolean eq(Object left, String right) {
        if (left == null) return right == null || right.isEmpty();
        // 支持布尔比较
        if (left instanceof Boolean) return left.toString().equals(right);
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

    /** In 列表：rightStr 为 "item1,item2,item3" 或 "\"A\",\"B\"" */
    private static boolean inList(Object left, String rightStr) {
        if (left == null) return false;
        String leftStr = left.toString();
        // 解析逗号分隔，去掉引号和空格
        String[] items = rightStr.split(",");
        for (String item : items) {
            String trimmed = item.trim().replaceAll("^\"|\"$|^'|'$", "");
            if (leftStr.equals(trimmed)) return true;
        }
        return false;
    }

    // ---- token types ----

    private enum TokenType {
        VAR, OP, STRING, NUMBER, BOOLEAN, LIST,
        AND, OR, LPAREN, RPAREN
    }

    private record Token(TokenType type, String value) {}
}

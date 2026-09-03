package com.caritasem.ruleuler.grayscale;

import java.math.BigDecimal;
import java.util.*;

/**
 * 轻量 REA 条件求值器。系统符号全大写。不支持函数。
 */
public class ConditionEvaluator {

    private static final Set<String> UNARY_OPS = Set.of("Null", "NotNull");

    private static final Map<String, String> TEXT_OP_MAP = new HashMap<>();
    static {
        TEXT_OP_MAP.put("==", "Equals");
        TEXT_OP_MAP.put("!=", "NotEquals");
        TEXT_OP_MAP.put(">", "GreaterThen");
        TEXT_OP_MAP.put(">=", "GreaterThenEquals");
        TEXT_OP_MAP.put("<", "LessThen");
        TEXT_OP_MAP.put("<=", "LessThenEquals");
        TEXT_OP_MAP.put("CONTAIN", "Contain");
        TEXT_OP_MAP.put("NOTCONTAIN", "NotContain");
        TEXT_OP_MAP.put("IN", "In");
        TEXT_OP_MAP.put("NOTIN", "NotIn");
        TEXT_OP_MAP.put("MATCH", "Match");
        TEXT_OP_MAP.put("NOTMATCH", "NotMatch");
        TEXT_OP_MAP.put("STARTWITH", "StartWith");
        TEXT_OP_MAP.put("NOTSTARTWITH", "NotStartWith");
        TEXT_OP_MAP.put("ENDWITH", "EndWith");
        TEXT_OP_MAP.put("NOTENDWITH", "NotEndWith");
        TEXT_OP_MAP.put("EQUALSIGNORECASE", "EqualsIgnoreCase");
        TEXT_OP_MAP.put("NOTEQUALSIGNORECASE", "NotEqualsIgnoreCase");
        TEXT_OP_MAP.put("NULL", "Null");
        TEXT_OP_MAP.put("NOTNULL", "NotNull");
    }

    private static final Map<String, String> LEGACY_OP = new HashMap<>();
    static {
        LEGACY_OP.put("Contain", "CONTAIN");
        LEGACY_OP.put("NotContain", "NOTCONTAIN");
        LEGACY_OP.put("In", "IN");
        LEGACY_OP.put("NotIn", "NOTIN");
        LEGACY_OP.put("Match", "MATCH");
        LEGACY_OP.put("NotMatch", "NOTMATCH");
        LEGACY_OP.put("StartWith", "STARTWITH");
        LEGACY_OP.put("Startwith", "STARTWITH");
        LEGACY_OP.put("NotStartWith", "NOTSTARTWITH");
        LEGACY_OP.put("NotStartwith", "NOTSTARTWITH");
        LEGACY_OP.put("EndWith", "ENDWITH");
        LEGACY_OP.put("Endwith", "ENDWITH");
        LEGACY_OP.put("NotEndWith", "NOTENDWITH");
        LEGACY_OP.put("NotEndwith", "NOTENDWITH");
        LEGACY_OP.put("EqualsIgnoreCase", "EQUALSIGNORECASE");
        LEGACY_OP.put("NotEqualsIgnoreCase", "NOTEQUALSIGNORECASE");
        LEGACY_OP.put("Null", "NULL");
        LEGACY_OP.put("NotNull", "NOTNULL");
    }

    public static boolean evaluate(String expression, Map<String, Object> body) {
        if (expression == null || expression.isBlank()) return true;
        List<Token> tokens = tokenize(expression);
        return parseOr(tokens, new int[]{0}, body);
    }

    public static List<Token> tokenize(String expr) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(expr.charAt(i))) i++;
            if (i >= len) break;

            char c = expr.charAt(i);

            if (c == '(') {
                Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
                if (prev != null && ("IN".equals(prev.value) || "NOTIN".equals(prev.value))) {
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
                } else if (prev != null && prev.type == TokenType.VAR) {
                    throw new IllegalArgumentException("灰度条件不支持函数");
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

            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i + 1;
                i++;
                while (i < len && expr.charAt(i) != quote) i++;
                tokens.add(new Token(TokenType.STRING, expr.substring(start, i)));
                i++;
                continue;
            }

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
                } else if ("TRUE".equals(word) || "FALSE".equals(word)) {
                    tokens.add(new Token(TokenType.BOOLEAN, "TRUE".equals(word) ? "true" : "false"));
                } else if ("true".equals(word) || "false".equals(word)) {
                    throw new IllegalArgumentException("请使用 TRUE/FALSE");
                } else if (TEXT_OP_MAP.containsKey(word)) {
                    tokens.add(new Token(TokenType.OP, word));
                } else if (LEGACY_OP.containsKey(word)) {
                    throw new IllegalArgumentException("请使用 " + LEGACY_OP.get(word));
                } else {
                    tokens.add(new Token(TokenType.VAR, word));
                }
                continue;
            }

            throw new IllegalArgumentException("意外的字符: '" + c + "'");
        }
        return tokens;
    }

    private static boolean parseOr(List<Token> tokens, int[] pos, Map<String, Object> body) {
        boolean result = parseAnd(tokens, pos, body);
        while (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.OR) {
            pos[0]++;
            boolean right = parseAnd(tokens, pos, body);
            result = result || right;
        }
        return result;
    }

    private static boolean parseAnd(List<Token> tokens, int[] pos, Map<String, Object> body) {
        boolean result = parseAtom(tokens, pos, body);
        while (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.AND) {
            pos[0]++;
            boolean right = parseAtom(tokens, pos, body);
            result = result && right;
        }
        return result;
    }

    private static boolean parseAtom(List<Token> tokens, int[] pos, Map<String, Object> body) {
        if (pos[0] >= tokens.size()) {
            throw new IllegalArgumentException("条件表达式不完整");
        }

        Token t = tokens.get(pos[0]);
        if (t.type == TokenType.LPAREN) {
            pos[0]++;
            boolean result = parseOr(tokens, pos, body);
            if (pos[0] < tokens.size() && tokens.get(pos[0]).type == TokenType.RPAREN) {
                pos[0]++;
            }
            return result;
        }

        if (t.type != TokenType.VAR) {
            throw new IllegalArgumentException("期望变量: " + t.value);
        }
        String leftPath = t.value;
        pos[0]++;

        if (pos[0] >= tokens.size() || tokens.get(pos[0]).type == TokenType.AND
                || tokens.get(pos[0]).type == TokenType.OR
                || tokens.get(pos[0]).type == TokenType.RPAREN) {
            Object leftVal = resolveLeft(leftPath, body);
            return eq(leftVal, "true");
        }

        Token opToken = tokens.get(pos[0]);
        if (opToken.type != TokenType.OP) {
            throw new IllegalArgumentException("期望操作符");
        }
        String opName = TEXT_OP_MAP.getOrDefault(opToken.value, opToken.value);
        pos[0]++;

        if (UNARY_OPS.contains(opName)) {
            Object leftVal = resolveLeft(leftPath, body);
            return switch (opName) {
                case "Null" -> leftVal == null;
                case "NotNull" -> leftVal != null;
                default -> false;
            };
        }

        if (pos[0] >= tokens.size()) {
            throw new IllegalArgumentException("期望右侧值");
        }
        Token rightToken = tokens.get(pos[0]);
        pos[0]++;

        Object leftVal = resolveLeft(leftPath, body);

        if (rightToken.type == TokenType.LIST) {
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
            default -> throw new IllegalArgumentException("未知操作符: " + opName);
        };
    }

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

    private static boolean inList(Object left, String rightStr) {
        if (left == null) return false;
        String leftStr = left.toString();
        String[] items = rightStr.split(",");
        for (String item : items) {
            String trimmed = item.trim().replaceAll("^\"|\"$|^'|'$", "");
            if (leftStr.equals(trimmed)) return true;
        }
        return false;
    }

    public enum TokenType {
        VAR, OP, STRING, NUMBER, BOOLEAN, LIST,
        AND, OR, LPAREN, RPAREN
    }

    public record Token(TokenType type, String value) {}
}

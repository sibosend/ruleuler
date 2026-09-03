package com.caritasem.ruleuler.server.grayscale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度 condition_expr 一次性 token 改写：旧 GUI/旧 REA 的 Contain/true
 * 升到全大写 CONTAIN/TRUE。禁止 SQL REPLACE（会误伤字符串字面量）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayscaleConditionExprMigrator implements ApplicationRunner {

    public static final String MIGRATION_NAME = "grayscale_condition_expr_uppercase_v2";

    private static final Map<String, String> OP_REWRITE = new HashMap<>();
    static {
        OP_REWRITE.put("Contain", "CONTAIN");
        OP_REWRITE.put("NotContain", "NOTCONTAIN");
        OP_REWRITE.put("In", "IN");
        OP_REWRITE.put("NotIn", "NOTIN");
        OP_REWRITE.put("Match", "MATCH");
        OP_REWRITE.put("NotMatch", "NOTMATCH");
        OP_REWRITE.put("StartWith", "STARTWITH");
        OP_REWRITE.put("Startwith", "STARTWITH");
        OP_REWRITE.put("NotStartWith", "NOTSTARTWITH");
        OP_REWRITE.put("NotStartwith", "NOTSTARTWITH");
        OP_REWRITE.put("EndWith", "ENDWITH");
        OP_REWRITE.put("Endwith", "ENDWITH");
        OP_REWRITE.put("NotEndWith", "NOTENDWITH");
        OP_REWRITE.put("NotEndwith", "NOTENDWITH");
        OP_REWRITE.put("EqualsIgnoreCase", "EQUALSIGNORECASE");
        OP_REWRITE.put("NotEqualsIgnoreCase", "NOTEQUALSIGNORECASE");
        OP_REWRITE.put("Null", "NULL");
        OP_REWRITE.put("NotNull", "NOTNULL");
    }

    private final JdbcTemplate jdbc;
    private final GrayscaleRuleDao grayscaleRuleDao;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ruleuler_migrator_log (
                  name varchar(100) NOT NULL PRIMARY KEY,
                  ran_at bigint NOT NULL
                )
                """);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ruleuler_migrator_log WHERE name=?",
                Integer.class, MIGRATION_NAME);
        if (n != null && n > 0) {
            return;
        }

        int updated = 0;
        for (var rule : grayscaleRuleDao.findAllWithCondition()) {
            String next = rewrite(rule.getConditionExpr());
            if (!next.equals(rule.getConditionExpr())) {
                grayscaleRuleDao.updateCondition(rule.getId(), next);
                updated++;
            }
        }
        jdbc.update("INSERT INTO ruleuler_migrator_log (name, ran_at) VALUES (?, ?)",
                MIGRATION_NAME, System.currentTimeMillis());
        log.info("灰度条件大写迁移完成, 更新 {} 条", updated);
    }

    /**
     * 按旧 tokenizer 识别 OP/BOOLEAN，只改这些 token。
     * 字符串字面量（含 {@code "true"} / {@code "Contain"}）不动。
     */
    public static String rewrite(String expr) {
        if (expr == null || expr.isBlank()) return expr;
        List<MigToken> tokens = tokenizeOld(expr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            MigToken t = tokens.get(i);
            sb.append(emit(t));
        }
        return sb.toString();
    }

    private static String emit(MigToken t) {
        return switch (t.type) {
            case STRING -> '"' + t.value + '"';
            case LIST -> '(' + t.value + ')';
            default -> t.value;
        };
    }

    private static List<MigToken> tokenizeOld(String expr) {
        List<MigToken> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(expr.charAt(i))) i++;
            if (i >= len) break;
            char c = expr.charAt(i);

            if (c == '(') {
                MigToken prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
                if (prev != null && ("In".equals(prev.value) || "NotIn".equals(prev.value)
                        || "IN".equals(prev.value) || "NOTIN".equals(prev.value))) {
                    int start = i + 1;
                    int depth = 1;
                    i++;
                    while (i < len && depth > 0) {
                        if (expr.charAt(i) == '(') depth++;
                        else if (expr.charAt(i) == ')') depth--;
                        i++;
                    }
                    tokens.add(new MigToken(MigType.LIST, expr.substring(start, i - 1)));
                } else {
                    tokens.add(new MigToken(MigType.OTHER, "("));
                    i++;
                }
                continue;
            }
            if (c == ')') {
                tokens.add(new MigToken(MigType.OTHER, ")"));
                i++;
                continue;
            }
            if (c == '=' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new MigToken(MigType.OTHER, "=="));
                i += 2;
                continue;
            }
            if (c == '!' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new MigToken(MigType.OTHER, "!="));
                i += 2;
                continue;
            }
            if (c == '>' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new MigToken(MigType.OTHER, ">="));
                i += 2;
                continue;
            }
            if (c == '<' && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(new MigToken(MigType.OTHER, "<="));
                i += 2;
                continue;
            }
            if (c == '>') { tokens.add(new MigToken(MigType.OTHER, ">")); i++; continue; }
            if (c == '<') { tokens.add(new MigToken(MigType.OTHER, "<")); i++; continue; }

            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i + 1;
                i++;
                while (i < len && expr.charAt(i) != quote) i++;
                tokens.add(new MigToken(MigType.STRING, expr.substring(start, i)));
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
                tokens.add(new MigToken(MigType.OTHER, expr.substring(start, i)));
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '_' || expr.charAt(i) == '.')) {
                    i++;
                }
                String word = expr.substring(start, i);
                if ("true".equals(word)) {
                    tokens.add(new MigToken(MigType.BOOLEAN, "TRUE"));
                } else if ("false".equals(word)) {
                    tokens.add(new MigToken(MigType.BOOLEAN, "FALSE"));
                } else if (OP_REWRITE.containsKey(word)) {
                    tokens.add(new MigToken(MigType.OP, OP_REWRITE.get(word)));
                } else {
                    tokens.add(new MigToken(MigType.OTHER, word));
                }
                continue;
            }
            i++;
        }
        return tokens;
    }

    private enum MigType { STRING, LIST, BOOLEAN, OP, OTHER }
    private record MigToken(MigType type, String value) {}
}

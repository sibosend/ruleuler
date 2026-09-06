package com.caritasem.ruleuler.grayscale;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    @Test
    void trueLiteral() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("flag", true));
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.flag == TRUE", body));
        assertFalse(ConditionEvaluator.evaluate("FlightInfo.flag == FALSE", body));
    }

    @Test
    void lowercaseTrueRejected() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("flag", true));
        assertThrows(IllegalArgumentException.class,
                () -> ConditionEvaluator.evaluate("FlightInfo.flag == true", body));
    }

    @Test
    void containUppercase() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("name", "hello-test"));
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.name CONTAIN \"test\"", body));
    }

    @Test
    void inUppercase() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("type", "A"));
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.type IN (\"A\",\"B\")", body));
    }

    @Test
    void functionThrows() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("name", "x"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ConditionEvaluator.evaluate("TRIM(FlightInfo.name) == \"x\"", body));
        assertTrue(ex.getMessage().contains("函数"));
    }

    @Test
    void tokenizePublic() {
        var tokens = ConditionEvaluator.tokenize("FlightInfo.level == \"VIP\"");
        assertFalse(tokens.isEmpty());
    }

    @Test
    void nullUnary() {
        Map<String, Object> inner = new java.util.HashMap<>();
        inner.put("gate", null);
        Map<String, Object> body = Map.of("FlightInfo", inner);
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.gate NULL", body));
        assertFalse(ConditionEvaluator.evaluate("FlightInfo.gate NOTNULL", body));
    }

    @Test
    void legacyNullStillAccepted() {
        Map<String, Object> inner = new java.util.HashMap<>();
        inner.put("gate", null);
        Map<String, Object> body = Map.of("FlightInfo", inner);
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.gate Null", body));
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.gate NULL", body));
        assertFalse(ConditionEvaluator.evaluate("FlightInfo.gate NotNull", body));
    }

    @Test
    void isnullPredicate() {
        Map<String, Object> inner = new java.util.HashMap<>();
        inner.put("gate", null);
        Map<String, Object> body = Map.of("FlightInfo", inner);
        assertTrue(ConditionEvaluator.evaluate("ISNULL(FlightInfo.gate)", body));
        assertFalse(ConditionEvaluator.evaluate("ISNOTNULL(FlightInfo.gate)", body));
    }

    @Test
    void isnullEmptyStringIsFalse() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("gate", ""));
        assertFalse(ConditionEvaluator.evaluate("ISNULL(FlightInfo.gate)", body));
        assertTrue(ConditionEvaluator.evaluate("ISNOTNULL(FlightInfo.gate)", body));
    }

    @Test
    void isnullMissingFieldIsTrue() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of());
        assertTrue(ConditionEvaluator.evaluate("ISNULL(FlightInfo.gate)", body));
    }

    @Test
    void arithmeticCompare() {
        Map<String, Object> body = Map.of(
                "FlightInfo", Map.of("score", 75, "bonus", 10),
                "threshold", 80);
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.score + 10 > 80", body));
        assertFalse(ConditionEvaluator.evaluate("FlightInfo.score + 10 > 90", body));
        assertTrue(ConditionEvaluator.evaluate("threshold < FlightInfo.score + FlightInfo.bonus", body));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ConditionEvaluator.evaluate("80 < FlightInfo.score + FlightInfo.bonus", body));
        assertTrue(ex.getMessage().contains("比较左边不能是数字"));
    }

    @Test
    void inListStillWorks() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("gate", "A1"));
        assertTrue(ConditionEvaluator.evaluate("FlightInfo.gate IN (\"A1\", \"A2\")", body));
        assertFalse(ConditionEvaluator.evaluate("FlightInfo.gate IN (\"B1\", \"B2\")", body));
    }

    @Test
    void absStillForbidden() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("score", 1));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ConditionEvaluator.evaluate("ABS(FlightInfo.score) + 1 > 0", body));
        assertTrue(ex.getMessage().contains("函数"));
    }
}

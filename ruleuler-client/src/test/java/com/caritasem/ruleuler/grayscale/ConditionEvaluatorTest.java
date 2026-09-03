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
    void legacyNullRejected() {
        Map<String, Object> body = Map.of("FlightInfo", Map.of("gate", "A"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ConditionEvaluator.evaluate("FlightInfo.gate Null", body));
        assertTrue(ex.getMessage().contains("NULL"));
    }
}

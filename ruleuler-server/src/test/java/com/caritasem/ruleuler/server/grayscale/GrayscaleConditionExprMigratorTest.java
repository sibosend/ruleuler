package com.caritasem.ruleuler.server.grayscale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrayscaleConditionExprMigratorTest {

    @Test
    void containBecomesUppercase() {
        assertEquals(
                "FlightInfo.name CONTAIN \"test\"",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.name Contain \"test\""));
    }

    @Test
    void trueBecomesUppercase() {
        assertEquals(
                "FlightInfo.flag != TRUE",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.flag != true"));
    }

    @Test
    void quotedTrueIsUnchanged() {
        assertEquals(
                "FlightInfo.name == \"true\"",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.name == \"true\""));
    }

    @Test
    void inListAndFalse() {
        String out = GrayscaleConditionExprMigrator.rewrite(
                "FlightInfo.type In (\"A\",\"B\") OR FlightInfo.flag != false");
        assertEquals("FlightInfo.type IN (\"A\",\"B\") OR FlightInfo.flag != FALSE", out);
    }

    @Test
    void alreadyUppercaseIdempotent() {
        String src = "FlightInfo.level == \"VIP\" AND FlightInfo.score > 5";
        assertEquals(src, GrayscaleConditionExprMigrator.rewrite(src));
    }

    @Test
    void nullBecomesUppercase() {
        assertEquals(
                "FlightInfo.gate NULL",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.gate Null"));
        assertEquals(
                "FlightInfo.gate NOTNULL",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.gate NotNull"));
    }

    @Test
    void quotedNullIsUnchanged() {
        assertEquals(
                "FlightInfo.name == \"Null\"",
                GrayscaleConditionExprMigrator.rewrite("FlightInfo.name == \"Null\""));
    }
}

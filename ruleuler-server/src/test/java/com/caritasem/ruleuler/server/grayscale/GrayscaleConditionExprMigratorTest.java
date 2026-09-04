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

    @Test
    void singleQuotePreservedVerbatim() {
        String out = GrayscaleConditionExprMigrator.rewrite(
                "FlightInfo.name Contain 'test' AND FlightInfo.level == 'VIP'");
        assertEquals("FlightInfo.name CONTAIN 'test' AND FlightInfo.level == 'VIP'", out);
    }

    @Test
    void doubleQuoteInsideSingleQuotedStringNotCorrupted() {
        // 值里含 "：重发射必须保留原词素，不能变成 "say "hi""
        String out = GrayscaleConditionExprMigrator.rewrite("FlightInfo.name == 'say \"hi\"'");
        assertEquals("FlightInfo.name == 'say \"hi\"'", out);
    }
}

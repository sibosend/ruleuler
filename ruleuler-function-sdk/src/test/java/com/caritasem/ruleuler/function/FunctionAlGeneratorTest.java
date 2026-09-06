package com.caritasem.ruleuler.function;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionAlGeneratorTest {

    @Test
    void xmlContainsPackageAndMethods() {
        var xml = FunctionAlGenerator.xml("geo-functions", "1.2.0", List.of(
                new FunctionAlGenerator.BeanDecl("geoFunctions", "地理函数", "com.ex.Geo",
                        List.of(new FunctionAlGenerator.MethodDecl("两地距离", "distanceKm", "Double",
                                List.of(new FunctionAlGenerator.Param("出发纬度", "Double")))))));
        assertTrue(xml.contains("function-package=\"geo-functions\""));
        assertTrue(xml.contains("function-version=\"1.2.0\""));
        assertTrue(xml.contains("id=\"geoFunctions\""));
        assertTrue(xml.contains("method-name=\"distanceKm\""));
    }

    @Test
    void rejectBadPackageName() {
        assertThrows(IllegalArgumentException.class, () ->
                FunctionAlGenerator.xml("geo.functions", "1", List.of()));
    }

    @Test
    void rejectOverload() {
        var methods = List.of(
                new FunctionAlGenerator.MethodDecl("a", "foo", "void", List.of()),
                new FunctionAlGenerator.MethodDecl("b", "foo", "void", List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                FunctionAlGenerator.xml("geo", "1", List.of(
                        new FunctionAlGenerator.BeanDecl("b", "B", "C", methods))));
    }

    @Test
    void rejectObjectParam() {
        assertThrows(IllegalArgumentException.class, () ->
                FunctionAlGenerator.assertParamType("java.lang.Object", "x"));
    }

    @Test
    void dateMustBeUtilDate() {
        assertThrows(IllegalArgumentException.class, () ->
                FunctionAlGenerator.assertParamType("java.sql.Date", "x"));
        FunctionAlGenerator.assertParamType("java.util.Date", "x");
    }
}

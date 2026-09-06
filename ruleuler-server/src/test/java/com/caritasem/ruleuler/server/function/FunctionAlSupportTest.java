package com.caritasem.ruleuler.server.function;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FunctionAlSupportTest {

    private final FunctionAlSupport support = new FunctionAlSupport(mock(JdbcTemplate.class));

    @Test
    void parseValid() {
        FunctionAlSupport.ParsedAl parsed = support.parse("""
                <action-library function-package="geo-functions" function-version="1.2.0">
                  <spring-bean id="geoFunctions" name="地理">
                    <method name="距离" method-name="distanceKm">
                      <parameter name="纬度" type="Double"/>
                    </method>
                  </spring-bean>
                </action-library>
                """);
        assertEquals("geo-functions", parsed.functionPackage());
        assertEquals("1.2.0", parsed.functionVersion());
        assertTrue(parsed.beanIds().contains("geoFunctions"));
    }

    @Test
    void rejectObjectParam() {
        assertThrows(IllegalArgumentException.class, () -> support.parse("""
                <action-library function-package="geo" function-version="1">
                  <spring-bean id="g" name="g">
                    <method name="x" method-name="x">
                      <parameter name="p" type="Object"/>
                    </method>
                  </spring-bean>
                </action-library>
                """));
    }

    @Test
    void rejectMissingPackage() {
        assertThrows(IllegalArgumentException.class, () -> support.parse("""
                <action-library>
                  <spring-bean id="g" name="g"></spring-bean>
                </action-library>
                """));
    }
}

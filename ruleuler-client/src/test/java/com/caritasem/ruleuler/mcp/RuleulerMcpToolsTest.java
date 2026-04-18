package com.caritasem.ruleuler.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.caritasem.ruleuler.dto.RuleExecutionResult;
import com.caritasem.ruleuler.service.RuleExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleulerMcpToolsTest {

    @Mock
    private RuleExecutionService ruleExecutionService;

    @InjectMocks
    private RuleulerMcpTools tools;

    @Test
    void executeRule_success_returnsStructuredJson() {
        Map<String, Object> data = Map.of("FlightInfo", Map.of("gate", "A12"));
        Map<String, Object> meta = Map.of(
                "executionId", "test-uuid",
                "packageId", "project/pkg",
                "route", "BASE"
        );
        when(ruleExecutionService.execute(eq("project"), eq("pkg"), eq("flow"), any()))
                .thenReturn(new RuleExecutionResult(200, "ok", data, meta));

        String result = tools.executeRule("project", "pkg", "flow",
                "{\"FlightInfo\":{\"aircraft_type\":\"A380\"}}");

        JSONObject json = JSON.parseObject(result);
        assertEquals(200, json.getIntValue("status"));
        assertEquals("test-uuid", json.getString("executionId"));
        assertEquals("project/pkg", json.getString("packageId"));
        assertEquals("BASE", json.getString("route"));
        assertNotNull(json.get("data"));
    }

    @Test
    void executeRule_failure_returnsErrorJson() {
        when(ruleExecutionService.execute(eq("project"), eq("pkg"), eq("flow"), any()))
                .thenReturn(new RuleExecutionResult(400, "未知变量类别: BadCat", null, null));

        String result = tools.executeRule("project", "pkg", "flow",
                "{\"BadCat\":{\"field\":\"val\"}}");

        JSONObject json = JSON.parseObject(result);
        assertEquals(400, json.getIntValue("status"));
        assertTrue(json.getString("error").contains("BadCat"));
    }

    @Test
    void executeRule_invalidJson_returns400() {
        String result = tools.executeRule("p", "k", "f", "not-json");

        JSONObject json = JSON.parseObject(result);
        assertEquals(400, json.getIntValue("status"));
        assertTrue(json.getString("error").contains("Invalid JSON"));
    }

    @Test
    void executeRule_serverError_returns500() {
        when(ruleExecutionService.execute(any(), any(), any(), any()))
                .thenReturn(new RuleExecutionResult(500, "NPE here", null, null));

        String result = tools.executeRule("p", "k", "f", "{\"A\":{}}");

        JSONObject json = JSON.parseObject(result);
        assertEquals(500, json.getIntValue("status"));
        assertTrue(json.getString("error").contains("NPE"));
    }
}

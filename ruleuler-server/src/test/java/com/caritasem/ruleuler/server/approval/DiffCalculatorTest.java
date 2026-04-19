package com.caritasem.ruleuler.server.approval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiffCalculatorTest {

    private final DiffCalculator calc = new DiffCalculator(null);

    @Test
    void reaShadowAttrDiff() throws Exception {
        String prev = """
            <rule-set>
              <rule name="rule1">
                <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="1"/></atom></and></if>
                <then><var-assign var="y" var-label="y"><value content="A"/></var-assign></then>
              </rule>
              <rule name="rule2">
                <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="2"/></atom></and></if>
                <then><var-assign var="y" var-label="y"><value content="B"/></var-assign></then>
              </rule>
            </rule-set>
            """;

        String curr = """
            <rule-set>
              <rule name="rule1">
                <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="1"/></atom></and></if>
                <then><var-assign var="y" var-label="y"><value content="A"/></var-assign></then>
              </rule>
              <rule name="rule2" shadow="true">
                <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="2"/></atom></and></if>
                <then><var-assign var="y" var-label="y"><value content="B"/></var-assign></then>
              </rule>
            </rule-set>
            """;

        String diff = calc.computeRuleDiff("/test/abc.rea.xml", prev, curr);
        System.out.println("DIFF: " + diff);

        // rule2 应该被检测为 MODIFIED，且 fields 里包含 shadow 变化
        assertTrue(diff.contains("rule2"), "应包含 rule2");
        assertTrue(diff.contains("shadow"), "fields 里应包含 shadow 属性变化");
        assertTrue(diff.contains("\"field\":\"shadow\""), "应有 shadow 字段");
    }

    @Test
    void reaExtractByRuleTag() throws Exception {
        String xml = """
            <rule-set>
              <rule name="rule1"><if><and></and></if><then></then></rule>
              <rule name="rule2" shadow="true"><if><and></and></if><then></then></rule>
            </rule-set>
            """;

        var rules = calc.extractRules("/test/abc.rea.xml", xml);
        assertEquals(2, rules.size(), "应提取出2个rule元素");
        assertTrue(rules.containsKey("rule1"));
        assertTrue(rules.containsKey("rule2"));
    }

    @Test
    void ruleAttrDiff_enabled() throws Exception {
        String prev = """
            <rule name="r" enabled="true">
              <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="1"/></atom></and></if>
              <then><var-assign var="y" var-label="y"><value content="A"/></var-assign></then>
            </rule>
            """;

        String curr = """
            <rule name="r" enabled="false">
              <if><and><atom op="Equals"><left var="x" var-label="x"/><value content="1"/></atom></and></if>
              <then><var-assign var="y" var-label="y"><value content="A"/></var-assign></then>
            </rule>
            """;

        String diff = calc.computeRuleDiff("/test/abc.rea.xml", prev, curr);
        System.out.println("DIFF: " + diff);
        assertTrue(diff.contains("\"field\":\"enabled\""), "应包含 enabled 字段变化");
        assertTrue(diff.contains("\"prev\":\"true\""), "prev 应为 true");
        assertTrue(diff.contains("\"curr\":\"false\""), "curr 应为 false");
    }
}

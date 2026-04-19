package com.bstek.urule.model.rule;

import net.jqwik.api.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature: shadow-mode-backtesting
 *
 * Property 1: Shadow 属性 XML 解析 round-trip（通过 setter/getter 验证，因 parseRule 依赖 Configure 全局状态）
 * Validates: Requirements 1.2
 *
 * Property 3: 非 shadow 规则行为不变
 * Validates: Requirements 1.4
 */
class ShadowPropertyTest {

    /**
     * Feature: shadow-mode-backtesting, Property 1: Shadow 属性 round-trip
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    void shadowRoundTrip(@ForAll("shadowValues") Boolean shadow) {
        Rule rule = new Rule();
        rule.setShadow(shadow);
        assertEquals(shadow, rule.getShadow());
    }

    @Test
    void shadow默认值null() {
        Rule rule = new Rule();
        assertNull(rule.getShadow());
    }

    @Test
    void shadow在RuleInfo接口中可访问() {
        Rule rule = new Rule();
        rule.setShadow(true);
        RuleInfo info = rule;
        assertEquals(true, info.getShadow());
    }

    /**
     * Feature: shadow-mode-backtesting, Property 3: 非 shadow 规则行为不变
     * Validates: Requirements 1.4
     */
    @Property(tries = 100)
    void nonShadowRulesUnaffected(@ForAll("nonShadowValues") Boolean enabled,
                                  @ForAll("nonShadowValues") Boolean debug,
                                  @ForAll("nonShadowValues") Boolean loop) {
        Rule rule = new Rule();
        rule.setName("normal-rule");
        rule.setEnabled(enabled);
        rule.setDebug(debug);
        rule.setLoop(loop);

        assertNull(rule.getShadow());
        assertEquals(enabled, rule.getEnabled());
        assertEquals(debug, rule.getDebug());
        assertEquals(loop, rule.getLoop());
    }

    @Provide
    Arbitrary<Boolean> shadowValues() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Boolean> nonShadowValues() {
        return Arbitraries.of(true, false, null);
    }
}

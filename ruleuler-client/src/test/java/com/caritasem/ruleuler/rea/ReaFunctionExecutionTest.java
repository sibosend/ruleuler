package com.caritasem.ruleuler.rea;

import com.bstek.urule.model.GeneralEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * REA 函数调用全量引擎执行测试。
 *
 * 用例来自 ruleuler-admin 导出的 rea-function-map.json（编译器真实输出），
 * 每个 REA 函数（内置 66 + 聚合 5 + 自定义 3）都在引擎里构建、执行、断言。
 * 改了函数配对表 / 编译器 → 先 npm run export:functionmap 再跑这里。
 */
class ReaFunctionExecutionTest {

    private static JsonNode CASES;

    @BeforeAll
    static void load() throws Exception {
        // 起一次引擎，提前暴露上下文装配问题
        ReaExecutionHarness.context();
        try (InputStream in = ReaFunctionExecutionTest.class.getResourceAsStream("/rea-function-map.json")) {
            assertNotNull(in, "rea-function-map.json 不在 test resources，先执行 npm run export:functionmap");
            CASES = new ObjectMapper().readTree(in).get("cases");
        }
    }

    @TestFactory
    Collection<DynamicTest> everyFunctionExecutes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : CASES) {
            String rea = c.get("rea").asText();
            tests.add(DynamicTest.dynamicTest(rea, () -> runCase(c)));
        }
        return tests;
    }

    private void runCase(JsonNode c) {
        String rea = c.get("rea").asText();
        String position = c.get("position").asText();
        String ifXml = "condition".equals(position) ? c.get("xml").asText() : ReaExecutionHarness.ALWAYS_TRUE_IF;
        String thenXml = "condition".equals(position) ? ReaExecutionHarness.FIRED_THEN : c.get("xml").asText();

        Map<String, GeneralEntity> facts = ReaExecutionHarness.defaultFacts();
        if (c.has("facts")) {
            ReaExecutionHarness.applyOverrides(facts, objectMapper().convertValue(c.get("facts"), Map.class));
        }
        ReaExecutionHarness.run(ReaExecutionHarness.ruleSet(ifXml, thenXml), facts);

        String path = c.has("path") ? c.get("path").asText() : "FlightInfo.result";
        Object actual = resolvePath(facts, path);
        JsonNode assertSpec = c.get("assert");
        String type = assertSpec.get("type").asText();
        String where = rea + " " + path;

        switch (type) {
            case "string" -> assertEquals(assertSpec.get("value").asText(), str(actual), where);
            case "number" -> {
                assertNotNull(actual, where + " 为 null");
                assertEquals(0, new BigDecimal(str(actual)).compareTo(new BigDecimal(assertSpec.get("value").asText())), where + " = " + actual);
            }
            case "approx" -> {
                assertNotNull(actual, where + " 为 null");
                double expected = assertSpec.get("value").asDouble();
                assertTrue(Math.abs(Double.parseDouble(str(actual)) - expected) < 1e-9, where + " = " + actual + "，期望 ≈" + expected);
            }
            case "nonnull" -> assertNotNull(actual, where + " 为 null");
            case "dateEquals" -> {
                assertNotNull(actual, where + " 为 null");
                assertEquals(assertSpec.get("value").asText(),
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date) actual), where);
            }
            case "listSize" -> assertEquals(assertSpec.get("value").asInt(), ((List<?>) actual).size(), where + " = " + actual);
            case "mapSize" -> assertEquals(assertSpec.get("value").asInt(), ((Map<?, ?>) actual).size(), where + " = " + actual);
            case "amountOf" -> assertEquals(0, new BigDecimal(str(((GeneralEntity) actual).get("amount")))
                    .compareTo(new BigDecimal(assertSpec.get("value").asText())), where);
            case "listFirstAmount" -> {
                List<?> list = (List<?>) actual;
                assertTrue(list.size() > 0, where + " 为空");
                assertEquals(0, new BigDecimal(str(((GeneralEntity) list.get(0)).get("amount")))
                        .compareTo(new BigDecimal(assertSpec.get("value").asText())), where);
            }
            default -> fail("未知断言类型: " + type);
        }
    }

    // ── 语义锁定（引擎怪癖，单独写死）──────────────────────────────
    // MONTH 0-based / WEEK 周日=1 / DAY→getay 已在 JSON 矩阵用例里断言（MONTH=8、WEEK=6、DAY=4）

    @Test
    void sortTypeOnlyChineseOrOneOrTrueMeansAsc() {
        // 引擎 asc() 只认 "1"/"true"/"正序"；传 "asc" 会按倒序排
        Map<String, GeneralEntity> facts = ReaExecutionHarness.defaultFacts();
        String xml = """
                <var-assign var-category="Order" var="sorted" var-label="排序" datatype="List" type="variable">
                    <value type="Method" bean-name="urule.listAction" bean-label="List集合" method-name="sort" method-label="集合排序"><parameter name="集合对象" type="List"><value var-category="Order" var="items" var-label="明细" datatype="List" type="Variable"/></parameter><parameter name="属性名" type="String"><value content="amount" type="Input"/></parameter><parameter name="排序方式" type="String"><value content="asc" type="Input"/></parameter></value>
                  </var-assign>""".stripIndent();
        facts.get("Order").put("sorted", null);
        ReaExecutionHarness.run(ReaExecutionHarness.ruleSet(ReaExecutionHarness.ALWAYS_TRUE_IF, xml), facts);
        List<?> sorted = (List<?>) facts.get("Order").get("sorted");
        assertEquals(300, ((GeneralEntity) sorted.get(0)).get("amount"), "\"asc\" 不是引擎的升序词面，被当倒序排");
    }

    @Test
    void substringIntegerInputCoerced() {
        // SUBSTRING 的 Integer 参数来自 Input 字面量 "0"/"3"，靠 <parameter type="Integer"> 强转
        Map<String, GeneralEntity> facts = ReaExecutionHarness.defaultFacts();
        String xml = """
                <var-assign var-category="FlightInfo" var="result" var-label="结果" datatype="Object" type="variable">
                    <value type="Method" bean-name="urule.stringAction" bean-label="字符串" method-name="substring" method-label="指定起始的字符串截取"><parameter name="目标字符串" type="String"><value content="ruleuler" type="Input"/></parameter><parameter name="开始位置" type="Integer"><value content="0" type="Input"/></parameter><parameter name="结束位置" type="Integer"><value content="3" type="Input"/></parameter></value>
                  </var-assign>""".stripIndent();
        ReaExecutionHarness.run(ReaExecutionHarness.ruleSet(ReaExecutionHarness.ALWAYS_TRUE_IF, xml), facts);
        assertEquals("rul", facts.get("FlightInfo").get("result"));
    }

    // ── helpers ───────────────────────────────────────────────

    private static Object resolvePath(Map<String, GeneralEntity> facts, String path) {
        String[] parts = path.split("\\.", 2);
        GeneralEntity entity = facts.get(parts[0]);
        return entity == null ? null : entity.get(parts[1]);
    }

    private static String str(Object v) {
        return String.valueOf(v);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

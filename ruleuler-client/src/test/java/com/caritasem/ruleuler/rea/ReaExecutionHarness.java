package com.caritasem.ruleuler.rea;

import com.bstek.urule.builder.KnowledgeBuilder;
import com.bstek.urule.builder.ResourceBase;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSessionFactory;
import com.bstek.urule.runtime.KnowledgeSession;
import com.caritasem.ruleuler.grayscale.SnapshotResourceProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REA 函数执行测试 harness：不起 Boot，纯 ClassPathXmlApplicationContext
 * 拉起引擎（照 SnapshotPackageBuilder 的构建方式）。
 * XML 内容经 SnapshotResourceProvider 以 dbr: 路径注入。
 */
public final class ReaExecutionHarness {

    public static final String VL_PATH = "dbr:/test/rea-func.vl.xml";
    public static final String AL_PATH = "dbr:/test/rea-func.al.xml";
    public static final String RS_PATH = "dbr:/test/rea-func.rs.xml";

    /** 与 ruleuler-admin functionExecSpec.ts 的固定事实保持一致 */
    public static final String VL_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <variable-library>
              <category name="FlightInfo" clazz="FlightInfo" type="GeneralEntity">
                <var name="name" label="名称" type="String" act="InOut"/>
                <var name="score" label="分数" type="Integer" act="InOut"/>
                <var name="tags" label="标签" type="List" act="InOut"/>
                <var name="extra" label="扩展" type="Map" act="InOut"/>
                <var name="is_international" label="国际" type="Boolean" act="InOut"/>
                <var name="departure" label="起飞时间" type="Date" act="InOut"/>
                <var name="result" label="结果" type="Object" act="InOut"/>
                <var name="tag_to_remove" label="待删元素" type="Integer" act="InOut"/>
              </category>
              <category name="Order" clazz="Order" type="GeneralEntity">
                <var name="items" label="明细" type="List" act="InOut"/>
                <!-- SUM/AVG/MAXOF/MINOF 的 property-name 由引擎在集合所属类别里解析 -->
                <var name="amount" label="金额" type="Integer" act="InOut"/>
                <var name="sorted" label="排序结果" type="List" act="InOut"/>
              </category>
            </variable-library>""";

    /** riskService 签名声明（bean-label 必须与 REA 编译输出的 bean-label 一致） */
    public static final String AL_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <action-library>
              <spring-bean id="riskService" name="RiskService">
                <method name="评分" method-name="score">
                  <parameter name="航司" type="String"/>
                  <parameter name="人数" type="Integer"/>
                </method>
                <method name="高风险" method-name="isHigh">
                  <parameter name="航司" type="String"/>
                </method>
                <method name="加标签" method-name="addTag">
                  <parameter name="集合" type="List"/>
                  <parameter name="标签" type="Object"/>
                </method>
              </spring-bean>
            </action-library>""";

    private static volatile ApplicationContext CONTEXT;

    private ReaExecutionHarness() {
    }

    static synchronized ApplicationContext context() {
        if (CONTEXT == null) {
            CONTEXT = new ClassPathXmlApplicationContext("rea-test-context.xml");
        }
        return CONTEXT;
    }

    public static Map<String, GeneralEntity> defaultFacts() {
        GeneralEntity flight = new GeneralEntity("FlightInfo");
        flight.put("name", "  ruleuler  ");
        flight.put("score", -42);
        flight.put("tags", new ArrayList<>(List.of(3, 1, 2)));
        Map<String, Object> extra = new HashMap<>();
        extra.put("k", "v");
        flight.put("extra", extra);
        flight.put("is_international", true);
        flight.put("tag_to_remove", 1);
        Calendar departure = new GregorianCalendar(2026, Calendar.SEPTEMBER, 4, 10, 30, 15);
        flight.put("departure", departure.getTime());

        GeneralEntity order = new GeneralEntity("Order");
        List<Object> items = new ArrayList<>();
        for (int amount : new int[]{100, 200, 300}) {
            GeneralEntity item = new GeneralEntity("OrderItem");
            item.put("amount", amount);
            items.add(item);
        }
        order.put("items", items);

        Map<String, GeneralEntity> facts = new LinkedHashMap<>();
        facts.put("FlightInfo", flight);
        facts.put("Order", order);
        return facts;
    }

    /** 按 category.field 覆盖事实字段 */
    @SuppressWarnings("unchecked")
    public static void applyOverrides(Map<String, GeneralEntity> facts, Map<String, Object> overrides) {
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            Map<String, Object> fields = (Map<String, Object>) e.getValue();
            GeneralEntity entity = facts.get(e.getKey());
            if (entity == null || fields == null) {
                continue;
            }
            for (Map.Entry<String, Object> f : fields.entrySet()) {
                if (f.getValue() instanceof List<?> list) {
                    entity.put(f.getKey(), new ArrayList<>(list));
                } else if (f.getValue() instanceof Map<?, ?> map) {
                    entity.put(f.getKey(), new HashMap<>((Map<String, Object>) map));
                } else {
                    entity.put(f.getKey(), f.getValue());
                }
            }
        }
    }

    /**
     * 构建 + 执行一个 ruleset XML，返回执行后的事实。
     * ruleSetXml 里引用 VL_PATH / AL_PATH 作为 import 路径。
     */
    public static Map<String, GeneralEntity> run(String ruleSetXml, Map<String, GeneralEntity> facts) {
        Map<String, String> resources = new HashMap<>();
        resources.put(VL_PATH, VL_XML);
        resources.put(AL_PATH, AL_XML);
        resources.put(RS_PATH, ruleSetXml);
        SnapshotResourceProvider.setSnapshot(resources);
        try {
            KnowledgeBuilder kb = context().getBean("urule.knowledgeBuilder", KnowledgeBuilder.class);
            ResourceBase rb = kb.newResourceBase();
            rb.addResource(RS_PATH, null);
            KnowledgePackage pkg;
            try {
                pkg = kb.buildKnowledgeBase(rb).getKnowledgePackage();
            } catch (IOException e) {
                throw new IllegalStateException("构建 KnowledgePackage 失败", e);
            }
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);
            for (GeneralEntity entity : facts.values()) {
                session.insert(entity);
            }
            session.fireRules(new HashMap<String, Object>());
            return facts;
        } finally {
            SnapshotResourceProvider.clearSnapshot();
        }
    }

    /** 拼一个最小 ruleset：import + 单规则。ifXml/thenXml 来自 JSON 导出的编译器真实输出。 */
    public static String ruleSet(String ifXml, String thenXml) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <rule-set>
                  <import-variable-library path="%s"/>
                  <import-action-library path="%s"/>
                  <rule name="rea-fn" salience="1">
                    %s
                    <then>
                      %s
                    </then>
                  </rule>
                </rule-set>""".formatted(VL_PATH, AL_PATH, ifXml, thenXml);
    }

    /** 恒真条件：FlightInfo.is_international == true */
    public static final String ALWAYS_TRUE_IF = """
            <if><and>
                  <atom op="Equals">
                    <left var-category="FlightInfo" var="is_international" var-label="国际" datatype="Boolean" type="variable"/>
                    <value content="true" type="Input"/>
                  </atom>
                </and></if>""";

    /** 条件用例的 then：命中则写 FIRED */
    public static final String FIRED_THEN = """
            <var-assign var-category="FlightInfo" var="result" var-label="结果" datatype="Object" type="variable">
                <value content="FIRED" type="Input"/>
              </var-assign>""";
}

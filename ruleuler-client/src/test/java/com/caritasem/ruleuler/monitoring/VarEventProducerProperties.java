package com.caritasem.ruleuler.monitoring;

import com.alibaba.fastjson2.JSONObject;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.library.ResourceLibrary;
import com.bstek.urule.model.library.variable.Variable;
import com.bstek.urule.model.library.variable.VariableCategory;
import com.bstek.urule.model.library.variable.VariableLibrary;
import com.bstek.urule.model.rete.Rete;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import net.jqwik.api.*;
import net.jqwik.api.Tuple.Tuple2;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

/**
 * VarEventProducer 属性测试。
 * Feature: variable-monitoring
 */
class VarEventProducerProperties {

    // 非复合类型列表（VarTypeMapper 不会 skip 的类型）
    private static final Datatype[] NON_COMPLEX = {
            Datatype.Integer, Datatype.Double, Datatype.Float, Datatype.Long,
            Datatype.BigDecimal, Datatype.Boolean, Datatype.String, Datatype.Char,
            Datatype.Enum, Datatype.Date
    };

    // ========== Property 3: 输入变量行生成 ==========

    /**
     * Property 3: 输入变量行生成
     * 随机请求体（多类别、多字段，全部为非复合类型），验证 input 行数 = 非复合类型字段总数。
     * Validates: Requirements 1.1, 1.2
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 3: 输入变量行生成")
    void inputRowCountEqualsNonComplexFieldCount(
            @ForAll("requestBodies") List<Tuple2<String, List<Tuple2<String, Datatype>>>> categories
    ) {
        // 构建变量定义和请求体
        List<VariableCategory> vcList = new ArrayList<>();
        Map<String, JSONObject> body = new LinkedHashMap<>();

        int expectedInputCount = 0;

        for (var catTuple : categories) {
            String catName = catTuple.get1();
            List<Tuple2<String, Datatype>> fields = catTuple.get2();

            VariableCategory vc = new VariableCategory();
            vc.setName(catName);
            JSONObject json = new JSONObject();

            for (var fieldTuple : fields) {
                String varName = fieldTuple.get1();
                Datatype dt = fieldTuple.get2();

                Variable v = new Variable();
                v.setName(varName);
                v.setType(dt);
                vc.addVariable(v);

                // 为每个字段生成一个合法的运行时值
                json.put(varName, sampleValue(dt));
                expectedInputCount++;
            }

            vcList.add(vc);
            body.put(catName, json);
        }

        // 构建 mock KnowledgePackage
        KnowledgePackage pkg = buildMockPackage(vcList);
        KnowledgeSession session = mock(KnowledgeSession.class);
        when(session.getParameters()).thenReturn(Collections.emptyMap());

        // 执行
        LinkedBlockingQueue<VarLogRow> queue = new LinkedBlockingQueue<>(10000);
        VarEventProducer producer = new VarEventProducer(queue);
        producer.produceSuccess("exec-1", "proj", "pkg", "flow",
                100L, body, Collections.emptyMap(), session, pkg);

        // 验证 input 行数
        List<VarLogRow> inputRows = queue.stream()
                .filter(r -> "input".equals(r.ioType()))
                .collect(Collectors.toList());

        assert inputRows.size() == expectedInputCount
                : "input 行数应等于非复合类型字段总数: expected=" + expectedInputCount + ", actual=" + inputRows.size();

        // 验证每行的 varCategory 和 varName 正确
        for (VarLogRow row : inputRows) {
            assert "input".equals(row.ioType()) : "io_type 应为 input";
            assert body.containsKey(row.varCategory())
                    : "varCategory 应在请求体中: " + row.varCategory();
            assert body.get(row.varCategory()).containsKey(row.varName())
                    : "varName 应在对应类别中: " + row.varName();
        }
    }

    // ========== Property 4: 输出变量行仅记录变更字段 ==========

    /**
     * Property 4: 输出变量行仅记录变更字段
     * 随机输入/输出实体对，验证 output 行仅包含变更字段。
     * Validates: Requirements 1.3, 1.4
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 4: 输出变量行仅记录变更字段")
    void outputRowsOnlyContainChangedFields(
            @ForAll("entityPairs") List<Tuple2<String, List<Tuple2<String, Datatype>>>> categories,
            @ForAll("changeMasks") List<List<Boolean>> changeMasks
    ) {
        // 确保 changeMasks 和 categories 对齐
        Assume.that(changeMasks.size() >= categories.size());
        for (int i = 0; i < categories.size(); i++) {
            Assume.that(changeMasks.get(i).size() >= categories.get(i).get2().size());
        }

        List<VariableCategory> vcList = new ArrayList<>();
        Map<String, JSONObject> body = new LinkedHashMap<>();
        Map<String, GeneralEntity> entities = new LinkedHashMap<>();
        Set<String> expectedChangedKeys = new HashSet<>(); // "category.varName"

        for (int ci = 0; ci < categories.size(); ci++) {
            var catTuple = categories.get(ci);
            String catName = catTuple.get1();
            List<Tuple2<String, Datatype>> fields = catTuple.get2();

            VariableCategory vc = new VariableCategory();
            vc.setName(catName);
            JSONObject inputJson = new JSONObject();
            GeneralEntity entity = new GeneralEntity(catName);

            for (int fi = 0; fi < fields.size(); fi++) {
                var fieldTuple = fields.get(fi);
                String varName = fieldTuple.get1();
                Datatype dt = fieldTuple.get2();

                Variable v = new Variable();
                v.setName(varName);
                v.setType(dt);
                vc.addVariable(v);

                Object inputVal = sampleValue(dt);
                inputJson.put(varName, inputVal);

                boolean changed = changeMasks.get(ci).get(fi);
                if (changed) {
                    // 输出值不同于输入值
                    entity.put(varName, sampleDifferentValue(dt, inputVal));
                    expectedChangedKeys.add(catName + "." + varName);
                } else {
                    // 输出值与输入值相同
                    entity.put(varName, inputVal);
                }
            }

            vcList.add(vc);
            body.put(catName, inputJson);
            entities.put(catName, entity);
        }

        // 构建 mock
        KnowledgePackage pkg = buildMockPackage(vcList);
        KnowledgeSession session = mock(KnowledgeSession.class);
        when(session.getParameters()).thenReturn(Collections.emptyMap());

        // 执行
        LinkedBlockingQueue<VarLogRow> queue = new LinkedBlockingQueue<>(10000);
        VarEventProducer producer = new VarEventProducer(queue);
        producer.produceSuccess("exec-2", "proj", "pkg", "flow",
                50L, body, entities, session, pkg);

        // 收集 output 行（排除 __param__）
        List<VarLogRow> outputRows = queue.stream()
                .filter(r -> "output".equals(r.ioType()))
                .filter(r -> !"__param__".equals(r.varCategory()))
                .collect(Collectors.toList());

        // 验证：output 行的 key 集合 == 预期变更字段集合
        Set<String> actualChangedKeys = outputRows.stream()
                .map(r -> r.varCategory() + "." + r.varName())
                .collect(Collectors.toSet());

        assert actualChangedKeys.equals(expectedChangedKeys)
                : "output 行应仅包含变更字段: expected=" + expectedChangedKeys + ", actual=" + actualChangedKeys;
    }

    // ========== 生成器 ==========

    /**
     * 生成随机请求体：1~3 个类别，每个类别 1~4 个非复合类型字段。
     * 类别名和字段名保证唯一。
     */
    @Provide
    Arbitrary<List<Tuple2<String, List<Tuple2<String, Datatype>>>>> requestBodies() {
        Arbitrary<Datatype> dtArb = Arbitraries.of(NON_COMPLEX);
        // 单个字段：(varName, Datatype)
        Arbitrary<Tuple2<String, Datatype>> fieldArb =
                Arbitraries.integers().between(0, 9999)
                        .flatMap(i -> dtArb.map(dt -> Tuple.of("var_" + i, dt)));

        // 单个类别：(catName, fields)
        Arbitrary<Tuple2<String, List<Tuple2<String, Datatype>>>> catArb =
                Arbitraries.integers().between(0, 999)
                        .flatMap(ci -> fieldArb.list().ofMinSize(1).ofMaxSize(4)
                                .map(fields -> deduplicateFields(fields))
                                .map(fields -> Tuple.of("Cat_" + ci, fields)));

        return catArb.list().ofMinSize(1).ofMaxSize(3)
                .map(this::deduplicateCategories);
    }

    /** entityPairs 复用 requestBodies 生成器 */
    @Provide
    Arbitrary<List<Tuple2<String, List<Tuple2<String, Datatype>>>>> entityPairs() {
        return requestBodies();
    }

    /** 为每个类别的每个字段生成 changed/unchanged 掩码 */
    @Provide
    Arbitrary<List<List<Boolean>>> changeMasks() {
        return Arbitraries.of(true, false)
                .list().ofMinSize(1).ofMaxSize(4)
                .list().ofMinSize(1).ofMaxSize(3);
    }

    // ========== 辅助方法 ==========

    /** 去重字段名 */
    private List<Tuple2<String, Datatype>> deduplicateFields(List<Tuple2<String, Datatype>> fields) {
        Set<String> seen = new HashSet<>();
        List<Tuple2<String, Datatype>> result = new ArrayList<>();
        for (var f : fields) {
            if (seen.add(f.get1())) result.add(f);
        }
        return result;
    }

    /** 去重类别名 */
    private List<Tuple2<String, List<Tuple2<String, Datatype>>>> deduplicateCategories(
            List<Tuple2<String, List<Tuple2<String, Datatype>>>> cats) {
        Set<String> seen = new HashSet<>();
        List<Tuple2<String, List<Tuple2<String, Datatype>>>> result = new ArrayList<>();
        for (var c : cats) {
            if (seen.add(c.get1())) result.add(c);
        }
        return result;
    }

    /** 根据 Datatype 生成一个合法的运行时值 */
    private Object sampleValue(Datatype dt) {
        return switch (dt) {
            case Integer -> 42;
            case Double -> 3.14;
            case Float -> 2.71f;
            case Long -> 100L;
            case BigDecimal -> new java.math.BigDecimal("9.99");
            case Boolean -> true;
            case String -> "hello";
            case Char -> 'A';
            case Enum -> "ACTIVE";
            case Date -> new java.util.Date(1700000000000L);
            default -> null;
        };
    }

    /** 生成一个与 inputVal 不同的值 */
    private Object sampleDifferentValue(Datatype dt, Object inputVal) {
        return switch (dt) {
            case Integer -> ((int) inputVal) + 1;
            case Double -> ((double) inputVal) + 1.0;
            case Float -> ((float) inputVal) + 1.0f;
            case Long -> ((long) inputVal) + 1L;
            case BigDecimal -> ((java.math.BigDecimal) inputVal).add(java.math.BigDecimal.ONE);
            case Boolean -> !((boolean) inputVal);
            case String -> inputVal + "_changed";
            case Char -> (char) (((char) inputVal) + 1);
            case Enum -> inputVal + "_V2";
            case Date -> new java.util.Date(((java.util.Date) inputVal).getTime() + 86400000L);
            default -> null;
        };
    }

    /** 构建 mock KnowledgePackage，包含指定的变量类别定义 */
    private KnowledgePackage buildMockPackage(List<VariableCategory> vcList) {
        VariableLibrary vl = new VariableLibrary();
        vl.setVariableCategories(vcList);
        ResourceLibrary lib = new ResourceLibrary(
                List.of(vl), List.of(), List.of());
        Rete rete = new Rete(List.of(), lib);

        KnowledgePackage pkg = mock(KnowledgePackage.class);
        when(pkg.getRete()).thenReturn(rete);
        when(pkg.getParameters()).thenReturn(Collections.emptyMap());
        return pkg;
    }
}

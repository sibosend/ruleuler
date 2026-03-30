package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.RuleException;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.library.variable.Variable;
import com.bstek.urule.model.library.variable.VariableCategory;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import com.bstek.urule.runtime.KnowledgeSessionFactory;
import com.bstek.urule.runtime.service.KnowledgePackageService;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class TestExecutor {

    private static final Logger log = LoggerFactory.getLogger(TestExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private KnowledgePackageService knowledgePackageService;
    private TestCaseGenerator testCaseGenerator;
    private TestResultDao testResultDao;

    /**
     * V2：基于已有用例包执行测试 + baseline 比对 + 分布统计。
     *
     * @param packId        用例包 ID
     * @param baselineRunId baseline run ID，null 表示本次为 baseline
     * @return 完成的 TestRun
     */
    public TestRun execute(long packId, Long baselineRunId) {
        // 1. 加载用例包
        TestCasePack pack = testResultDao.findPackById(packId);
        if (pack == null) {
            throw new RuleException("用例包不存在: packId=" + packId);
        }

        // 2. 加载用例
        List<TestCase> cases = testResultDao.findCasesByPackId(packId);
        if (cases.isEmpty()) {
            throw new RuleException("用例包为空: packId=" + packId);
        }

        // 3. 创建 TestRun
        String runType = (baselineRunId == null) ? "smoke" : "regression";
        TestRun run = new TestRun();
        run.setProject(pack.getProject());
        run.setPackageId(pack.getPackageId());
        run.setPackId(packId);
        run.setRunType(runType);
        run.setBaselineRunId(baselineRunId);
        run.setTotalCases(cases.size());
        run.setExecutedCases(0);
        run.setStatus("running");
        run.setStartedAt(System.currentTimeMillis());
        long runId = testResultDao.createRun(run);
        run.setId(runId);

        // 4. 构建知识包
        KnowledgePackage knowledgePackage;
        try {
            String project = pack.getProject();
            String pkgInfo = project.startsWith("/") ? project.substring(1) + "/" + pack.getPackageId()
                    : project + "/" + pack.getPackageId();
            knowledgePackage = knowledgePackageService.buildKnowledgePackage(pkgInfo);
        } catch (Exception e) {
            run.setStatus("failed");
            run.setFinishedAt(System.currentTimeMillis());
            testResultDao.updateRun(run);
            throw new RuleException("构建知识包失败: " + pack.getProject() + "/" + pack.getPackageId() + ", " + e.getMessage());
        }

        // 5. 逐条执行，每条后更新进度
        int passed = 0;
        int failed = 0;
        List<TestResult> results = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            TestCase tc = cases.get(i);
            TestResult result = executeCase(tc, runId, knowledgePackage);
            results.add(result);
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
            // 更新进度
            testResultDao.updateRunProgress(runId, i + 1);
        }

        // 6. 批量写入结果
        if (!results.isEmpty()) {
            testResultDao.batchInsertResults(results);
        }

        // 7. Baseline 比对
        if (baselineRunId == null) {
            // 本次为 baseline，所有结果标记 BASELINE
            setAllDiffStatus(runId, "BASELINE");
        } else {
            // 回归运行，逐条比对
            testResultDao.batchUpdateDiffStatus(runId, baselineRunId);
        }

        // 8. 统计输出分布，写入 ruleuler_test_segment
        List<TestSegment> segments = computeOutputDistribution(runId, baselineRunId);
        if (!segments.isEmpty()) {
            testResultDao.batchInsertSegments(segments);
        }

        // 9. 更新 run 状态
        run.setPassedCases(passed);
        run.setFailedCases(failed);
        run.setExecutedCases(cases.size());
        run.setStatus("completed");
        run.setFinishedAt(System.currentTimeMillis());
        testResultDao.updateRun(run);
        return run;
    }

    /**
     * 执行单条用例，返回 TestResult。
     * input_data 格式：{"类别.变量名": 值, "参数名": 值}
     * 按变量类别构建 GeneralEntity 并 insert，参数走 fireRules(params)。
     */
    private TestResult executeCase(TestCase tc, long runId, KnowledgePackage knowledgePackage) {
        TestResult result = new TestResult();
        result.setRunId(runId);
        result.setCaseId(tc.getId());
        result.setCreatedAt(System.currentTimeMillis());
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> inputData = parseInputData(tc.getInputData());
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

            // 按类别分组：有 "." 的是变量（类别.变量名），没有的是参数
            Map<String, Map<String, Object>> categoryVars = new LinkedHashMap<>();
            Map<String, Object> parameters = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : inputData.entrySet()) {
                String key = entry.getKey();
                int dotIdx = key.indexOf('.');
                if (dotIdx > 0) {
                    String category = key.substring(0, dotIdx);
                    String varName = key.substring(dotIdx + 1);
                    categoryVars.computeIfAbsent(category, k -> new LinkedHashMap<>())
                            .put(varName, entry.getValue());
                } else {
                    parameters.put(key, entry.getValue());
                }
            }

            // 按类别构建 GeneralEntity 并 insert
            Map<String, String> varCategoryMap = knowledgePackage.getVariableCateogoryMap();
            Map<String, GeneralEntity> entities = new LinkedHashMap<>();

            // 先为输入数据中的类别创建 entity
            for (Map.Entry<String, Map<String, Object>> catEntry : categoryVars.entrySet()) {
                String category = catEntry.getKey();
                String clazz = varCategoryMap.get(category);
                String finalClazz = (clazz != null) ? clazz : category;
                GeneralEntity entity = entities.computeIfAbsent(category, k -> new GeneralEntity(finalClazz));
                for (Map.Entry<String, Object> varEntry : catEntry.getValue().entrySet()) {
                    entity.put(varEntry.getKey(), varEntry.getValue());
                }
            }

            // 确保所有非参数类别都有 entity（输出类别也需要 insert 到 working memory）
            for (Map.Entry<String, String> vcEntry : varCategoryMap.entrySet()) {
                String category = vcEntry.getKey();
                if (VariableCategory.PARAM_CATEGORY.equals(category)) continue;
                if (!entities.containsKey(category)) {
                    entities.put(category, new GeneralEntity(vcEntry.getValue()));
                }
            }

            for (GeneralEntity entity : entities.values()) {
                session.insert(entity);
            }

            // 执行决策流或规则
            Map<String, com.bstek.urule.model.flow.FlowDefinition> flowMap = knowledgePackage.getFlowMap();
            if (flowMap != null && !flowMap.isEmpty()) {
                String flowId = flowMap.keySet().iterator().next();
                session.startProcess(flowId, parameters);
            } else {
                if (parameters.isEmpty()) {
                    session.fireRules();
                } else {
                    session.fireRules(parameters);
                }
            }

            // 收集输出：参数 + 各 entity 的属性
            Map<String, Object> output = new LinkedHashMap<>();
            // 参数输出
            Map<String, Object> paramOutput = session.getParameters();
            if (paramOutput != null) output.putAll(paramOutput);
            // 变量输出（从 entity 中读回）
            for (Map.Entry<String, GeneralEntity> entityEntry : entities.entrySet()) {
                String category = entityEntry.getKey();
                GeneralEntity entity = entityEntry.getValue();
                for (Object key : entity.keySet()) {
                    output.put(category + "." + key, entity.get(key));
                }
            }

            result.setActualOutput(objectMapper.writeValueAsString(output));
            result.setPassed(true);
        } catch (Exception e) {
            log.warn("测试用例执行异常: caseId={}, caseName={}", tc.getId(), tc.getCaseName(), e);
            result.setPassed(false);
            result.setErrorMessage(e.getMessage());
        }
        result.setExecutionTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 将指定 run 的所有结果设置为指定 diff_status（用于 baseline 标记）。
     */
    private void setAllDiffStatus(long runId, String status) {
        testResultDao.setAllDiffStatus(runId, status);
    }

    /**
     * 统计输出分布：解析每条结果的 actual_output JSON，按输出变量值分组计数。
     * 若有 baseline，同时查询 baseline 的分段数据填入对比列。
     */
    List<TestSegment> computeOutputDistribution(long runId, Long baselineRunId) {
        List<TestResult> results = testResultDao.findResultsByRunId(runId);
        if (results.isEmpty()) return Collections.emptyList();

        // 按输出变量值分组计数: varName → (value → count)
        Map<String, Map<String, Integer>> distribution = new LinkedHashMap<>();
        int totalResults = 0;

        for (TestResult r : results) {
            if (r.getActualOutput() == null || r.getActualOutput().isEmpty()) continue;
            totalResults++;
            try {
                Map<String, Object> output = objectMapper.readValue(
                        r.getActualOutput(), new TypeReference<Map<String, Object>>() {});
                for (Map.Entry<String, Object> entry : output.entrySet()) {
                    String varName = entry.getKey();
                    String value = entry.getValue() == null ? "null" : String.valueOf(entry.getValue());
                    distribution.computeIfAbsent(varName, k -> new LinkedHashMap<>())
                            .merge(value, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.debug("解析 actual_output 失败: resultId={}", r.getId(), e);
            }
        }

        // 查询 baseline 分段数据（如果有）
        Map<String, Map<String, TestSegment>> baselineMap = new HashMap<>();
        if (baselineRunId != null) {
            List<TestSegment> baselineSegments = testResultDao.findSegmentsByRunId(baselineRunId);
            for (TestSegment bs : baselineSegments) {
                if ("OUTPUT".equals(bs.getVariableType())) {
                    baselineMap.computeIfAbsent(bs.getVariableName(), k -> new HashMap<>())
                            .put(bs.getSegmentLabel(), bs);
                }
            }
        }

        // 构建 TestSegment 列表
        List<TestSegment> segments = new ArrayList<>();
        final int total = totalResults;

        for (Map.Entry<String, Map<String, Integer>> varEntry : distribution.entrySet()) {
            String varName = varEntry.getKey();
            Map<String, Integer> valueCounts = varEntry.getValue();

            for (Map.Entry<String, Integer> valEntry : valueCounts.entrySet()) {
                String segmentLabel = valEntry.getKey();
                int count = valEntry.getValue();
                BigDecimal pct = total > 0
                        ? BigDecimal.valueOf(count * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                TestSegment seg = new TestSegment();
                seg.setRunId(runId);
                seg.setVariableName(varName);
                seg.setVariableType("OUTPUT");
                seg.setSegmentLabel(segmentLabel);
                seg.setCaseCount(count);
                seg.setPercentage(pct);

                // 填充 baseline 对比数据
                Map<String, TestSegment> baselineVarMap = baselineMap.get(varName);
                if (baselineVarMap != null) {
                    TestSegment bs = baselineVarMap.get(segmentLabel);
                    if (bs != null) {
                        seg.setBaselineCount(bs.getCaseCount());
                        seg.setBaselinePercentage(bs.getPercentage());
                        seg.setChangePct(pct.subtract(bs.getPercentage()));
                    }
                }

                segments.add(seg);
            }
        }
        return segments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInputData(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuleException("解析输入数据JSON失败: " + e.getMessage());
        }
    }

    public void setKnowledgePackageService(KnowledgePackageService knowledgePackageService) {
        this.knowledgePackageService = knowledgePackageService;
    }

    public void setTestCaseGenerator(TestCaseGenerator testCaseGenerator) {
        this.testCaseGenerator = testCaseGenerator;
    }

    public void setTestResultDao(TestResultDao testResultDao) {
        this.testResultDao = testResultDao;
    }
}

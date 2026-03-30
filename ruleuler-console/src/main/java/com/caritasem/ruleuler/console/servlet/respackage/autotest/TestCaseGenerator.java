package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.RuleException;
import com.bstek.urule.console.repository.RepositoryService;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class TestCaseGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestCaseGenerator.class);

    private ConditionParser conditionParser;
    private DagPathWalker dagPathWalker;
    private ValueGenerator valueGenerator;
    private ConstraintSolver constraintSolver;
    private RepositoryService repositoryService;
    private ConflictDetector conflictDetector;
    private SegmentAnalyzer segmentAnalyzer;
    private TestResultDao testResultDao;

    /**
     * V2: 基于 SegmentAnalyzer 生成用例包。
     * 1. ConflictDetector 前置检查（仅记录，不阻断）
     * 2. SegmentAnalyzer 提取分段 + 选策略 + 生成代表性输入
     * 3. 创建 TestCasePack（source_type=auto），批量持久化用例
     */
    public TestCasePack generatePack(String project, String packageId, String flowFilePath) {
        // 1. 冲突检测（记录日志，不阻断）
        if (conflictDetector != null) {
            try {
                List<ConflictItem> conflicts = conflictDetector.detect(flowFilePath);
                if (!conflicts.isEmpty()) {
                    log.warn("generatePack: 冲突检测发现 {} 条冲突, flowFile={}", conflicts.size(), flowFilePath);
                    for (ConflictItem c : conflicts) {
                        log.warn("  冲突[{}] {} - {} @ {}: {}", c.getSeverity(), c.getConflictType(),
                                c.getRuleFile(), c.getLocation(), c.getDescription());
                    }
                }
            } catch (Exception e) {
                log.error("generatePack: 冲突检测异常, 继续生成用例, flowFile={}", flowFilePath, e);
            }
        }

        // 2. 提取分段 + 选策略 + 生成代表性输入
        Map<String, List<Segment>> segments = segmentAnalyzer.extractSegments(flowFilePath);
        long partitionCount = segmentAnalyzer.computePartitionCount(segments);
        Strategy strategy = segmentAnalyzer.chooseStrategy(partitionCount);
        log.info("generatePack: 分段变量数={}, 分区数={}, 策略={}", segments.size(), partitionCount, strategy);

        List<Map<String, Object>> inputs = segmentAnalyzer.generateRepresentativeInputs(segments, strategy);
        log.info("generatePack: 生成代表性输入 {} 条", inputs.size());

        // 3. 创建 TestCasePack
        long now = System.currentTimeMillis();
        TestCasePack pack = new TestCasePack();
        pack.setProject(project);
        pack.setPackageId(packageId);
        pack.setPackName("auto_" + packageId + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date(now)));
        pack.setSourceType("auto");
        pack.setTotalCases(inputs.size());
        pack.setCreatedAt(now);
        long packId = testResultDao.createPack(pack);
        pack.setId(packId);

        // 4. 转换为 TestCase 并批量持久化
        if (!inputs.isEmpty()) {
            List<TestCase> cases = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                Map<String, Object> inputMap = inputs.get(i);
                TestCase tc = new TestCase();
                tc.setPackId(packId);
                tc.setProject(project);
                tc.setPackageId(packageId);
                tc.setFlowFile(flowFilePath);
                tc.setCaseName("SEG_" + (i + 1));
                tc.setInputData(toJson(inputMap));
                tc.setExpectedType("HIT");
                tc.setTestPurpose("分段代表值覆盖");
                tc.setCreatedAt(now);
                tc.setUpdatedAt(now);
                cases.add(tc);
            }
            testResultDao.batchInsertCases(cases);
        }

        return pack;
    }

    /**
     * V1: 为知识包生成测试用例。
     * 1. 解析决策流条件
     * 2. 遍历 DAG 路径
     * 3. 对每条路径生成命中 + MC/DC 不命中用例
     * 4. 持久化到 DB（先删旧数据再插入）
     */
    public List<TestCase> generate(String project, String packageId, String flowFilePath) {
        // 0. L0 静态冲突检测（记录冲突但不阻断用例生成）
        if (conflictDetector != null) {
            try {
                List<ConflictItem> conflicts = conflictDetector.detect(flowFilePath);
                if (!conflicts.isEmpty()) {
                    log.warn("自动测试: 冲突检测发现 {} 条冲突, flowFile={}", conflicts.size(), flowFilePath);
                    for (ConflictItem c : conflicts) {
                        log.warn("  冲突[{}] {} - {} @ {}: {}", c.getSeverity(), c.getConflictType(), c.getRuleFile(), c.getLocation(), c.getDescription());
                    }
                } else {
                    log.info("自动测试: 冲突检测通过, 无冲突, flowFile={}", flowFilePath);
                }
            } catch (Exception e) {
                log.error("自动测试: 冲突检测异常, 继续生成用例, flowFile={}", flowFilePath, e);
            }
        }

        // 1. Parse conditions
        Map<String, List<ConditionConstraint>> nodeConstraints = conditionParser.parse(flowFilePath);
        log.info("自动测试: 解析条件完成, 节点数={}, flowFile={}", nodeConstraints.size(), flowFilePath);
        for (Map.Entry<String, List<ConditionConstraint>> entry : nodeConstraints.entrySet()) {
            log.info("  节点[{}]: {}个约束", entry.getKey(), entry.getValue().size());
        }

        // 2. Read flow XML and walk DAG paths
        Element flowXml = readFlowXml(flowFilePath);
        List<DagPath> paths = dagPathWalker.walkAllPaths(flowXml, nodeConstraints);
        log.info("自动测试: DAG路径遍历完成, 路径数={}", paths.size());

        // 3. Generate test cases for each path
        List<TestCase> allCases = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (DagPath path : paths) {
            List<ConditionConstraint> constraints = path.getConstraints();
            log.info("自动测试: 路径[{}], 约束数={}", path.getDescription(), constraints == null ? 0 : constraints.size());
            if (constraints == null || constraints.isEmpty()) {
                allCases.add(buildTestCase(project, packageId, flowFilePath, path, "HIT", Collections.emptyMap(), null, "验证无条件路径命中", now));
                continue;
            }

            // 为每个变量生成值：同一变量取第一个约束的命中值（不跨规则合并）
            Map<String, Object> hitData = new LinkedHashMap<>();
            for (ConditionConstraint cc : constraints) {
                if (!hitData.containsKey(cc.getVariableName())) {
                    hitData.put(cc.getVariableName(), valueGenerator.generateHitValue(cc));
                }
            }

            // HIT case
            allCases.add(buildTestCase(project, packageId, flowFilePath, path, "HIT", hitData, null, "验证路径完整命中", now));

            // MC/DC MISS cases: flip each constraint independently
            for (ConditionConstraint cc : constraints) {
                Map<String, Object> missData = new LinkedHashMap<>(hitData);
                missData.put(cc.getVariableName(), valueGenerator.generateMissValue(cc));
                String flippedDesc = cc.getVariableName() + " " + cc.getOp() + " " + cc.getValue();
                String purpose = "验证\"" + flippedDesc + "\"独立生效";
                allCases.add(buildTestCase(project, packageId, flowFilePath, path, "MISS", missData, flippedDesc, purpose, now));
            }
        }

        // 4. Return cases (no persistence here, TestExecutor handles it)
        return allCases;
    }

    private Element readFlowXml(String flowFilePath) {
        try {
            InputStream is = repositoryService.readFile(flowFilePath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            return doc.getRootElement();
        } catch (Exception e) {
            throw new RuleException("读取决策流文件失败: " + flowFilePath);
        }
    }

    private Map<String, List<ConditionConstraint>> groupByVariable(List<ConditionConstraint> constraints) {
        Map<String, List<ConditionConstraint>> map = new LinkedHashMap<>();
        for (ConditionConstraint cc : constraints) {
            map.computeIfAbsent(cc.getVariableName(), k -> new ArrayList<>()).add(cc);
        }
        return map;
    }

    private TestCase buildTestCase(String project, String packageId, String flowFile,
                                    DagPath path, String expectedType,
                                    Map<String, Object> inputData, String flippedCondition, String testPurpose, long now) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setPackageId(packageId);
        tc.setFlowFile(flowFile);
        tc.setCaseName(expectedType + "_" + path.getDescription().hashCode()
                + (flippedCondition != null ? "_flip_" + flippedCondition.hashCode() : ""));
        tc.setPathDescription(path.getDescription());
        tc.setInputData(toJson(inputData));
        tc.setExpectedType(expectedType);
        tc.setFlippedCondition(flippedCondition);
        tc.setTestPurpose(testPurpose);
        tc.setCreatedAt(now);
        tc.setUpdatedAt(now);
        return tc;
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    // ---- Setters for Spring injection ----
    public void setConditionParser(ConditionParser conditionParser) { this.conditionParser = conditionParser; }
    public void setDagPathWalker(DagPathWalker dagPathWalker) { this.dagPathWalker = dagPathWalker; }
    public void setValueGenerator(ValueGenerator valueGenerator) { this.valueGenerator = valueGenerator; }
    public void setConstraintSolver(ConstraintSolver constraintSolver) { this.constraintSolver = constraintSolver; }
    public void setRepositoryService(RepositoryService repositoryService) { this.repositoryService = repositoryService; }
    public void setConflictDetector(ConflictDetector conflictDetector) { this.conflictDetector = conflictDetector; }
    public void setSegmentAnalyzer(SegmentAnalyzer segmentAnalyzer) { this.segmentAnalyzer = segmentAnalyzer; }
    public void setTestResultDao(TestResultDao testResultDao) { this.testResultDao = testResultDao; }
}

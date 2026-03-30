package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.RuleException;
import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.rule.Op;
import com.bstek.urule.console.repository.RepositoryService;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 输入分段分析器：从规则定义中提取输入变量的切分点，生成有序区间。
 */
public class SegmentAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SegmentAnalyzer.class);
    private static final String OTHER_SENTINEL = "__OTHER__";

    private ConditionParser conditionParser;
    private RepositoryService repositoryService;

    /**
     * 提取所有输入变量的分段信息。
     * 1. 读取决策流 XML，提取所有规则文件路径
     * 2. 对每个规则文件调用 conditionParser.parseRuleFile() 获取约束
     * 3. 按 variableName 聚合约束
     * 4. 数值类型：收集切分点，生成 N+1 个区间
     * 5. 枚举/字符串类型：收集所有 Equals 值 + __OTHER__
     * 6. 无条件引用的变量排除
     */
    public Map<String, List<Segment>> extractSegments(String flowXmlPath) {
        // 收集所有约束
        List<ConditionConstraint> allConstraints = collectAllConstraints(flowXmlPath);

        // 按变量名聚合
        Map<String, List<ConditionConstraint>> grouped = new LinkedHashMap<>();
        for (ConditionConstraint cc : allConstraints) {
            if (cc.getVariableName() == null || cc.getVariableName().isEmpty()) continue;
            if (cc.getOp() == null) continue;
            grouped.computeIfAbsent(cc.getVariableName(), k -> new ArrayList<>()).add(cc);
        }

        // 生成分段
        Map<String, List<Segment>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<ConditionConstraint>> entry : grouped.entrySet()) {
            String varName = entry.getKey();
            List<ConditionConstraint> constraints = entry.getValue();
            Datatype datatype = constraints.get(0).getDatatype();
            String varCategory = constraints.get(0).getVariableCategory();

            List<Segment> segments;
            if (isNumericType(datatype)) {
                segments = buildNumericSegments(varName, datatype, constraints);
            } else {
                segments = buildEnumSegments(varName, datatype, constraints);
            }

            // 设置 variableCategory
            for (Segment seg : segments) {
                seg.setVariableCategory(varCategory);
            }

            if (!segments.isEmpty()) {
                result.put(varName, segments);
            }
        }
        return result;
    }

    /**
     * 计算理论分区总数（笛卡尔积）。
     */
    public long computePartitionCount(Map<String, List<Segment>> segments) {
        if (segments == null || segments.isEmpty()) return 0;
        long count = 1;
        for (List<Segment> segs : segments.values()) {
            count *= segs.size();
            if (count < 0) return Long.MAX_VALUE; // 溢出保护
        }
        return count;
    }

    /**
     * 根据分区数自动选择策略。
     * ≤100 → FULL, 101-10000 → PAIRWISE, >10000 → PAIRWISE_SAMPLED
     */
    public Strategy chooseStrategy(long partitionCount) {
        if (partitionCount <= 100) return Strategy.FULL;
        if (partitionCount <= 10000) return Strategy.PAIRWISE;
        return Strategy.PAIRWISE_SAMPLED;
    }

    /**
     * 根据策略生成代表性输入集。
     * FULL: 全笛卡尔积
     * PAIRWISE: 贪心 all-pairs，保证任意两变量所有区间组合至少出现一次
     * PAIRWISE_SAMPLED: pairwise + 随机边界采样
     */
    public List<Map<String, Object>> generateRepresentativeInputs(
            Map<String, List<Segment>> segments, Strategy strategy) {
        if (segments == null || segments.isEmpty()) return Collections.emptyList();

        // 构建有序的变量名列表和对应的分段列表
        List<String> varNames = new ArrayList<>(segments.keySet());
        List<List<Segment>> segLists = new ArrayList<>();
        for (String v : varNames) segLists.add(segments.get(v));

        List<int[]> tuples;
        switch (strategy) {
            case FULL:
                tuples = generateFullTuples(segLists);
                break;
            case PAIRWISE:
                tuples = generatePairwiseTuples(segLists);
                break;
            case PAIRWISE_SAMPLED:
                tuples = generatePairwiseTuples(segLists);
                addBoundarySamples(tuples, segLists);
                break;
            default:
                tuples = generateFullTuples(segLists);
        }

        // 将索引元组转为按变量类别分组的结构：{类别名: {变量名: 值}}
        List<Map<String, Object>> result = new ArrayList<>();
        for (int[] tuple : tuples) {
            // 按类别分组
            Map<String, Map<String, Object>> categoryMap = new LinkedHashMap<>();
            for (int i = 0; i < varNames.size(); i++) {
                Segment seg = segLists.get(i).get(tuple[i]);
                String category = seg.getVariableCategory();
                if (category == null || category.isEmpty()) category = "参数";
                categoryMap.computeIfAbsent(category, k -> new LinkedHashMap<>())
                        .put(varNames.get(i), seg.getRepresentative());
            }
            // 转为扁平 Map，key 为 "类别名.变量名"（参数类别直接用变量名）
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> catEntry : categoryMap.entrySet()) {
                String cat = catEntry.getKey();
                for (Map.Entry<String, Object> varEntry : catEntry.getValue().entrySet()) {
                    if ("参数".equals(cat)) {
                        row.put(varEntry.getKey(), varEntry.getValue());
                    } else {
                        row.put(cat + "." + varEntry.getKey(), varEntry.getValue());
                    }
                }
            }
            result.add(row);
        }
        return result;
    }

    /**
     * FULL 策略：全笛卡尔积。
     */
    private List<int[]> generateFullTuples(List<List<Segment>> segLists) {
        List<int[]> result = new ArrayList<>();
        int k = segLists.size();
        int[] indices = new int[k];
        int[] sizes = new int[k];
        for (int i = 0; i < k; i++) sizes[i] = segLists.get(i).size();

        while (true) {
            result.add(indices.clone());
            // 进位
            int pos = k - 1;
            while (pos >= 0) {
                indices[pos]++;
                if (indices[pos] < sizes[pos]) break;
                indices[pos] = 0;
                pos--;
            }
            if (pos < 0) break;
        }
        return result;
    }

    /**
     * PAIRWISE 策略：贪心 all-pairs 覆盖。
     * 保证任意两个变量的所有区间组合至少出现一次。
     */
    private List<int[]> generatePairwiseTuples(List<List<Segment>> segLists) {
        int k = segLists.size();
        int[] sizes = new int[k];
        for (int i = 0; i < k; i++) sizes[i] = segLists.get(i).size();

        if (k <= 1) {
            // 单变量直接枚举
            List<int[]> result = new ArrayList<>();
            for (int i = 0; i < sizes[0]; i++) result.add(new int[]{i});
            return result;
        }

        // 收集所有需要覆盖的 pair: (varI, valI, varJ, valJ)
        Set<Long> uncovered = new LinkedHashSet<>();
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                for (int vi = 0; vi < sizes[i]; vi++) {
                    for (int vj = 0; vj < sizes[j]; vj++) {
                        uncovered.add(encodePair(i, vi, j, vj));
                    }
                }
            }
        }

        List<int[]> result = new ArrayList<>();
        Random rng = new Random(42); // 固定种子保证可重复

        while (!uncovered.isEmpty()) {
            // 贪心：选覆盖最多 uncovered pair 的元组
            int[] best = null;
            int bestScore = -1;

            // 尝试多个候选
            int candidates = Math.min(50, k * 10);
            for (int c = 0; c < candidates; c++) {
                int[] candidate = new int[k];
                for (int i = 0; i < k; i++) candidate[i] = rng.nextInt(sizes[i]);

                int score = countCovered(candidate, uncovered, k);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            // 逐位优化：对每个变量尝试所有值，选覆盖最多的
            for (int i = 0; i < k; i++) {
                int bestVal = best[i];
                int bestValScore = -1;
                for (int v = 0; v < sizes[i]; v++) {
                    best[i] = v;
                    int score = countCovered(best, uncovered, k);
                    if (score > bestValScore) {
                        bestValScore = score;
                        bestVal = v;
                    }
                }
                best[i] = bestVal;
            }

            // 移除已覆盖的 pair
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    uncovered.remove(encodePair(i, best[i], j, best[j]));
                }
            }
            result.add(best);
        }
        return result;
    }

    /**
     * PAIRWISE_SAMPLED: 在 pairwise 基础上追加随机边界采样。
     * 额外添加 10% 的随机组合（至少 5 条）。
     */
    private void addBoundarySamples(List<int[]> tuples, List<List<Segment>> segLists) {
        int k = segLists.size();
        int[] sizes = new int[k];
        for (int i = 0; i < k; i++) sizes[i] = segLists.get(i).size();

        int extra = Math.max(5, tuples.size() / 10);
        Random rng = new Random(123);
        Set<String> existing = new HashSet<>();
        for (int[] t : tuples) existing.add(Arrays.toString(t));

        for (int attempt = 0; attempt < extra * 10 && extra > 0; attempt++) {
            int[] candidate = new int[k];
            for (int i = 0; i < k; i++) {
                // 偏向边界值（第一个和最后一个分段）
                if (rng.nextDouble() < 0.5) {
                    candidate[i] = rng.nextBoolean() ? 0 : sizes[i] - 1;
                } else {
                    candidate[i] = rng.nextInt(sizes[i]);
                }
            }
            String key = Arrays.toString(candidate);
            if (!existing.contains(key)) {
                existing.add(key);
                tuples.add(candidate);
                extra--;
            }
        }
    }

    /** 编码一个 pair 为 long，用于快速查找。 */
    private static long encodePair(int varI, int valI, int varJ, int valJ) {
        return ((long) varI << 48) | ((long) valI << 32) | ((long) varJ << 16) | valJ;
    }

    /** 计算一个元组覆盖了多少 uncovered pair。 */
    private static int countCovered(int[] tuple, Set<Long> uncovered, int k) {
        int count = 0;
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                if (uncovered.contains(encodePair(i, tuple[i], j, tuple[j]))) {
                    count++;
                }
            }
        }
        return count;
    }

    // ========== 内部方法 ==========

    /**
     * 从决策流 XML 中收集所有规则文件的约束。
     */
    private List<ConditionConstraint> collectAllConstraints(String flowXmlPath) {
        List<ConditionConstraint> all = new ArrayList<>();
        try {
            InputStream is = repositoryService.readFile(flowXmlPath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element flowRoot = doc.getRootElement();
            String flowDir = flowXmlPath.substring(0, flowXmlPath.lastIndexOf("/") + 1);

            for (Object obj : flowRoot.elements()) {
                if (!(obj instanceof Element)) continue;
                Element ele = (Element) obj;
                if (!"rule".equals(ele.getName())) continue;
                String file = ele.attributeValue("file");
                if (file == null || file.isEmpty()) continue;

                String filePath = stripStoragePrefix(file);
                if (!filePath.startsWith("/")) {
                    filePath = flowDir + filePath;
                }
                try {
                    List<ConditionConstraint> constraints = conditionParser.parseRuleFile(filePath);
                    all.addAll(constraints);
                } catch (Exception e) {
                    log.warn("SegmentAnalyzer: 解析规则文件失败 {}", filePath, e);
                }
            }
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("SegmentAnalyzer: 读取决策流失败: " + flowXmlPath, e));
        }
        return all;
    }

    /**
     * 数值类型：收集所有切分点，排序去重，生成 N+1 个区间。
     * 切分点来自比较操作符的 value。
     */
    List<Segment> buildNumericSegments(String varName, Datatype datatype,
                                        List<ConditionConstraint> constraints) {
        TreeSet<BigDecimal> cutPoints = new TreeSet<>();
        for (ConditionConstraint cc : constraints) {
            if (!isComparisonOp(cc.getOp())) continue;
            if (cc.getValue() == null || cc.getValue().isEmpty()) continue;
            try {
                cutPoints.add(new BigDecimal(cc.getValue().trim()));
            } catch (NumberFormatException e) {
                log.debug("SegmentAnalyzer: 忽略非数值 value={}", cc.getValue());
            }
        }

        if (cutPoints.isEmpty()) return Collections.emptyList();

        List<BigDecimal> sorted = new ArrayList<>(cutPoints);
        List<Segment> segments = new ArrayList<>();

        // 区间 0: (-∞, first)
        segments.add(makeNumericSegment(varName, datatype,
                null, null, Op.LessThen, sorted.get(0).toPlainString(),
                computeRepresentative(null, sorted.get(0), datatype)));

        // 区间 1..N-1: [sorted[i-1], sorted[i])
        for (int i = 0; i < sorted.size() - 1; i++) {
            BigDecimal lo = sorted.get(i);
            BigDecimal hi = sorted.get(i + 1);
            segments.add(makeNumericSegment(varName, datatype,
                    Op.GreaterThenEquals, lo.toPlainString(),
                    Op.LessThen, hi.toPlainString(),
                    computeRepresentative(lo, hi, datatype)));
        }

        // 区间 N: [last, +∞)
        BigDecimal last = sorted.get(sorted.size() - 1);
        segments.add(makeNumericSegment(varName, datatype,
                Op.GreaterThenEquals, last.toPlainString(), null, null,
                computeRepresentative(last, null, datatype)));

        return segments;
    }

    /**
     * 枚举/字符串类型：收集所有 Equals 值 + __OTHER__。
     */
    List<Segment> buildEnumSegments(String varName, Datatype datatype,
                                     List<ConditionConstraint> constraints) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ConditionConstraint cc : constraints) {
            if (cc.getOp() == Op.Equals || cc.getOp() == Op.EqualsIgnoreCase) {
                if (cc.getValue() != null && !cc.getValue().isEmpty()) {
                    values.add(cc.getValue());
                }
            }
        }

        if (values.isEmpty()) return Collections.emptyList();

        List<Segment> segments = new ArrayList<>();
        for (String val : values) {
            Segment seg = new Segment();
            seg.setVariableName(varName);
            seg.setDatatype(datatype);
            seg.setLowerOp(Op.Equals);
            seg.setLowerValue(val);
            seg.setRepresentative(val);
            seg.setLabel(val);
            segments.add(seg);
        }

        // __OTHER__
        Segment other = new Segment();
        other.setVariableName(varName);
        other.setDatatype(datatype);
        other.setLowerOp(Op.NotEquals);
        other.setLowerValue(OTHER_SENTINEL);
        other.setRepresentative(generateOtherValue(values));
        other.setLabel(OTHER_SENTINEL);
        segments.add(other);

        return segments;
    }

    private Segment makeNumericSegment(String varName, Datatype datatype,
                                        Op lowerOp, String lowerValue,
                                        Op upperOp, String upperValue,
                                        Object representative) {
        Segment seg = new Segment();
        seg.setVariableName(varName);
        seg.setDatatype(datatype);
        seg.setLowerOp(lowerOp);
        seg.setLowerValue(lowerValue);
        seg.setUpperOp(upperOp);
        seg.setUpperValue(upperValue);
        seg.setRepresentative(representative);
        seg.setLabel(formatLabel(lowerOp, lowerValue, upperOp, upperValue));
        return seg;
    }

    /**
     * 计算区间代表值（中点）。
     */
    static Object computeRepresentative(BigDecimal lower, BigDecimal upper, Datatype datatype) {
        if (lower != null && upper != null) {
            // 有限区间取中点
            BigDecimal mid = lower.add(upper).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            return convertToDatatype(mid, datatype);
        } else if (lower != null) {
            // [lower, +∞) → lower + 1
            return convertToDatatype(lower.add(BigDecimal.ONE), datatype);
        } else if (upper != null) {
            // (-∞, upper) → upper - 1
            return convertToDatatype(upper.subtract(BigDecimal.ONE), datatype);
        }
        return 0;
    }

    private static Object convertToDatatype(BigDecimal val, Datatype datatype) {
        if (datatype == Datatype.Integer) return val.intValue();
        if (datatype == Datatype.Long) return val.longValue();
        if (datatype == Datatype.Float) return val.floatValue();
        if (datatype == Datatype.Double) return val.doubleValue();
        if (datatype == Datatype.BigDecimal) return val;
        return val.doubleValue();
    }

    /**
     * 生成一个不在已有枚举值中的 __OTHER__ 代表值。
     */
    static String generateOtherValue(Set<String> existingValues) {
        String candidate = "__OTHER_VALUE__";
        int i = 0;
        while (existingValues.contains(candidate)) {
            candidate = "__OTHER_VALUE_" + (i++) + "__";
        }
        return candidate;
    }

    private String formatLabel(Op lowerOp, String lowerValue, Op upperOp, String upperValue) {
        if (lowerOp == null && upperOp != null) {
            return "(-∞, " + upperValue + ")";
        }
        if (lowerOp != null && upperOp == null) {
            return "[" + lowerValue + ", +∞)";
        }
        if (lowerOp != null && upperOp != null) {
            String lb = (lowerOp == Op.GreaterThen) ? "(" : "[";
            String rb = (upperOp == Op.LessThenEquals) ? "]" : ")";
            return lb + lowerValue + ", " + upperValue + rb;
        }
        return "(-∞, +∞)";
    }

    private boolean isComparisonOp(Op op) {
        return op == Op.Equals || op == Op.GreaterThen || op == Op.GreaterThenEquals
                || op == Op.LessThen || op == Op.LessThenEquals;
    }

    static boolean isNumericType(Datatype dt) {
        return dt == Datatype.Integer || dt == Datatype.Double || dt == Datatype.Long
                || dt == Datatype.Float || dt == Datatype.BigDecimal;
    }

    private static String stripStoragePrefix(String path) {
        int colonIdx = path.indexOf(":");
        if (colonIdx > 0 && colonIdx < 5) {
            return path.substring(colonIdx + 1);
        }
        return path;
    }

    // ========== 输出画像提取 ==========

    /**
     * 提取所有输出变量的可能取值画像。
     * 遍历决策流引用的所有规则文件，按根元素类型分派提取 var-assign 赋值。
     * 跟踪每个变量被多少个流程节点赋值，用于判断 isOverridable。
     */
    public Map<String, OutputProfile> extractOutputProfiles(String flowXmlPath) {
        // varName → OutputProfile（聚合所有规则文件的结果）
        Map<String, OutputProfile> profiles = new LinkedHashMap<>();
        // varName → 赋值该变量的流程节点集合（用于 overridable 判断）
        Map<String, Set<String>> varToFlowNodes = new LinkedHashMap<>();

        try {
            InputStream is = repositoryService.readFile(flowXmlPath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element flowRoot = doc.getRootElement();
            String flowDir = flowXmlPath.substring(0, flowXmlPath.lastIndexOf("/") + 1);

            for (Object obj : flowRoot.elements()) {
                if (!(obj instanceof Element)) continue;
                Element ele = (Element) obj;
                if (!"rule".equals(ele.getName())) continue;
                String file = ele.attributeValue("file");
                if (file == null || file.isEmpty()) continue;

                String filePath = stripStoragePrefix(file);
                if (!filePath.startsWith("/")) {
                    filePath = flowDir + filePath;
                }
                // 流程节点名（用于 overridable 判断）
                String nodeName = ele.attributeValue("name");
                if (nodeName == null) nodeName = filePath;

                try {
                    InputStream ruleIs = repositoryService.readFile(filePath);
                    SAXReader ruleReader = new SAXReader();
                    Document ruleDoc = ruleReader.read(ruleIs);
                    Element root = ruleDoc.getRootElement();
                    String rootName = root.getName();

                    List<VarAssignEntry> entries;
                    if ("decision-table".equals(rootName)) {
                        entries = extractFromDecisionTable(root, filePath);
                    } else if ("decision-tree".equals(rootName)) {
                        entries = extractFromDecisionTree(root, filePath);
                    } else if ("scorecard".equals(rootName)) {
                        entries = extractFromScorecard(root, filePath);
                    } else {
                        entries = extractFromRuleSet(root, filePath);
                    }

                    for (VarAssignEntry entry : entries) {
                        OutputProfile profile = profiles.computeIfAbsent(entry.varName, k -> {
                            OutputProfile p = new OutputProfile();
                            p.setVariableName(entry.varName);
                            p.setVariableCategory(entry.varCategory);
                            p.setDatatype(entry.datatype);
                            p.setPossibleValues(new LinkedHashSet<>());
                            p.setSourceRules(new ArrayList<>());
                            return p;
                        });
                        profile.getPossibleValues().addAll(entry.values);
                        if (!profile.getSourceRules().contains(filePath)) {
                            profile.getSourceRules().add(filePath);
                        }
                        varToFlowNodes.computeIfAbsent(entry.varName, k -> new LinkedHashSet<>()).add(nodeName);
                    }
                } catch (Exception e) {
                    log.warn("extractOutputProfiles: 解析规则文件失败 {}", filePath, e);
                }
            }
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("extractOutputProfiles: 读取决策流失败: " + flowXmlPath, e));
        }

        // 设置 overridable：被多个流程节点赋值的变量
        for (Map.Entry<String, Set<String>> entry : varToFlowNodes.entrySet()) {
            OutputProfile profile = profiles.get(entry.getKey());
            if (profile != null) {
                profile.setOverridable(entry.getValue().size() > 1);
            }
        }
        return profiles;
    }

    /** 内部临时结构：一个规则文件中提取的赋值条目 */
    static class VarAssignEntry {
        String varName;
        String varCategory;
        Datatype datatype;
        Set<String> values = new LinkedHashSet<>();
    }

    /**
     * 规则集：递归收集所有 var-assign 元素，提取 var + value content。
     */
    List<VarAssignEntry> extractFromRuleSet(Element root, String sourceFile) {
        Map<String, VarAssignEntry> map = new LinkedHashMap<>();
        collectVarAssignValues(root, map);
        return new ArrayList<>(map.values());
    }

    private void collectVarAssignValues(Element element, Map<String, VarAssignEntry> map) {
        if ("var-assign".equals(element.getName())) {
            String varName = element.attributeValue("var");
            if (varName == null || varName.isEmpty()) return;
            VarAssignEntry entry = map.computeIfAbsent(varName, k -> {
                VarAssignEntry e = new VarAssignEntry();
                e.varName = varName;
                e.varCategory = element.attributeValue("var-category");
                e.datatype = parseDatatype(element.attributeValue("datatype"));
                return e;
            });
            String content = extractValueContent(element);
            if (content != null) entry.values.add(content);
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectVarAssignValues((Element) obj, map);
            }
        }
    }

    /**
     * 决策表：从 Assignment 列提取变量定义，从对应 cell 的 value content 提取可能值。
     */
    List<VarAssignEntry> extractFromDecisionTable(Element root, String sourceFile) {
        // 1. 收集 Assignment 列定义
        Map<Integer, Element> assignCols = new LinkedHashMap<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"col".equals(ele.getName())) continue;
            if (!"Assignment".equals(ele.attributeValue("type"))) continue;
            int colNum = Integer.parseInt(ele.attributeValue("num"));
            assignCols.put(colNum, ele);
        }
        if (assignCols.isEmpty()) return Collections.emptyList();

        // 2. 收集 cell
        Map<String, VarAssignEntry> map = new LinkedHashMap<>();
        for (Map.Entry<Integer, Element> colEntry : assignCols.entrySet()) {
            int colNum = colEntry.getKey();
            Element colDef = colEntry.getValue();
            String varName = colDef.attributeValue("var");
            if (varName == null) continue;

            VarAssignEntry entry = map.computeIfAbsent(varName, k -> {
                VarAssignEntry e = new VarAssignEntry();
                e.varName = varName;
                e.varCategory = colDef.attributeValue("var-category");
                e.datatype = parseDatatype(colDef.attributeValue("datatype"));
                return e;
            });

            // 遍历所有 cell，找到属于该列的
            for (Object obj : root.elements()) {
                if (!(obj instanceof Element)) continue;
                Element ele = (Element) obj;
                if (!"cell".equals(ele.getName())) continue;
                int cellCol = Integer.parseInt(ele.attributeValue("col"));
                if (cellCol != colNum) continue;
                String content = extractValueContent(ele);
                if (content != null) entry.values.add(content);
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 决策树：递归遍历，在 action-tree-node 下收集 var-assign。
     */
    List<VarAssignEntry> extractFromDecisionTree(Element root, String sourceFile) {
        Map<String, VarAssignEntry> map = new LinkedHashMap<>();
        collectTreeActions(root, map);
        return new ArrayList<>(map.values());
    }

    private void collectTreeActions(Element element, Map<String, VarAssignEntry> map) {
        if ("action-tree-node".equals(element.getName())) {
            // 叶节点：收集其下所有 var-assign
            collectVarAssignValues(element, map);
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectTreeActions((Element) obj, map);
            }
        }
    }

    /**
     * 评分卡：
     * - scoring-type="sum" 时，枚举所有行得分的子集和
     * - 其他模式直接收集各行得分值
     * 赋值目标变量从 scorecard 根元素的 var/var-category/datatype 属性获取。
     */
    List<VarAssignEntry> extractFromScorecard(Element root, String sourceFile) {
        String scoringType = root.attributeValue("scoring-type");
        String varName = root.attributeValue("var");
        if (varName == null || varName.isEmpty()) return Collections.emptyList();

        VarAssignEntry entry = new VarAssignEntry();
        entry.varName = varName;
        entry.varCategory = root.attributeValue("var-category");
        entry.datatype = parseDatatype(root.attributeValue("datatype"));

        // 收集所有 score cell 的值
        List<BigDecimal> scores = new ArrayList<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"card-cell".equals(ele.getName())) continue;
            if (!"score".equals(ele.attributeValue("type"))) continue;
            String content = extractValueContent(ele);
            if (content != null) {
                try {
                    scores.add(new BigDecimal(content.trim()));
                } catch (NumberFormatException e) {
                    log.debug("extractFromScorecard: 忽略非数值 score={}", content);
                }
            }
        }

        if ("sum".equals(scoringType)) {
            entry.values = enumerateSubsetSums(scores);
        } else {
            for (BigDecimal s : scores) {
                entry.values.add(s.toPlainString());
            }
        }
        return Collections.singletonList(entry);
    }

    /**
     * 枚举 N 个得分的所有子集和（2^N 种组合）。
     * 限制 N ≤ 20 防止爆炸。
     */
    static Set<String> enumerateSubsetSums(List<BigDecimal> scores) {
        Set<BigDecimal> sums = new LinkedHashSet<>();
        sums.add(BigDecimal.ZERO); // 空子集 → 0
        for (BigDecimal score : scores) {
            Set<BigDecimal> newSums = new LinkedHashSet<>(sums);
            for (BigDecimal existing : sums) {
                newSums.add(existing.add(score));
            }
            sums = newSums;
        }
        Set<String> result = new LinkedHashSet<>();
        for (BigDecimal s : sums) {
            result.add(s.toPlainString());
        }
        return result;
    }

    /** 从元素中提取第一个 <value content="X"/> 的 content 属性 */
    private static String extractValueContent(Element element) {
        for (Object obj : element.elements()) {
            if (!(obj instanceof Element)) continue;
            Element child = (Element) obj;
            if ("value".equals(child.getName())) {
                return child.attributeValue("content");
            }
        }
        return null;
    }

    private static Datatype parseDatatype(String str) {
        if (str == null || str.isEmpty()) return Datatype.String;
        try { return Datatype.valueOf(str); } catch (IllegalArgumentException e) { return Datatype.String; }
    }

    // ========== Spring setter injection ==========

    public void setConditionParser(ConditionParser conditionParser) {
        this.conditionParser = conditionParser;
    }

    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }
}

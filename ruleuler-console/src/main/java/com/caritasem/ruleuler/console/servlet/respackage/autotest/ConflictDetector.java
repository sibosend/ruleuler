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
import java.util.*;

/**
 * L0 静态冲突检测器。
 * 在用例生成前对决策流引用的所有规则文件执行静态分析，检测：
 * - 决策表行重叠
 * - 规则集条件重叠（无 salience）
 * - 决策树分支不完备
 * - 跨组件变量覆盖
 */
public class ConflictDetector {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetector.class);

    private RepositoryService repositoryService;
    private ConditionParser conditionParser;

    /**
     * 对决策流引用的所有规则文件执行静态冲突检测。
     */
    public List<ConflictItem> detect(String flowXmlPath) {
        List<ConflictItem> conflicts = new ArrayList<>();
        try {
            InputStream is = repositoryService.readFile(flowXmlPath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element flowRoot = doc.getRootElement();
            String flowDir = flowXmlPath.substring(0, flowXmlPath.lastIndexOf("/") + 1);

            // 遍历决策流中的 rule 节点，读取并检测每个规则文件
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
                    InputStream ruleIs = repositoryService.readFile(filePath);
                    SAXReader ruleReader = new SAXReader();
                    Document ruleDoc = ruleReader.read(ruleIs);
                    Element ruleRoot = ruleDoc.getRootElement();
                    String rootName = ruleRoot.getName();

                    if ("decision-table".equals(rootName)) {
                        conflicts.addAll(detectDecisionTableOverlap(filePath, ruleRoot));
                    } else if ("rule-set".equals(rootName)) {
                        conflicts.addAll(detectRuleSetOverlap(filePath, ruleRoot));
                    } else if ("decision-tree".equals(rootName)) {
                        conflicts.addAll(detectDecisionTreeCompleteness(filePath, ruleRoot));
                    }
                } catch (Exception e) {
                    log.warn("冲突检测: 读取规则文件失败 {}", filePath, e);
                }
            }

            // 跨组件变量覆盖检测
            conflicts.addAll(detectCrossComponentOverride(flowXmlPath, flowRoot));

        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("冲突检测失败: " + flowXmlPath, e));
        }
        return conflicts;
    }

    /**
     * 决策表行重叠检测：同一决策表中两行 Criteria 条件是否存在交集。
     * 对每对行，检查所有 Criteria 列的条件是否在每个维度上都有交集。
     */
    List<ConflictItem> detectDecisionTableOverlap(String filePath, Element root) {
        List<ConflictItem> conflicts = new ArrayList<>();

        // 收集 Criteria 列定义
        Map<Integer, Element> criteriaColumns = new LinkedHashMap<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("col".equals(ele.getName()) && "Criteria".equals(ele.attributeValue("type"))) {
                criteriaColumns.put(Integer.parseInt(ele.attributeValue("num")), ele);
            }
        }
        if (criteriaColumns.isEmpty()) return conflicts;

        // 收集行数
        int maxRow = -1;
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("row".equals(ele.getName())) {
                int rowNum = Integer.parseInt(ele.attributeValue("num"));
                if (rowNum > maxRow) maxRow = rowNum;
            }
        }

        // 收集 cell: (row,col) → cell
        Map<String, Element> cellMap = new HashMap<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"cell".equals(ele.getName())) continue;
            int col = Integer.parseInt(ele.attributeValue("col"));
            if (!criteriaColumns.containsKey(col)) continue;
            int row = Integer.parseInt(ele.attributeValue("row"));
            cellMap.put(row + "," + col, ele);
        }

        // 按行提取每行的条件：row → (variableName → List<ConditionConstraint>)
        List<Map<String, List<ConditionConstraint>>> rowConditions = new ArrayList<>();
        for (int row = 0; row <= maxRow; row++) {
            Map<String, List<ConditionConstraint>> varMap = new LinkedHashMap<>();
            for (Map.Entry<Integer, Element> entry : criteriaColumns.entrySet()) {
                int colNum = entry.getKey();
                Element colDef = entry.getValue();
                Element cell = cellMap.get(row + "," + colNum);
                if (cell == null) continue;
                String varName = colDef.attributeValue("var");
                if (varName == null) continue;
                String datatypeStr = colDef.attributeValue("datatype");
                Datatype datatype = parseDatatype(datatypeStr);

                List<ConditionConstraint> cellConstraints = extractCellConstraints(cell, varName, datatype);
                if (!cellConstraints.isEmpty()) {
                    varMap.computeIfAbsent(varName, k -> new ArrayList<>()).addAll(cellConstraints);
                }
            }
            rowConditions.add(varMap);
        }

        // 两两比较行
        for (int i = 0; i < rowConditions.size(); i++) {
            for (int j = i + 1; j < rowConditions.size(); j++) {
                if (rowsOverlap(rowConditions.get(i), rowConditions.get(j))) {
                    ConflictItem item = new ConflictItem();
                    item.setConflictType("OVERLAP");
                    item.setSeverity("WARNING");
                    item.setRuleFile(filePath);
                    item.setLocation("第" + i + "行 vs 第" + j + "行");
                    item.setDescription("决策表行重叠: 第" + i + "行与第" + j + "行的条件存在交集");
                    conflicts.add(item);
                }
            }
        }
        return conflicts;
    }

    /**
     * 规则集条件重叠检测：两条规则 <if> 条件交集 + salience 检查。
     * 如果两条规则条件有交集且都没有显式 salience，报告冲突。
     */
    List<ConflictItem> detectRuleSetOverlap(String filePath, Element root) {
        List<ConflictItem> conflicts = new ArrayList<>();

        // 收集每条 rule 的条件和 salience
        List<RuleInfo> rules = new ArrayList<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ruleEle = (Element) obj;
            if (!"rule".equals(ruleEle.getName())) continue;

            RuleInfo info = new RuleInfo();
            info.name = ruleEle.attributeValue("name");
            if (info.name == null) info.name = "unnamed";
            String salienceStr = ruleEle.attributeValue("salience");
            info.hasSalience = (salienceStr != null && !salienceStr.isEmpty());

            // 提取 <if> 下的 atom 条件
            info.conditions = new LinkedHashMap<>();
            for (Object child : ruleEle.elements()) {
                if (!(child instanceof Element)) continue;
                Element childEle = (Element) child;
                if ("if".equals(childEle.getName())) {
                    collectAtomConditions(childEle, info.conditions);
                }
            }
            if (!info.conditions.isEmpty()) {
                rules.add(info);
            }
        }

        // 两两比较
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                RuleInfo a = rules.get(i);
                RuleInfo b = rules.get(j);
                if (a.hasSalience && b.hasSalience) continue; // 都有 salience，跳过
                if (conditionsOverlap(a.conditions, b.conditions)) {
                    ConflictItem item = new ConflictItem();
                    item.setConflictType("OVERLAP");
                    item.setSeverity((!a.hasSalience && !b.hasSalience) ? "WARNING" : "WARNING");
                    item.setRuleFile(filePath);
                    item.setLocation(a.name + " vs " + b.name);
                    item.setDescription("规则集条件重叠: 规则\"" + a.name + "\"与\"" + b.name + "\"条件存在交集且未定义salience优先级");
                    conflicts.add(item);
                }
            }
        }
        return conflicts;
    }

    /**
     * 决策树分支完备性检测：variable-tree-node 下 condition-tree-node 是否覆盖全值域。
     * 仅对数值类型做完备性检查（枚举类型无法静态确定全集）。
     */
    List<ConflictItem> detectDecisionTreeCompleteness(String filePath, Element root) {
        List<ConflictItem> conflicts = new ArrayList<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("variable-tree-node".equals(ele.getName())) {
                checkVariableTreeCompleteness(ele, filePath, conflicts);
            }
        }
        return conflicts;
    }

    private void checkVariableTreeCompleteness(Element varNode, String filePath, List<ConflictItem> conflicts) {
        // 从 <left> 提取变量信息
        String varName = null;
        Datatype datatype = Datatype.String;
        for (Object obj : varNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("left".equals(ele.getName())) {
                varName = ele.attributeValue("var");
                String dt = ele.attributeValue("datatype");
                if (dt != null && !dt.isEmpty()) {
                    try { datatype = Datatype.valueOf(dt); } catch (IllegalArgumentException ignored) {}
                }
                break;
            }
        }

        // 收集子 condition-tree-node 的 op+value
        List<ConditionConstraint> branches = new ArrayList<>();
        for (Object obj : varNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("condition-tree-node".equals(ele.getName())) {
                ConditionConstraint cc = new ConditionConstraint();
                cc.setVariableName(varName);
                cc.setDatatype(datatype);
                String opStr = ele.attributeValue("op");
                if (opStr != null) {
                    try { cc.setOp(Op.valueOf(opStr)); } catch (IllegalArgumentException e) { cc.setOp(Op.parse(opStr)); }
                }
                for (Object child : ele.elements()) {
                    if (!(child instanceof Element)) continue;
                    Element childEle = (Element) child;
                    if ("value".equals(childEle.getName())) {
                        cc.setValue(childEle.attributeValue("content"));
                        break;
                    }
                }
                branches.add(cc);

                // 递归检查嵌套的 variable-tree-node
                for (Object child : ele.elements()) {
                    if (!(child instanceof Element)) continue;
                    Element childEle = (Element) child;
                    if ("variable-tree-node".equals(childEle.getName())) {
                        checkVariableTreeCompleteness(childEle, filePath, conflicts);
                    }
                }
            }
        }

        // 检查数值类型分支完备性
        if (isNumericType(datatype) && !branches.isEmpty()) {
            if (!isNumericBranchesComplete(branches)) {
                ConflictItem item = new ConflictItem();
                item.setConflictType("INCOMPLETE");
                item.setSeverity("WARNING");
                item.setRuleFile(filePath);
                item.setLocation("变量: " + (varName != null ? varName : "unknown"));
                item.setDescription("决策树分支不完备: 变量\"" + varName + "\"的条件分支未覆盖全值域");
                conflicts.add(item);
            }
        }
    }

    /**
     * 跨组件变量覆盖检测：决策流中多个节点对同一变量赋值。
     * 从每个 rule 节点引用的规则文件中提取赋值变量，检查是否有多个节点赋值同一变量。
     */
    List<ConflictItem> detectCrossComponentOverride(String flowXmlPath, Element flowRoot) {
        List<ConflictItem> conflicts = new ArrayList<>();
        String flowDir = flowXmlPath.substring(0, flowXmlPath.lastIndexOf("/") + 1);

        // nodeName → 赋值变量集合
        Map<String, Set<String>> nodeAssignments = new LinkedHashMap<>();

        for (Object obj : flowRoot.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"rule".equals(ele.getName())) continue;
            String nodeName = ele.attributeValue("name");
            String file = ele.attributeValue("file");
            if (file == null || file.isEmpty()) continue;

            String filePath = stripStoragePrefix(file);
            if (!filePath.startsWith("/")) {
                filePath = flowDir + filePath;
            }

            try {
                InputStream ruleIs = repositoryService.readFile(filePath);
                SAXReader ruleReader = new SAXReader();
                Document ruleDoc = ruleReader.read(ruleIs);
                Element ruleRoot = ruleDoc.getRootElement();
                Set<String> assignedVars = extractAssignedVariables(ruleRoot);
                if (!assignedVars.isEmpty()) {
                    nodeAssignments.put(nodeName != null ? nodeName : filePath, assignedVars);
                }
            } catch (Exception e) {
                log.warn("冲突检测: 提取赋值变量失败 {}", filePath, e);
            }
        }

        // 检查同一变量被多个节点赋值
        Map<String, List<String>> varToNodes = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : nodeAssignments.entrySet()) {
            for (String var : entry.getValue()) {
                varToNodes.computeIfAbsent(var, k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        for (Map.Entry<String, List<String>> entry : varToNodes.entrySet()) {
            if (entry.getValue().size() > 1) {
                ConflictItem item = new ConflictItem();
                item.setConflictType("OVERRIDE");
                item.setSeverity("WARNING");
                item.setRuleFile(flowXmlPath);
                item.setLocation("变量: " + entry.getKey());
                item.setDescription("跨组件变量覆盖: 变量\"" + entry.getKey() + "\"被多个节点赋值: " + String.join(", ", entry.getValue()));
                conflicts.add(item);
            }
        }
        return conflicts;
    }

    // ========== 辅助方法 ==========

    /**
     * 从规则文件根元素中提取所有 var-assign 的变量名。
     */
    private Set<String> extractAssignedVariables(Element root) {
        Set<String> vars = new LinkedHashSet<>();
        collectVarAssigns(root, vars);
        return vars;
    }

    private void collectVarAssigns(Element element, Set<String> vars) {
        if ("var-assign".equals(element.getName())) {
            String var = element.attributeValue("var");
            if (var != null && !var.isEmpty()) {
                vars.add(var);
            }
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectVarAssigns((Element) obj, vars);
            }
        }
    }

    /**
     * 从 cell 中提取条件约束（op + value）。
     */
    private List<ConditionConstraint> extractCellConstraints(Element cell, String varName, Datatype datatype) {
        List<ConditionConstraint> result = new ArrayList<>();
        List<Element> conditions = new ArrayList<>();
        collectConditionElements(cell, conditions);
        for (Element cond : conditions) {
            String opStr = cond.attributeValue("op");
            if (opStr == null) continue;
            Op op;
            try { op = Op.valueOf(opStr); } catch (IllegalArgumentException e) { op = Op.parse(opStr); }
            String value = null;
            for (Object obj : cond.elements()) {
                if (!(obj instanceof Element)) continue;
                Element child = (Element) obj;
                if ("value".equals(child.getName())) {
                    value = child.attributeValue("content");
                    break;
                }
            }
            ConditionConstraint cc = new ConditionConstraint();
            cc.setVariableName(varName);
            cc.setOp(op);
            cc.setValue(value);
            cc.setDatatype(datatype);
            result.add(cc);
        }
        return result;
    }

    private void collectConditionElements(Element element, List<Element> conditions) {
        if ("condition".equals(element.getName())) {
            conditions.add(element);
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectConditionElements((Element) obj, conditions);
            }
        }
    }

    /**
     * 从 <if> 元素中递归收集 atom 条件，按变量名分组。
     */
    private void collectAtomConditions(Element element, Map<String, List<ConditionConstraint>> varMap) {
        if ("atom".equals(element.getName())) {
            String opStr = element.attributeValue("op");
            if (opStr == null) return;
            Op op;
            try { op = Op.valueOf(opStr); } catch (IllegalArgumentException e) { op = Op.parse(opStr); }

            ConditionConstraint cc = new ConditionConstraint();
            cc.setOp(op);
            for (Object obj : element.elements()) {
                if (!(obj instanceof Element)) continue;
                Element child = (Element) obj;
                if ("left".equals(child.getName())) {
                    cc.setVariableName(child.attributeValue("var"));
                    String dt = child.attributeValue("datatype");
                    cc.setDatatype(parseDatatype(dt));
                } else if ("value".equals(child.getName())) {
                    cc.setValue(child.attributeValue("content"));
                }
            }
            if (cc.getVariableName() != null) {
                varMap.computeIfAbsent(cc.getVariableName(), k -> new ArrayList<>()).add(cc);
            }
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectAtomConditions((Element) obj, varMap);
            }
        }
    }

    /**
     * 判断两行条件是否重叠：所有共同变量的条件都有交集。
     * 如果一行有某个变量而另一行没有，视为该维度无约束（即有交集）。
     */
    private boolean rowsOverlap(Map<String, List<ConditionConstraint>> row1,
                                 Map<String, List<ConditionConstraint>> row2) {
        if (row1.isEmpty() || row2.isEmpty()) return false;

        Set<String> allVars = new LinkedHashSet<>();
        allVars.addAll(row1.keySet());
        allVars.addAll(row2.keySet());

        for (String var : allVars) {
            List<ConditionConstraint> c1 = row1.get(var);
            List<ConditionConstraint> c2 = row2.get(var);
            if (c1 == null || c2 == null) continue; // 一方无约束，视为有交集
            if (!constraintListsOverlap(c1, c2)) return false;
        }
        return true;
    }

    /**
     * 判断两组规则条件是否重叠。
     */
    private boolean conditionsOverlap(Map<String, List<ConditionConstraint>> cond1,
                                       Map<String, List<ConditionConstraint>> cond2) {
        if (cond1.isEmpty() || cond2.isEmpty()) return false;

        Set<String> allVars = new LinkedHashSet<>();
        allVars.addAll(cond1.keySet());
        allVars.addAll(cond2.keySet());

        for (String var : allVars) {
            List<ConditionConstraint> c1 = cond1.get(var);
            List<ConditionConstraint> c2 = cond2.get(var);
            if (c1 == null || c2 == null) continue;
            if (!constraintListsOverlap(c1, c2)) return false;
        }
        return true;
    }

    /**
     * 判断两组约束列表是否有交集。
     * 数值类型：计算各自的范围，检查范围是否有交集。
     * 枚举/字符串类型：检查 Equals 值是否有交集。
     */
    private boolean constraintListsOverlap(List<ConditionConstraint> list1, List<ConditionConstraint> list2) {
        if (list1.isEmpty() || list2.isEmpty()) return true;
        Datatype dt = list1.get(0).getDatatype();
        if (isNumericType(dt)) {
            return numericRangesOverlap(list1, list2);
        } else {
            return enumValuesOverlap(list1, list2);
        }
    }

    /**
     * 数值范围重叠检测。
     * 将约束列表转为 [min, max] 范围，检查两个范围是否有交集。
     */
    private boolean numericRangesOverlap(List<ConditionConstraint> list1, List<ConditionConstraint> list2) {
        double[] range1 = computeNumericRange(list1);
        double[] range2 = computeNumericRange(list2);
        // range = [min, max]，交集存在条件: max1 >= min2 && max2 >= min1
        return range1[1] >= range2[0] && range2[1] >= range1[0];
    }

    /**
     * 从约束列表计算数值范围 [min, max]。
     */
    private double[] computeNumericRange(List<ConditionConstraint> constraints) {
        double min = Double.NEGATIVE_INFINITY;
        double max = Double.POSITIVE_INFINITY;
        for (ConditionConstraint cc : constraints) {
            if (cc.getOp() == null || cc.getValue() == null) continue;
            double val;
            try { val = Double.parseDouble(cc.getValue()); } catch (NumberFormatException e) { continue; }
            switch (cc.getOp()) {
                case Equals:
                    min = Math.max(min, val);
                    max = Math.min(max, val);
                    break;
                case GreaterThen:
                    min = Math.max(min, val + 0.0001); // 严格大于
                    break;
                case GreaterThenEquals:
                    min = Math.max(min, val);
                    break;
                case LessThen:
                    max = Math.min(max, val - 0.0001); // 严格小于
                    break;
                case LessThenEquals:
                    max = Math.min(max, val);
                    break;
                default:
                    break;
            }
        }
        return new double[]{min, max};
    }

    /**
     * 枚举/字符串值重叠检测。
     * 如果两组都有 Equals 条件，检查值集合是否有交集。
     * 如果任一组没有 Equals 条件（如只有 NotEquals），保守地认为有交集。
     */
    private boolean enumValuesOverlap(List<ConditionConstraint> list1, List<ConditionConstraint> list2) {
        Set<String> vals1 = extractEqualsValues(list1);
        Set<String> vals2 = extractEqualsValues(list2);
        if (vals1.isEmpty() || vals2.isEmpty()) return true; // 保守判断
        for (String v : vals1) {
            if (vals2.contains(v)) return true;
        }
        return false;
    }

    private Set<String> extractEqualsValues(List<ConditionConstraint> constraints) {
        Set<String> values = new LinkedHashSet<>();
        for (ConditionConstraint cc : constraints) {
            if (cc.getOp() == Op.Equals && cc.getValue() != null) {
                values.add(cc.getValue());
            }
        }
        return values;
    }

    /**
     * 检查数值类型分支是否完备覆盖全值域。
     * 完备条件：分支集合中存在互补的条件对，使得任意数值都至少满足一个分支。
     *
     * 简化策略：收集所有切分点，检查每个切分点两侧和边界是否都被覆盖。
     */
    boolean isNumericBranchesComplete(List<ConditionConstraint> branches) {
        // 收集所有切分点
        Set<Double> cutPoints = new TreeSet<>();
        boolean hasLessOrEqual = false;
        boolean hasGreaterOrEqual = false;

        for (ConditionConstraint cc : branches) {
            if (cc.getOp() == null || cc.getValue() == null) continue;
            double val;
            try { val = Double.parseDouble(cc.getValue()); } catch (NumberFormatException e) { continue; }
            cutPoints.add(val);

            switch (cc.getOp()) {
                case LessThen:
                case LessThenEquals:
                    hasLessOrEqual = true;
                    break;
                case GreaterThen:
                case GreaterThenEquals:
                    hasGreaterOrEqual = true;
                    break;
                case Equals:
                    // Equals 单独不构成范围覆盖
                    break;
                default:
                    break;
            }
        }

        if (cutPoints.isEmpty()) return false;

        // 必须同时有向左和向右的条件才可能完备
        if (!hasLessOrEqual || !hasGreaterOrEqual) return false;

        // 检查测试点：每个切分点、切分点之间、极端值
        List<Double> testPoints = new ArrayList<>();
        List<Double> sortedCuts = new ArrayList<>(cutPoints);
        testPoints.add(sortedCuts.get(0) - 1); // 最小切分点左侧
        for (int i = 0; i < sortedCuts.size(); i++) {
            testPoints.add(sortedCuts.get(i)); // 切分点本身
            if (i + 1 < sortedCuts.size()) {
                testPoints.add((sortedCuts.get(i) + sortedCuts.get(i + 1)) / 2); // 两切分点之间
            }
        }
        testPoints.add(sortedCuts.get(sortedCuts.size() - 1) + 1); // 最大切分点右侧

        for (double testVal : testPoints) {
            if (!anyBranchCovers(branches, testVal)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyBranchCovers(List<ConditionConstraint> branches, double testVal) {
        for (ConditionConstraint cc : branches) {
            if (cc.getOp() == null || cc.getValue() == null) continue;
            double val;
            try { val = Double.parseDouble(cc.getValue()); } catch (NumberFormatException e) { continue; }
            switch (cc.getOp()) {
                case Equals:
                    if (testVal == val) return true;
                    break;
                case GreaterThen:
                    if (testVal > val) return true;
                    break;
                case GreaterThenEquals:
                    if (testVal >= val) return true;
                    break;
                case LessThen:
                    if (testVal < val) return true;
                    break;
                case LessThenEquals:
                    if (testVal <= val) return true;
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean isNumericType(Datatype dt) {
        return dt == Datatype.Integer || dt == Datatype.Double
                || dt == Datatype.Long || dt == Datatype.Float
                || dt == Datatype.BigDecimal;
    }

    private static Datatype parseDatatype(String str) {
        if (str == null || str.isEmpty()) return Datatype.String;
        try { return Datatype.valueOf(str); } catch (IllegalArgumentException e) { return Datatype.String; }
    }

    private static String stripStoragePrefix(String path) {
        int colonIdx = path.indexOf(":");
        if (colonIdx > 0 && colonIdx < 5) {
            return path.substring(colonIdx + 1);
        }
        return path;
    }

    // ---- 内部数据结构 ----
    private static class RuleInfo {
        String name;
        boolean hasSalience;
        Map<String, List<ConditionConstraint>> conditions;
    }

    // ---- Spring setter 注入 ----
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public void setConditionParser(ConditionParser conditionParser) {
        this.conditionParser = conditionParser;
    }
}

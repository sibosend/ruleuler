package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.RuleException;
import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.library.variable.VariableCategory;
import com.bstek.urule.model.rule.Op;
import com.bstek.urule.console.repository.RepositoryService;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionParser {

    private static final Map<String, Op> CHINESE_OP_MAP = new HashMap<>();

    static {
        for (Op op : Op.values()) {
            CHINESE_OP_MAP.put(op.toString(), op);
        }
    }

    private static final String OP_ALTERNATIVES;

    static {
        List<String> opNames = new ArrayList<>();
        for (Op op : Op.values()) {
            opNames.add(Pattern.quote(op.toString()));
        }
        // 长的放前面，避免"等于"先匹配到"等于(不分大小写)"的前缀
        opNames.sort((a, b) -> b.length() - a.length());
        OP_ALTERNATIVES = String.join("|", opNames);
    }

    private static final Pattern DSL_CONDITION_PATTERN = Pattern.compile(
            "(\\S+)\\.(\\S+)\\s+(" + OP_ALTERNATIVES + ")\\s*(.*)"
    );

    private final RepositoryService repositoryService;

    public ConditionParser(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    public Map<String, List<ConditionConstraint>> parse(String flowXmlPath) {
        Map<String, List<ConditionConstraint>> result = new LinkedHashMap<>();
        try {
            InputStream is = repositoryService.readFile(flowXmlPath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element root = doc.getRootElement();
            String flowDir = flowXmlPath.substring(0, flowXmlPath.lastIndexOf("/") + 1);

            for (Object obj : root.elements()) {
                if (!(obj instanceof Element)) continue;
                Element ele = (Element) obj;
                String name = ele.getName();
                String nodeName = ele.attributeValue("name");

                if ("decision".equals(name)) {
                    List<ConditionConstraint> constraints = parseDecisionNode(ele);
                    if (!constraints.isEmpty()) {
                        result.put(nodeName, constraints);
                    }
                } else if ("rule".equals(name)) {
                    String file = ele.attributeValue("file");
                    if (file != null && !file.isEmpty()) {
                        String filePath = stripStoragePrefix(file);
                        if (!filePath.startsWith("/")) {
                            filePath = flowDir + filePath;
                        }
                        List<ConditionConstraint> constraints = parseRuleFile(filePath);
                        if (!constraints.isEmpty()) {
                            result.put(nodeName, constraints);
                        }
                    }
                } else if ("fork".equals(name)) {
                    List<ConditionConstraint> constraints = parseForkConnections(ele);
                    if (!constraints.isEmpty()) {
                        result.put(nodeName, constraints);
                    }
                }
            }
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("解析决策流文件失败: " + flowXmlPath, e));
        }
        return result;
    }

    List<ConditionConstraint> parseDecisionNode(Element decisionElement) {
        List<ConditionConstraint> constraints = new ArrayList<>();
        String nodeName = decisionElement.attributeValue("name");
        for (Object obj : decisionElement.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"item".equals(ele.getName())) continue;
            String script = ele.getStringValue();
            if (script == null || script.trim().isEmpty()) continue;
            constraints.addAll(parseDslScript(script, nodeName));
        }
        return constraints;
    }

    List<ConditionConstraint> parseRuleFile(String ruleFilePath) {
        List<ConditionConstraint> constraints = new ArrayList<>();
        try {
            InputStream is = repositoryService.readFile(ruleFilePath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element root = doc.getRootElement();
            String rootName = root.getName();
            if ("decision-table".equals(rootName)) {
                constraints.addAll(parseDecisionTable(root, ruleFilePath));
            } else if ("decision-tree".equals(rootName)) {
                constraints.addAll(parseDecisionTree(root, ruleFilePath));
            } else if ("scorecard".equals(rootName)) {
                constraints.addAll(parseScorecard(root, ruleFilePath));
            } else {
                collectAtoms(root, constraints, ruleFilePath);
            }
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("解析规则文件失败: " + ruleFilePath, e));
        }
        return constraints;
    }

    /**
     * 决策表解析：从 col[Criteria] 提取变量定义，从 cell[condition] 提取 op+value，按行 AND 组合。
     */
    List<ConditionConstraint> parseDecisionTable(Element root, String sourceFile) {
        // 1. 收集 Criteria 列定义：colNum → col元素
        Map<Integer, Element> criteriaColumns = new LinkedHashMap<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"col".equals(ele.getName())) continue;
            if (!"Criteria".equals(ele.attributeValue("type"))) continue;
            int colNum = Integer.parseInt(ele.attributeValue("num"));
            criteriaColumns.put(colNum, ele);
        }
        if (criteriaColumns.isEmpty()) return new ArrayList<>();

        // 2. 收集行数
        int maxRow = -1;
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"row".equals(ele.getName())) continue;
            int rowNum = Integer.parseInt(ele.attributeValue("num"));
            if (rowNum > maxRow) maxRow = rowNum;
        }

        // 3. 收集 Criteria 列的 cell：(row, col) → cell元素
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

        // 4. 按行组合：每行的所有 Criteria 列条件 AND 在一起
        List<ConditionConstraint> constraints = new ArrayList<>();
        for (int row = 0; row <= maxRow; row++) {
            for (Map.Entry<Integer, Element> entry : criteriaColumns.entrySet()) {
                int colNum = entry.getKey();
                Element colDef = entry.getValue();
                Element cell = cellMap.get(row + "," + colNum);
                if (cell == null) continue;

                List<ConditionConstraint> cellConstraints = parseCellConditions(cell, colDef, sourceFile, row);
                constraints.addAll(cellConstraints);
            }
        }
        return constraints;
    }

    /**
     * 决策树解析：递归 variable-tree-node → condition-tree-node，每条根到叶路径一组约束。
     *
     * XML 结构:
     * <decision-tree>
     *   <variable-tree-node>
     *     <left var="x" var-label="X" datatype="Integer"/>
     *     <condition-tree-node op="GreaterThen">
     *       <value content="7"/>
     *       <action-tree-node/> ← 叶节点，产生一条路径
     *       <variable-tree-node> ← 嵌套变量节点，继续递归
     *       <condition-tree-node> ← 同变量的子条件，继续递归
     *     </condition-tree-node>
     *   </variable-tree-node>
     * </decision-tree>
     */
    List<ConditionConstraint> parseDecisionTree(Element root, String sourceFile) {
        List<ConditionConstraint> allConstraints = new ArrayList<>();
        // decision-tree 根下直接是一个 variable-tree-node
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("variable-tree-node".equals(ele.getName())) {
                List<List<ConditionConstraint>> paths = new ArrayList<>();
                walkVariableTreeNode(ele, sourceFile, new ArrayList<>(), paths);
                for (List<ConditionConstraint> path : paths) {
                    allConstraints.addAll(path);
                }
            }
        }
        return allConstraints;
    }

    /**
     * 处理 variable-tree-node：从 <left> 提取变量定义，遍历子 condition-tree-node。
     */
    private void walkVariableTreeNode(Element varNode, String sourceFile,
                                       List<ConditionConstraint> currentPath,
                                       List<List<ConditionConstraint>> completedPaths) {
        // 从 <left> 提取变量信息
        String varName = null, varLabel = null;
        String varCategory = null;
        Datatype datatype = Datatype.String;
        for (Object obj : varNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("left".equals(ele.getName())) {
                varName = ele.attributeValue("var");
                varLabel = ele.attributeValue("var-label");
                varCategory = ele.attributeValue("var-category");
                String dt = ele.attributeValue("datatype");
                if (dt != null && !dt.isEmpty()) {
                    try { datatype = Datatype.valueOf(dt); } catch (IllegalArgumentException ignored) {}
                }
                break;
            }
        }

        // 遍历子 condition-tree-node
        for (Object obj : varNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("condition-tree-node".equals(ele.getName())) {
                walkConditionTreeNode(ele, sourceFile, varName, varLabel, varCategory, datatype, currentPath, completedPaths);
            }
        }
    }

    /**
     * 处理 condition-tree-node：提取 op+value 构建约束，递归子节点。
     * 子节点可以是 action-tree-node（叶）、variable-tree-node（嵌套变量）、condition-tree-node（同变量子条件）。
     */
    private void walkConditionTreeNode(Element condNode, String sourceFile,
                                        String varName, String varLabel, String varCategory, Datatype datatype,
                                        List<ConditionConstraint> currentPath,
                                        List<List<ConditionConstraint>> completedPaths) {
        // 构建当前条件约束
        ConditionConstraint cc = new ConditionConstraint();
        cc.setVariableName(varName);
        cc.setVariableLabel(varLabel);
        cc.setVariableCategory(varCategory);
        cc.setDatatype(datatype);
        cc.setSourceNode(sourceFile);

        String opStr = condNode.attributeValue("op");
        if (opStr != null) {
            try { cc.setOp(Op.valueOf(opStr)); } catch (IllegalArgumentException e) { cc.setOp(Op.parse(opStr)); }
        }

        // 从子 <value> 提取值
        for (Object obj : condNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if ("value".equals(ele.getName())) {
                cc.setValue(ele.attributeValue("content"));
                break;
            }
        }

        List<ConditionConstraint> newPath = new ArrayList<>(currentPath);
        newPath.add(cc);

        // 检查子节点类型
        boolean hasChildren = false;
        for (Object obj : condNode.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            String name = ele.getName();
            if ("variable-tree-node".equals(name)) {
                hasChildren = true;
                walkVariableTreeNode(ele, sourceFile, newPath, completedPaths);
            } else if ("condition-tree-node".equals(name)) {
                hasChildren = true;
                walkConditionTreeNode(ele, sourceFile, varName, varLabel, varCategory, datatype, newPath, completedPaths);
            } else if ("action-tree-node".equals(name)) {
                hasChildren = true;
                // 叶节点：当前路径完成
                completedPaths.add(new ArrayList<>(newPath));
            }
        }

        // 如果没有任何子节点（异常情况），也作为一条路径
        if (!hasChildren) {
            completedPaths.add(new ArrayList<>(newPath));
        }
    }

    /**
     * 评分卡解析：card-cell[attribute] 定义变量，同行 card-cell[condition] 提取约束，每行独立产生一个约束。
     *
     * XML 结构:
     * <scorecard>
     *   <card-cell type="attribute" row="0" col="0" var="x" var-label="X" datatype="Integer"/>
     *   <card-cell type="condition" row="0" col="1">
     *     <joint><condition op="GreaterThen"><value content="7"/></condition></joint>
     *   </card-cell>
     * </scorecard>
     */
    List<ConditionConstraint> parseScorecard(Element root, String sourceFile) {
        // 评分卡根元素的默认变量类别
        String defaultCategory = root.attributeValue("attr-col-category");

        // 1. 按 row 收集 attribute cell 和 condition cell
        Map<Integer, Element> attributeCells = new LinkedHashMap<>();
        Map<Integer, List<Element>> conditionCells = new LinkedHashMap<>();

        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"card-cell".equals(ele.getName())) continue;
            String type = ele.attributeValue("type");
            int row = Integer.parseInt(ele.attributeValue("row"));
            if ("attribute".equals(type)) {
                attributeCells.put(row, ele);
            } else if ("condition".equals(type)) {
                conditionCells.computeIfAbsent(row, k -> new ArrayList<>()).add(ele);
            }
        }

        // 2. 每行：从 attribute cell 取变量定义，从 condition cell 取条件
        List<ConditionConstraint> constraints = new ArrayList<>();
        for (Map.Entry<Integer, Element> entry : attributeCells.entrySet()) {
            int row = entry.getKey();
            Element attrCell = entry.getValue();
            List<Element> condCells = conditionCells.get(row);
            if (condCells == null || condCells.isEmpty()) continue;

            String varName = attrCell.attributeValue("var");
            String varLabel = attrCell.attributeValue("var-label");
            String varCategory = attrCell.attributeValue("var-category");
            if (varCategory == null || varCategory.isEmpty()) varCategory = defaultCategory;
            String datatypeStr = attrCell.attributeValue("datatype");
            Datatype datatype = Datatype.String;
            if (datatypeStr != null && !datatypeStr.isEmpty()) {
                try { datatype = Datatype.valueOf(datatypeStr); } catch (IllegalArgumentException ignored) {}
            }

            for (Element condCell : condCells) {
                // condition cell 内部结构和决策表 cell 一样：joint > condition > value
                List<Element> conditions = new ArrayList<>();
                collectConditionElements(condCell, conditions);
                for (Element cond : conditions) {
                    String opStr = cond.attributeValue("op");
                    if (opStr == null) continue;
                    Op op;
                    try { op = Op.valueOf(opStr); } catch (IllegalArgumentException e) { op = Op.parse(opStr); }

                    String value = null;
                    for (Object child : cond.elements()) {
                        if (!(child instanceof Element)) continue;
                        Element childEle = (Element) child;
                        if ("value".equals(childEle.getName())) {
                            value = childEle.attributeValue("content");
                            break;
                        }
                    }

                    ConditionConstraint cc = new ConditionConstraint();
                    cc.setVariableName(varName);
                    cc.setVariableLabel(varLabel);
                    cc.setVariableCategory(varCategory);
                    cc.setOp(op);
                    cc.setValue(value);
                    cc.setDatatype(datatype);
                    cc.setSourceNode(sourceFile + "#row" + row);
                    constraints.add(cc);
                }
            }
        }
        return constraints;
    }

    /**
     * 解析单个 cell 中的 condition 元素，提取 op 和 value。
     * cell 结构: <cell><joint type="and"><condition op="Equals"><value content="B737"/></condition></joint></cell>
     */
    private List<ConditionConstraint> parseCellConditions(Element cell, Element colDef, String sourceFile, int row) {
        List<ConditionConstraint> result = new ArrayList<>();
        String varName = colDef.attributeValue("var");
        String varLabel = colDef.attributeValue("var-label");
        String varCategory = colDef.attributeValue("var-category");
        String datatypeStr = colDef.attributeValue("datatype");
        Datatype datatype = Datatype.String;
        if (datatypeStr != null && !datatypeStr.isEmpty()) {
            try {
                datatype = Datatype.valueOf(datatypeStr);
            } catch (IllegalArgumentException ignored) {}
        }

        // 递归查找所有 <condition> 元素
        List<Element> conditions = new ArrayList<>();
        collectConditionElements(cell, conditions);

        for (Element cond : conditions) {
            String opStr = cond.attributeValue("op");
            if (opStr == null) continue;
            Op op;
            try {
                op = Op.valueOf(opStr);
            } catch (IllegalArgumentException e) {
                op = Op.parse(opStr);
            }

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
            cc.setVariableLabel(varLabel);
            cc.setVariableCategory(varCategory);
            cc.setOp(op);
            cc.setValue(value);
            cc.setDatatype(datatype);
            cc.setSourceNode(sourceFile + "#row" + row);
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

    List<ConditionConstraint> parseForkConnections(Element forkElement) {
        List<ConditionConstraint> constraints = new ArrayList<>();
        String nodeName = forkElement.attributeValue("name");
        for (Object obj : forkElement.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            if (!"connection".equals(ele.getName())) continue;
            String script = ele.getStringValue();
            if (script == null || script.trim().isEmpty()) continue;
            String connName = ele.attributeValue("name");
            String sourceNode = connName != null ? connName : nodeName;
            constraints.addAll(parseDslScript(script, sourceNode));
        }
        return constraints;
    }

    private List<ConditionConstraint> parseDslScript(String script, String sourceNode) {
        List<ConditionConstraint> constraints = new ArrayList<>();
        String[] lines = script.split("[\\r\\n]+");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            Matcher m = DSL_CONDITION_PATTERN.matcher(line);
            if (m.find()) {
                ConditionConstraint cc = new ConditionConstraint();
                cc.setVariableLabel(m.group(1));
                cc.setVariableName(m.group(2));
                String opStr = m.group(3);
                cc.setOp(CHINESE_OP_MAP.get(opStr));
                String value = m.group(4);
                cc.setValue(value != null ? value.trim() : null);
                cc.setDatatype(Datatype.String);
                cc.setSourceNode(sourceNode);
                constraints.add(cc);
            }
        }
        return constraints;
    }

    /**
     * 按规则文件中的每条 rule 独立提取条件。
     * 返回 ruleName → constraints 的映射。
     */
    Map<String, List<ConditionConstraint>> parseRuleFileByRule(String ruleFilePath) {
        Map<String, List<ConditionConstraint>> result = new LinkedHashMap<>();
        try {
            InputStream is = repositoryService.readFile(ruleFilePath);
            SAXReader reader = new SAXReader();
            Document doc = reader.read(is);
            Element root = doc.getRootElement();
            // 遍历每个 <rule> 元素
            for (Object obj : root.elements()) {
                if (!(obj instanceof Element)) continue;
                Element ruleEle = (Element) obj;
                if (!"rule".equals(ruleEle.getName())) continue;
                String ruleName = ruleEle.attributeValue("name");
                if (ruleName == null) ruleName = "unnamed";
                List<ConditionConstraint> constraints = new ArrayList<>();
                collectAtoms(ruleEle, constraints, ruleFilePath);
                if (!constraints.isEmpty()) {
                    result.put(ruleName, constraints);
                }
            }
        } catch (RuleException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleException(new RuntimeException("解析规则文件失败: " + ruleFilePath, e));
        }
        return result;
    }

    private static String stripStoragePrefix(String path) {
        int colonIdx = path.indexOf(":");
        if (colonIdx > 0 && colonIdx < 5) {
            return path.substring(colonIdx + 1);
        }
        return path;
    }

    private void collectAtoms(Element element, List<ConditionConstraint> constraints, String sourceFile) {
        if ("atom".equals(element.getName())) {
            ConditionConstraint cc = parseAtomElement(element, sourceFile);
            if (cc != null) {
                constraints.add(cc);
            }
            return;
        }
        for (Object obj : element.elements()) {
            if (obj instanceof Element) {
                collectAtoms((Element) obj, constraints, sourceFile);
            }
        }
    }

    private ConditionConstraint parseAtomElement(Element atom, String sourceFile) {
        String opStr = atom.attributeValue("op");
        if (opStr == null) return null;

        Op op;
        try {
            op = Op.valueOf(opStr);
        } catch (IllegalArgumentException e) {
            op = Op.parse(opStr);
        }

        ConditionConstraint cc = new ConditionConstraint();
        cc.setOp(op);
        cc.setSourceNode(sourceFile);

        for (Object obj : atom.elements()) {
            if (!(obj instanceof Element)) continue;
            Element child = (Element) obj;
            if ("left".equals(child.getName())) {
                String varName = child.attributeValue("var");
                if (varName != null) cc.setVariableName(varName);
                String varLabel = child.attributeValue("var-label");
                if (varLabel != null) cc.setVariableLabel(varLabel);
                String varCategory = child.attributeValue("var-category");
                if (varCategory != null) cc.setVariableCategory(varCategory);
                String leftType = child.attributeValue("type");
                if ("parameter".equals(leftType)) cc.setVariableCategory(VariableCategory.PARAM_CATEGORY);
                String datatype = child.attributeValue("datatype");
                if (datatype != null && !datatype.isEmpty()) {
                    try {
                        cc.setDatatype(Datatype.valueOf(datatype));
                    } catch (IllegalArgumentException e) {
                        cc.setDatatype(Datatype.String);
                    }
                } else {
                    cc.setDatatype(Datatype.String);
                }
            } else if ("value".equals(child.getName())) {
                String content = child.attributeValue("content");
                cc.setValue(content);
            }
        }
        return cc;
    }
}

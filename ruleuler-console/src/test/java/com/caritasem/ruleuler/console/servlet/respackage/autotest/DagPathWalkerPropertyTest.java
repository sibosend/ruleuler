package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import net.jqwik.api.*;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DagPathWalkerPropertyTest {

    private final DagPathWalker walker = new DagPathWalker();

    private Element buildFlowXml() {
        Document doc = DocumentHelper.createDocument();
        return doc.addElement("flow-definition");
    }

    private void addStartNode(Element root, String name, String connectionTo) {
        Element start = root.addElement("start");
        start.addAttribute("name", name);
        Element conn = start.addElement("connection");
        conn.addAttribute("to", connectionTo);
    }

    private void addEndNode(Element root, String name) {
        Element end = root.addElement("end");
        end.addAttribute("name", name);
    }

    private void addRuleNode(Element root, String name, String connectionTo) {
        Element rule = root.addElement("rule");
        rule.addAttribute("name", name);
        rule.addAttribute("file", "/dummy.rs.xml");
        Element conn = rule.addElement("connection");
        conn.addAttribute("to", connectionTo);
    }

    private void addDecisionNode(Element root, String name, List<String> itemTargets) {
        Element decision = root.addElement("decision");
        decision.addAttribute("name", name);
        for (String target : itemTargets) {
            Element item = decision.addElement("item");
            item.addAttribute("connection", target);
            item.setText("参数.x 等于 1");
        }
    }

    private void addForkNode(Element root, String name, List<String> connectionTargets) {
        Element fork = root.addElement("fork");
        fork.addAttribute("name", name);
        for (String target : connectionTargets) {
            Element conn = fork.addElement("connection");
            conn.addAttribute("to", target);
            conn.addAttribute("name", name + "_to_" + target);
        }
    }

    // Feature: auto-test-generator, Property 2: 路径起止正确性
    // Validates: Requirements 2.1

    @Property(tries = 100)
    void everyPathStartsAtStartAndEndsAtEnd(@ForAll("intermediateCount") int midCount) {
        Element root = buildFlowXml();
        String startName = "开始";
        String endName = "结束";

        if (midCount == 0) {
            addStartNode(root, startName, endName);
        } else {
            addStartNode(root, startName, "rule0");
            for (int i = 0; i < midCount; i++) {
                String next = (i == midCount - 1) ? endName : "rule" + (i + 1);
                addRuleNode(root, "rule" + i, next);
            }
        }
        addEndNode(root, endName);

        List<DagPath> paths = walker.walkAllPaths(root, Collections.emptyMap());

        assertFalse(paths.isEmpty(), "至少应有一条路径");
        for (DagPath path : paths) {
            List<String> nodes = path.getNodeNames();
            assertEquals(startName, nodes.get(0), "首节点必须是 StartNode");
            assertEquals(endName, nodes.get(nodes.size() - 1), "末节点必须是 EndNode");
        }
    }

    @Provide
    Arbitrary<Integer> intermediateCount() {
        return Arbitraries.integers().between(0, 3);
    }

    // Feature: auto-test-generator, Property 3: 分支节点路径展开
    // Validates: Requirements 2.3, 2.4

    @Property(tries = 100)
    void decisionNodeExpandsToAtLeastNPaths(@ForAll("branchCount") int n) {
        Element root = buildFlowXml();
        String endName = "结束";

        addStartNode(root, "开始", "决策");

        List<String> targets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String ruleName = "rule" + i;
            targets.add(ruleName);
            addRuleNode(root, ruleName, endName);
        }
        addDecisionNode(root, "决策", targets);
        addEndNode(root, endName);

        List<DagPath> paths = walker.walkAllPaths(root, Collections.emptyMap());
        assertTrue(paths.size() >= n,
                "DecisionNode 有 " + n + " 个分支，路径数应 >= " + n + "，实际 " + paths.size());
    }

    @Property(tries = 100)
    void forkNodeExpandsToAtLeastMPaths(@ForAll("forkCount") int m) {
        Element root = buildFlowXml();
        String endName = "结束";

        addStartNode(root, "开始", "分支");

        List<String> targets = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            String ruleName = "frule" + i;
            targets.add(ruleName);
            addRuleNode(root, ruleName, endName);
        }
        addForkNode(root, "分支", targets);
        addEndNode(root, endName);

        List<DagPath> paths = walker.walkAllPaths(root, Collections.emptyMap());
        assertTrue(paths.size() >= m,
                "ForkNode 有 " + m + " 条连线，路径数应 >= " + m + "，实际 " + paths.size());
    }

    @Provide
    Arbitrary<Integer> branchCount() {
        return Arbitraries.integers().between(2, 5);
    }

    @Provide
    Arbitrary<Integer> forkCount() {
        return Arbitraries.integers().between(2, 4);
    }

    // Feature: auto-test-generator, Property 4: 路径约束收集完整性
    // Validates: Requirements 2.5

    @Property(tries = 100)
    void pathConstraintsMatchTraversedNodes(@ForAll("dummyTrigger") int ignored) {
        Element root = buildFlowXml();
        String endName = "结束";

        addStartNode(root, "开始", "决策");
        addDecisionNode(root, "决策", List.of("rule0", "rule1"));
        addRuleNode(root, "rule0", endName);
        addRuleNode(root, "rule1", endName);
        addEndNode(root, endName);

        ConditionConstraint ccDecision = makeConstraint("决策", "varD");
        ConditionConstraint ccRule0 = makeConstraint("rule0", "varR0");
        ConditionConstraint ccRule1 = makeConstraint("rule1", "varR1");

        Map<String, List<ConditionConstraint>> nodeConstraints = new HashMap<>();
        nodeConstraints.put("决策", List.of(ccDecision));
        nodeConstraints.put("rule0", List.of(ccRule0));
        nodeConstraints.put("rule1", List.of(ccRule1));

        List<DagPath> paths = walker.walkAllPaths(root, nodeConstraints);
        assertEquals(2, paths.size(), "应有 2 条路径");

        for (DagPath path : paths) {
            List<String> nodes = path.getNodeNames();
            List<ConditionConstraint> constraints = path.getConstraints();
            Set<String> constraintSources = new HashSet<>();
            for (ConditionConstraint cc : constraints) {
                constraintSources.add(cc.getSourceNode());
            }

            for (String node : nodes) {
                if (nodeConstraints.containsKey(node)) {
                    assertTrue(constraintSources.contains(node),
                            "路径经过节点 " + node + "，其约束应被收集");
                }
            }

            for (String nodeWithConstraint : nodeConstraints.keySet()) {
                if (!nodes.contains(nodeWithConstraint)) {
                    assertFalse(constraintSources.contains(nodeWithConstraint),
                            "路径未经过节点 " + nodeWithConstraint + "，其约束不应出现");
                }
            }
        }
    }

    @Provide
    Arbitrary<Integer> dummyTrigger() {
        return Arbitraries.integers().between(0, 99);
    }

    private ConditionConstraint makeConstraint(String sourceNode, String varName) {
        ConditionConstraint cc = new ConditionConstraint();
        cc.setSourceNode(sourceNode);
        cc.setVariableName(varName);
        cc.setOp(com.bstek.urule.model.rule.Op.Equals);
        cc.setValue("1");
        cc.setDatatype(com.bstek.urule.model.library.Datatype.Integer);
        return cc;
    }
}

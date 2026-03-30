package com.caritasem.ruleuler.console.servlet.respackage.autotest;

import com.bstek.urule.RuleException;
import org.dom4j.Element;

import java.util.*;

public class DagPathWalker {

    /**
     * DFS 遍历决策流 XML，返回所有 start→end 路径。
     */
    public List<DagPath> walkAllPaths(Element flowXml, Map<String, List<ConditionConstraint>> nodeConstraints) {
        Map<String, Element> nodeMap = buildNodeMap(flowXml);
        String startNodeName = findStartNodeName(nodeMap);
        List<DagPath> allPaths = new ArrayList<>();
        dfs(startNodeName, nodeMap, nodeConstraints,
                new ArrayList<>(), new ArrayList<>(), new HashSet<>(), allPaths);
        return allPaths;
    }

    private Map<String, Element> buildNodeMap(Element root) {
        Map<String, Element> map = new LinkedHashMap<>();
        for (Object obj : root.elements()) {
            if (!(obj instanceof Element)) continue;
            Element ele = (Element) obj;
            String name = ele.getName();
            if (isFlowNode(name)) {
                String nodeName = ele.attributeValue("name");
                if (nodeName != null) {
                    map.put(nodeName, ele);
                }
            }
        }
        return map;
    }

    private boolean isFlowNode(String elementName) {
        switch (elementName) {
            case "start": case "end": case "decision": case "rule":
            case "fork": case "join": case "action": case "script":
            case "rule-package":
                return true;
            default:
                return false;
        }
    }

    private String findStartNodeName(Map<String, Element> nodeMap) {
        for (Map.Entry<String, Element> entry : nodeMap.entrySet()) {
            if ("start".equals(entry.getValue().getName())) {
                return entry.getKey();
            }
        }
        throw new RuleException("决策流中未找到 StartNode");
    }

    private void dfs(String currentNodeName,
                     Map<String, Element> nodeMap,
                     Map<String, List<ConditionConstraint>> nodeConstraints,
                     List<String> currentPath,
                     List<ConditionConstraint> currentConstraints,
                     Set<String> visited,
                     List<DagPath> allPaths) {

        Element element = nodeMap.get(currentNodeName);
        if (element == null) return;

        String elementType = element.getName();

        // 到达 EndNode，记录路径
        if ("end".equals(elementType)) {
            currentPath.add(currentNodeName);
            DagPath path = new DagPath();
            path.setNodeNames(new ArrayList<>(currentPath));
            path.setConstraints(new ArrayList<>(currentConstraints));
            path.setDescription(String.join(" → ", currentPath));
            allPaths.add(path);
            currentPath.remove(currentPath.size() - 1);
            return;
        }

        // 环路检测
        if (visited.contains(currentNodeName)) return;

        visited.add(currentNodeName);
        currentPath.add(currentNodeName);

        // 收集当前节点的约束（精确匹配 + 前缀匹配 nodeName#ruleName）
        int constraintsBefore = currentConstraints.size();
        if (nodeConstraints != null) {
            for (Map.Entry<String, List<ConditionConstraint>> entry : nodeConstraints.entrySet()) {
                String key = entry.getKey();
                if (key.equals(currentNodeName) || key.startsWith(currentNodeName + "#")) {
                    currentConstraints.addAll(entry.getValue());
                }
            }
        }

        // 根据节点类型获取后继并递归
        List<String> successors = getSuccessors(element, elementType);
        for (String successor : successors) {
            dfs(successor, nodeMap, nodeConstraints,
                    currentPath, currentConstraints, visited, allPaths);
        }

        // 回溯
        while (currentConstraints.size() > constraintsBefore) {
            currentConstraints.remove(currentConstraints.size() - 1);
        }
        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentNodeName);
    }

    private List<String> getSuccessors(Element element, String elementType) {
        List<String> successors = new ArrayList<>();
        if ("decision".equals(elementType)) {
            // DecisionNode: <item connection="targetNodeName">
            for (Object obj : element.elements()) {
                if (!(obj instanceof Element)) continue;
                Element child = (Element) obj;
                if ("item".equals(child.getName())) {
                    String target = child.attributeValue("connection");
                    if (target != null) {
                        successors.add(target);
                    }
                }
            }
        } else {
            // 所有其他节点: <connection to="targetNodeName">
            for (Object obj : element.elements()) {
                if (!(obj instanceof Element)) continue;
                Element child = (Element) obj;
                if ("connection".equals(child.getName())) {
                    String target = child.attributeValue("to");
                    if (target != null) {
                        successors.add(target);
                    }
                }
            }
        }
        return successors;
    }
}

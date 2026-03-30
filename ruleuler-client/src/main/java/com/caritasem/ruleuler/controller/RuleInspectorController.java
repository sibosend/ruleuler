package com.caritasem.ruleuler.controller;

import com.bstek.urule.Utils;
import com.bstek.urule.model.rete.Rete;
import com.bstek.urule.model.rete.ObjectTypeNode;
import com.bstek.urule.model.rete.TerminalNode;
import com.bstek.urule.model.rete.BaseReteNode;
import com.bstek.urule.model.rete.Line;
import com.bstek.urule.model.rule.Rule;
import com.bstek.urule.model.flow.FlowDefinition;
import com.bstek.urule.model.flow.FlowNode;
import com.bstek.urule.model.flow.RuleNode;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgePackageWrapper;
import com.bstek.urule.runtime.service.KnowledgeService;
import com.caritasem.ruleuler.dto.RespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/inspector")
public class RuleInspectorController {

    private static Logger log = LoggerFactory.getLogger(RuleInspectorController.class);

    @PostMapping("/variables/{project}/{packageId}")
    public RespDTO extractVariables(@PathVariable String project,
                                     @PathVariable String packageId) {
        try {
            // 构建 knowledge package 路径
            String knowledgePackagePath = project + "/" + packageId;
            
            log.info("Loading knowledge package: {}", knowledgePackagePath);
            
            // 从 KnowledgeService 获取 KnowledgePackage
            KnowledgeService knowledgeService = (KnowledgeService) Utils.getApplicationContext()
                    .getBean(KnowledgeService.BEAN_ID);
            KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(knowledgePackagePath);
            
            if (knowledgePackage == null) {
                return new RespDTO(404, "Knowledge package not found: " + knowledgePackagePath);
            }
            
            // 调试：打印 KnowledgePackage 信息
            log.info("KnowledgePackage ID: {}", knowledgePackage.getId());
            log.info("Available methods in KnowledgePackage:");
            for (java.lang.reflect.Method m : knowledgePackage.getClass().getMethods()) {
                if (m.getName().startsWith("get") && m.getParameterCount() == 0 && 
                    !m.getName().equals("getClass")) {
                    try {
                        Object value = m.invoke(knowledgePackage);
                        if (value instanceof Collection) {
                            log.info("  {} -> Collection size: {}", m.getName(), ((Collection<?>) value).size());
                        } else if (value instanceof Map) {
                            log.info("  {} -> Map size: {}", m.getName(), ((Map<?, ?>) value).size());
                        } else {
                            log.info("  {} -> {}", m.getName(), value != null ? value.getClass().getSimpleName() : "null");
                        }
                    } catch (Exception e) {
                        log.info("  {} -> Error: {}", m.getName(), e.getMessage());
                    }
                }
            }
            
            // 提取所有变量
            Set<String> allVariables = new HashSet<>();
            List<Map<String, Object>> rulesList = new ArrayList<>();
            List<Map<String, Object>> flowsList = new ArrayList<>();
            
            // 从 FlowMap 提取 (决策流)
            Map<String, ?> flowMap = knowledgePackage.getFlowMap();
            Map<String, Set<String>> variableToFiles = new HashMap<>(); // 变量 -> 使用它的文件列表
            
            if (flowMap != null && !flowMap.isEmpty()) {
                log.info("Found {} flows in package", flowMap.size());
                for (Map.Entry<String, ?> entry : flowMap.entrySet()) {
                    String flowName = entry.getKey();
                    Object flow = entry.getValue();
                    
                    log.info("Processing flow: {}", flowName);
                    
                    Set<String> flowVariables = new HashSet<>();
                    List<Map<String, Object>> ruleNodes = new ArrayList<>();
                    
                    if (flow instanceof FlowDefinition) {
                        FlowDefinition flowDef = (FlowDefinition) flow;
                        List<FlowNode> nodes = flowDef.getNodes();
                        if (nodes != null) {
                            for (FlowNode node : nodes) {
                                if (node instanceof RuleNode) {
                                    RuleNode ruleNode = (RuleNode) node;
                                    String ruleFile = ruleNode.getFile();
                    
                                    // 从 RuleNode 的 KnowledgePackageWrapper 提取变量
                                    Set<String> nodeVariables = new HashSet<>();
                                    KnowledgePackageWrapper wrapper = ruleNode.getKnowledgePackageWrapper();
                                    if (wrapper != null) {
                                        KnowledgePackage nodeKp = wrapper.getKnowledgePackage();
                                        if (nodeKp != null) {
                                            Set<Object> visited = new HashSet<>();
                                            extractVariablesFromObject(nodeKp, nodeVariables, visited);
                                        }
                                    }
                                    
                                    // 记录变量与文件的关联
                                    for (String var : nodeVariables) {
                                        variableToFiles.computeIfAbsent(var, k -> new HashSet<>()).add(ruleFile);
                                    }
                                    flowVariables.addAll(nodeVariables);
                                    
                                    Map<String, Object> nodeInfo = new HashMap<>();
                                    nodeInfo.put("name", ruleNode.getName());
                                    nodeInfo.put("file", ruleFile);
                                    nodeInfo.put("version", ruleNode.getVersion());
                                    nodeInfo.put("variables", new ArrayList<>(nodeVariables));
                                    ruleNodes.add(nodeInfo);
                                }
                            }
                        }
                    }
                    
                    allVariables.addAll(flowVariables);

                    Map<String, Object> flowInfo = new HashMap<>();
                    flowInfo.put("name", flowName);
                    flowInfo.put("type", "decisionFlow");
                    flowInfo.put("variableCount", flowVariables.size());
                    flowInfo.put("variables", new ArrayList<>(flowVariables));
                    flowInfo.put("ruleNodes", ruleNodes);
                    flowsList.add(flowInfo);
                }
            }
            
            // 从 Rete 网络提取 (规则)
            Rete rete = knowledgePackage.getRete();
            if (rete != null) {
                List<ObjectTypeNode> objectTypeNodes = rete.getObjectTypeNodes();
                if (objectTypeNodes != null) {
                    for (ObjectTypeNode typeNode : objectTypeNodes) {
                        log.info("Processing ObjectTypeNode: {}", typeNode.getObjectTypeClass());
                        extractFromLines(typeNode.getLines(), allVariables, rulesList);
                    }
                }
            }
            
            // 从 noLhsRules 提取
            List<Rule> noLhsRules = knowledgePackage.getNoLhsRules();
            if (noLhsRules != null) {
                for (Rule rule : noLhsRules) {
                    log.info("processing noLhsRules {} ", rule.getName());
                    extractFromRule(rule, allVariables, rulesList, "noLhsRule");
                }
            }
            
            // 从 withElseRules 提取
            List<Rule> withElseRules = knowledgePackage.getWithElseRules();
            if (withElseRules != null) {
                for (Rule rule : withElseRules) {
                    log.info("processing withElseRules {} ", rule.getName());
                    extractFromRule(rule, allVariables, rulesList, "withElseRule");
                }
            }
            
            // 获取变量分类映射
            Map<String, String> variableCategoryMap = knowledgePackage.getVariableCateogoryMap();
            
            // 构建结果
            List<Map<String, Object>> variableList = new ArrayList<>();
            for (String varFullName : allVariables) {
                String[] parts = varFullName.split("\\.", 2);
                if (parts.length == 2) {
                    Map<String, Object> varInfo = new HashMap<>();
                    varInfo.put("category", parts[0]);
                    varInfo.put("name", parts[1]);
                    varInfo.put("fullName", varFullName);
                    varInfo.put("categoryClass", variableCategoryMap.get(parts[0]));
                    varInfo.put("usedByFiles", new ArrayList<>(variableToFiles.getOrDefault(varFullName, Collections.emptySet())));
                    variableList.add(varInfo);
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("project", project);
            result.put("packageId", packageId);
            result.put("knowledgePackagePath", knowledgePackagePath);
            result.put("totalFlows", flowsList.size());
            result.put("totalRules", rulesList.size());
            result.put("totalVariables", variableList.size());
            result.put("variables", variableList);
            result.put("variableCategoryMap", variableCategoryMap);
            
            log.info("Extracted {} variables from {} flows + {} rules in {}/{}", 
                    variableList.size(), flowsList.size(), rulesList.size(), project, packageId);
            
            return new RespDTO(200, "ok", result);
            
        } catch (Exception e) {
            log.error("Failed to extract variables from knowledge package", e);
            return new RespDTO(500, e.getMessage());
        }
    }
    
    private void extractFromLines(List<Line> lines, Set<String> allVariables, List<Map<String, Object>> rulesList) {
        if (lines == null || lines.isEmpty()) return;
        
        for (Line line : lines) {
            if (line.getTo() == null) continue;
            
            if (line.getTo() instanceof TerminalNode) {
                TerminalNode terminalNode = (TerminalNode) line.getTo();
                Rule rule = terminalNode.getRule();
                extractFromRule(rule, allVariables, rulesList, "normalRule");
            } else if (line.getTo() instanceof BaseReteNode) {
                BaseReteNode reteNode = (BaseReteNode) line.getTo();
                extractFromLines(reteNode.getLines(), allVariables, rulesList);
            }
        }
    }
    
    private void extractFromRule(Rule rule, Set<String> allVariables, List<Map<String, Object>> rulesList, String ruleType) {
        if (rule == null) return;
        
        Set<String> ruleVariables = new HashSet<>();
        Set<Object> visited = new HashSet<>();
        extractVariablesFromObject(rule, ruleVariables, visited);
        allVariables.addAll(ruleVariables);
        
        Map<String, Object> ruleInfo = new HashMap<>();
        ruleInfo.put("name", rule.getName());
        ruleInfo.put("type", ruleType);
        ruleInfo.put("variableCount", ruleVariables.size());
        ruleInfo.put("variables", new ArrayList<>(ruleVariables));
        rulesList.add(ruleInfo);
        
        log.info("Rule '{}' uses {} variables: {}", rule.getName(), ruleVariables.size(), ruleVariables);
    }
    
    private void extractVariablesFromObject(Object obj, Set<String> variables, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) return;
        visited.add(obj);
        
        try {
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            
            for (java.lang.reflect.Method method : methods) {
                try {
                    // 查找 variableCategory + variableName
                    if (method.getName().equals("getVariableCategory") && method.getParameterCount() == 0) {
                        String category = (String) method.invoke(obj);
                        if (category != null) {
                            for (java.lang.reflect.Method m2 : methods) {
                                if (m2.getName().equals("getVariableName") && m2.getParameterCount() == 0) {
                                    String varName = (String) m2.invoke(obj);
                                    if (varName != null) {
                                        variables.add(category + "." + varName);
                                    }
                                }
                            }
                        }
                    }
                    
                    // 递归处理
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0 && 
                        !method.getName().equals("getClass")) {
                        Object result = method.invoke(obj);
                        if (result instanceof List) {
                            for (Object item : (List<?>) result) {
                                extractVariablesFromObject(item, variables, visited);
                            }
                        } else if (result != null && result.getClass().getName().contains("bstek.urule")) {
                            extractVariablesFromObject(result, variables, visited);
                        }
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting variables from object: {}", e.getMessage());
        }
    }
}

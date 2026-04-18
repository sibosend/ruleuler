package com.caritasem.ruleuler.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bstek.urule.Utils;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.service.KnowledgeService;
import com.caritasem.ruleuler.dto.RuleExecutionResult;
import com.caritasem.ruleuler.service.RuleExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(name = "ruleuler.mcp.enabled", havingValue = "true")
public class RuleulerMcpTools {

    private static final Logger log = LoggerFactory.getLogger(RuleulerMcpTools.class);

    @Autowired
    private RuleExecutionService ruleExecutionService;

    @McpTool(name = "execute_rule",
            description = "Execute a RulEuler decision flow. Pass project name, knowledge package name, decision flow name and JSON input data grouped by variable category. Returns the decision result with only modified fields.")
    public String executeRule(
            @McpToolParam(description = "Project name", required = true) String project,
            @McpToolParam(description = "Knowledge package name", required = true) String knowledge,
            @McpToolParam(description = "Decision flow name", required = true) String process,
            @McpToolParam(description = "JSON input data grouped by variable category, e.g. {\"FlightInfo\":{\"aircraft_type\":\"A380\"}}", required = true) String data
    ) {
        try {
            Map<String, JSONObject> body = new LinkedHashMap<>();
            JSONObject parsed;
            try {
                parsed = JSON.parseObject(data);
            } catch (Exception e) {
                return "{\"status\":400,\"error\":\"Invalid JSON input: " + e.getMessage().replace("\"", "'") + "\"}";
            }
            if (parsed == null) {
                return "{\"status\":400,\"error\":\"Invalid JSON input: data is null\"}";
            }
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() instanceof JSONObject jo) {
                    body.put(entry.getKey(), jo);
                } else {
                    body.put(entry.getKey(), new JSONObject(Map.of(entry.getKey(), entry.getValue())));
                }
            }

            RuleExecutionResult r = ruleExecutionService.execute(project, knowledge, process, body);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", r.status());
            if (r.isSuccess()) {
                response.put("executionId", r.meta() != null ? r.meta().get("executionId") : null);
                response.put("packageId", r.meta() != null ? r.meta().get("packageId") : null);
                response.put("route", r.meta() != null ? r.meta().get("route") : null);
                response.put("data", r.data());
            } else {
                response.put("error", r.msg());
            }
            return JSON.toJSONString(response);

        } catch (Exception e) {
            log.error("execute_rule failed", e);
            return "{\"status\":500,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    @McpTool(name = "inspect_variables",
            description = "Inspect variable definitions of a RulEuler knowledge package. Returns variable list (category, name, associated files) and variable category mapping.")
    public String inspectVariables(
            @McpToolParam(description = "Project name", required = true) String project,
            @McpToolParam(description = "Knowledge package ID", required = true) String packageId
    ) {
        try {
            String knowledgePackagePath = project + "/" + packageId;

            KnowledgeService knowledgeService = (KnowledgeService) Utils.getApplicationContext()
                    .getBean(KnowledgeService.BEAN_ID);
            KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(knowledgePackagePath);

            if (knowledgePackage == null) {
                return "{\"status\":404,\"error\":\"Knowledge package not found: " + knowledgePackagePath + "\"}";
            }

            Set<String> allVariables = new HashSet<>();
            Map<String, Set<String>> variableToFiles = new HashMap<>();

            // 从 FlowMap 提取
            Map<String, ?> flowMap = knowledgePackage.getFlowMap();
            if (flowMap != null) {
                for (Map.Entry<String, ?> entry : flowMap.entrySet()) {
                    if (entry.getValue() instanceof com.bstek.urule.model.flow.FlowDefinition flowDef) {
                        var nodes = flowDef.getNodes();
                        if (nodes != null) {
                            for (var node : nodes) {
                                if (node instanceof com.bstek.urule.model.flow.RuleNode ruleNode) {
                                    Set<String> nodeVars = new HashSet<>();
                                    var wrapper = ruleNode.getKnowledgePackageWrapper();
                                    if (wrapper != null) {
                                        var nodeKp = wrapper.getKnowledgePackage();
                                        if (nodeKp != null) {
                                            Set<Object> visited = new HashSet<>();
                                            extractVariablesFromObject(nodeKp, nodeVars, visited);
                                        }
                                    }
                                    String ruleFile = ruleNode.getFile();
                                    for (String var : nodeVars) {
                                        variableToFiles.computeIfAbsent(var, k -> new HashSet<>()).add(ruleFile);
                                    }
                                    allVariables.addAll(nodeVars);
                                }
                            }
                        }
                    }
                }
            }

            Map<String, String> variableCategoryMap = knowledgePackage.getVariableCateogoryMap();

            List<Map<String, Object>> variableList = new ArrayList<>();
            for (String varFullName : allVariables) {
                String[] parts = varFullName.split("\\.", 2);
                if (parts.length == 2) {
                    Map<String, Object> varInfo = new LinkedHashMap<>();
                    varInfo.put("category", parts[0]);
                    varInfo.put("name", parts[1]);
                    varInfo.put("fullName", varFullName);
                    varInfo.put("categoryClass", variableCategoryMap.get(parts[0]));
                    varInfo.put("usedByFiles", new ArrayList<>(variableToFiles.getOrDefault(varFullName, Collections.emptySet())));
                    variableList.add(varInfo);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", 200);
            result.put("project", project);
            result.put("packageId", packageId);
            result.put("totalVariables", variableList.size());
            result.put("variables", variableList);
            result.put("variableCategoryMap", variableCategoryMap);

            return JSON.toJSONString(result);

        } catch (Exception e) {
            log.error("inspect_variables failed", e);
            return "{\"status\":500,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private void extractVariablesFromObject(Object obj, Set<String> variables, Set<Object> visited) {
        if (obj == null || visited.contains(obj)) return;
        visited.add(obj);
        try {
            for (var method : obj.getClass().getMethods()) {
                try {
                    if (method.getName().equals("getVariableCategory") && method.getParameterCount() == 0) {
                        String category = (String) method.invoke(obj);
                        if (category != null) {
                            for (var m2 : obj.getClass().getMethods()) {
                                if (m2.getName().equals("getVariableName") && m2.getParameterCount() == 0) {
                                    String varName = (String) m2.invoke(obj);
                                    if (varName != null) {
                                        variables.add(category + "." + varName);
                                    }
                                }
                            }
                        }
                    }
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0
                            && !method.getName().equals("getClass")) {
                        Object result = method.invoke(obj);
                        if (result instanceof List<?> list) {
                            for (Object item : list) {
                                extractVariablesFromObject(item, variables, visited);
                            }
                        } else if (result != null && result.getClass().getName().contains("bstek.urule")) {
                            extractVariablesFromObject(result, variables, visited);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting variables: {}", e.getMessage());
        }
    }
}

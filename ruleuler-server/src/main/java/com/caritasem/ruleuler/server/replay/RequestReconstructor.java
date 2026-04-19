package com.caritasem.ruleuler.server.replay;

import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.library.variable.Variable;
import com.caritasem.ruleuler.server.replay.model.ReconstructResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class RequestReconstructor {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final Random RANDOM = new Random();

    public Object restoreValue(String varType, Double valNum, String valStr) {
        if (valNum == null && valStr == null) return null;
        return switch (varType) {
            case "Integer" -> valNum != null ? valNum.intValue() : null;
            case "Double" -> valNum;
            case "Float" -> valNum != null ? valNum.floatValue() : null;
            case "Long" -> valNum != null ? valNum.longValue() : null;
            case "BigDecimal" -> valNum != null ? BigDecimal.valueOf(valNum) : null;
            case "Boolean" -> valNum != null ? valNum == 1.0 : null;
            case "String", "Char", "Enum" -> valStr;
            case "Date" -> parseDate(valStr);
            default -> valStr != null ? valStr : valNum;
        };
    }

    /**
     * 兼容旧签名
     */
    public ReconstructResult reconstruct(
            List<Map<String, Object>> varRows,
            Set<String> expectedCategories,
            Map<String, Map<String, String>> expectedVariables,
            String missingVarStrategy) {
        return reconstruct(varRows, expectedCategories, expectedVariables, missingVarStrategy, Collections.emptyMap());
    }

    public ReconstructResult reconstruct(
            List<Map<String, Object>> varRows,
            Set<String> expectedCategories,
            Map<String, Map<String, String>> expectedVariables,
            String missingVarStrategy,
            Map<String, Map<String, Variable>> variableDefs) {

        Map<String, Object> inputMap = new LinkedHashMap<>();
        Map<String, Object> outputMap = new LinkedHashMap<>();
        Map<String, String> varTypeMap = new LinkedHashMap<>();

        for (Map<String, Object> row : varRows) {
            String ioType = String.valueOf(row.get("io_type"));
            String category = String.valueOf(row.get("var_category"));
            String varName = String.valueOf(row.get("var_name"));
            String varType = String.valueOf(row.get("var_type"));
            Double valNum = row.get("val_num") != null ? ((Number) row.get("val_num")).doubleValue() : null;
            String valStr = row.get("val_str") != null ? String.valueOf(row.get("val_str")) : null;

            if (varName == null || varName.isEmpty()) continue;

            varTypeMap.put(category + "." + varName, varType);
            Object value = restoreValue(varType, valNum, valStr);
            Map<String, Object> target = "input".equals(ioType) ? inputMap : outputMap;
            String key = category + "." + varName;
            target.put(key, value);
        }

        Map<String, Map<String, Object>> inputByCategory = groupByCategory(inputMap);
        Map<String, Map<String, Object>> outputByCategory = groupByCategory(outputMap);

        List<String> missingCategories = new ArrayList<>();
        List<String> missingVariables = new ArrayList<>();

        // 补全缺失的 category 和 variable（从变量定义）
        if ("segment".equals(missingVarStrategy) && !variableDefs.isEmpty()) {
            for (Map.Entry<String, Map<String, Variable>> catEntry : variableDefs.entrySet()) {
                String cat = catEntry.getKey();
                Map<String, Variable> vars = catEntry.getValue();
                if (!inputByCategory.containsKey(cat)) {
                    missingCategories.add(cat);
                    inputByCategory.put(cat, new LinkedHashMap<>());
                }
                Map<String, Object> actual = inputByCategory.get(cat);
                for (Map.Entry<String, Variable> varEntry : vars.entrySet()) {
                    if (!actual.containsKey(varEntry.getKey())) {
                        missingVariables.add(cat + "." + varEntry.getKey());
                    }
                }
            }
        }

        if (expectedCategories != null) {
            for (String cat : expectedCategories) {
                if (!inputByCategory.containsKey(cat)) {
                    missingCategories.add(cat);
                    inputByCategory.put(cat, new LinkedHashMap<>());
                }
            }
        }

        if (expectedVariables != null) {
            for (Map.Entry<String, Map<String, String>> catEntry : expectedVariables.entrySet()) {
                String cat = catEntry.getKey();
                Map<String, String> vars = catEntry.getValue();
                Map<String, Object> actual = inputByCategory.get(cat);
                if (actual == null) continue;
                for (String varName : vars.keySet()) {
                    if (!actual.containsKey(varName)) {
                        missingVariables.add(cat + "." + varName);
                    }
                }
            }
        }

        // segment 填充：用变量定义的 defaultValue 或按类型填充零值
        List<String> filledVariables = new ArrayList<>();
        if ("segment".equals(missingVarStrategy)) {
            // 填充 null 值变量
            for (Map.Entry<String, Map<String, Object>> catEntry : inputByCategory.entrySet()) {
                String cat = catEntry.getKey();
                Map<String, Object> vars = catEntry.getValue();
                List<String> nullKeys = vars.entrySet().stream()
                        .filter(e -> e.getValue() == null)
                        .map(Map.Entry::getKey)
                        .toList();
                for (String varName : nullKeys) {
                    Object fillValue = resolveFillValue(cat, varName, varTypeMap, variableDefs);
                    vars.put(varName, fillValue);
                    filledVariables.add(cat + "." + varName);
                }
            }
            // 填充缺失变量（不在 varRows 中但在变量定义中的）
            for (String missingKey : missingVariables) {
                int dotIdx = missingKey.indexOf('.');
                String cat = missingKey.substring(0, dotIdx);
                String varName = missingKey.substring(dotIdx + 1);
                Map<String, Object> catVars = inputByCategory.get(cat);
                if (catVars != null && !catVars.containsKey(varName)) {
                    Object fillValue = resolveFillValue(cat, varName, varTypeMap, variableDefs);
                    catVars.put(varName, fillValue);
                    filledVariables.add(missingKey);
                }
            }
        }

        String completenessStatus = missingCategories.isEmpty() && missingVariables.isEmpty()
                && filledVariables.isEmpty()
                ? "COMPLETE" : "INCOMPLETE";

        return new ReconstructResult(
                inputByCategory,
                outputByCategory,
                missingCategories,
                missingVariables,
                filledVariables,
                completenessStatus
        );
    }

    /**
     * 从变量定义获取 defaultValue；无 defaultValue 则按 Datatype 填充类型零值
     */
    private Object resolveFillValue(String category, String varName,
                                     Map<String, String> varTypeMap,
                                     Map<String, Map<String, Variable>> variableDefs) {
        // 优先用变量定义的 defaultValue
        Map<String, Variable> catVars = variableDefs.get(category);
        if (catVars != null) {
            Variable var = catVars.get(varName);
            if (var != null && var.getDefaultValue() != null && !var.getDefaultValue().isEmpty()) {
                Object converted = convertDefaultValue(var.getType(), var.getDefaultValue());
                if (converted != null) return converted;
            }
            // 用变量定义的 Datatype 填充
            if (var != null && var.getType() != null) {
                return fillByDatatype(var.getType());
            }
        }
        // fallback：用 ClickHouse 日志里的 var_type
        String varType = varTypeMap.get(category + "." + varName);
        return fillByVarType(varType);
    }

    private Object convertDefaultValue(Datatype type, String defaultValue) {
        try {
            return type.convert(defaultValue);
        } catch (Exception e) {
            return null;
        }
    }

    private Object fillByDatatype(Datatype type) {
        return switch (type) {
            case Integer -> 0;
            case Long -> 0L;
            case Double -> 0.0;
            case Float -> 0.0f;
            case BigDecimal -> BigDecimal.ZERO;
            case Boolean -> false;
            case String -> "";
            case Char -> '\0';
            case Enum -> "";
            case Date -> new Date();
            case List -> Collections.emptyList();
            case Set -> Collections.emptySet();
            case Map -> Collections.emptyMap();
            case Object -> "";
        };
    }

    private Object fillByVarType(String varType) {
        if (varType == null) return 0;
        return switch (varType) {
            case "Integer" -> 0;
            case "Long" -> 0L;
            case "Double" -> 0.0;
            case "Float" -> 0.0f;
            case "BigDecimal" -> BigDecimal.ZERO;
            case "Boolean" -> false;
            case "String", "Char", "Enum" -> "";
            case "Date" -> new Date();
            default -> "";
        };
    }

    private Map<String, Map<String, Object>> groupByCategory(Map<String, Object> flatMap) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            String key = entry.getKey();
            int dotIdx = key.indexOf('.');
            if (dotIdx > 0) {
                String category = key.substring(0, dotIdx);
                String varName = key.substring(dotIdx + 1);
                result.computeIfAbsent(category, k -> new LinkedHashMap<>())
                        .put(varName, entry.getValue());
            }
        }
        return result;
    }

    private Object parseDate(String valStr) {
        if (valStr == null) return null;
        try {
            return new SimpleDateFormat(DATE_PATTERN).parse(valStr);
        } catch (Exception e) {
            return valStr;
        }
    }
}

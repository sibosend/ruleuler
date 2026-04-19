package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.server.replay.model.ReconstructResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class RequestReconstructor {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

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

    public ReconstructResult reconstruct(
            List<Map<String, Object>> varRows,
            Set<String> expectedCategories,
            Map<String, Map<String, String>> expectedVariables,
            String missingVarStrategy) {

        Map<String, Object> inputMap = new LinkedHashMap<>();
        Map<String, Object> outputMap = new LinkedHashMap<>();

        for (Map<String, Object> row : varRows) {
            String ioType = String.valueOf(row.get("io_type"));
            String category = String.valueOf(row.get("var_category"));
            String varName = String.valueOf(row.get("var_name"));
            String varType = String.valueOf(row.get("var_type"));
            Double valNum = row.get("val_num") != null ? ((Number) row.get("val_num")).doubleValue() : null;
            String valStr = row.get("val_str") != null ? String.valueOf(row.get("val_str")) : null;

            if (varName == null || varName.isEmpty()) continue;

            Object value = restoreValue(varType, valNum, valStr);
            Map<String, Object> target = "input".equals(ioType) ? inputMap : outputMap;
            String key = category + "." + varName;
            target.put(key, value);
        }

        Map<String, Map<String, Object>> inputByCategory = groupByCategory(inputMap);
        Map<String, Map<String, Object>> outputByCategory = groupByCategory(outputMap);

        List<String> missingCategories = new ArrayList<>();
        List<String> missingVariables = new ArrayList<>();

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

        String completenessStatus = missingCategories.isEmpty() && missingVariables.isEmpty()
                ? "COMPLETE" : "INCOMPLETE";

        return new ReconstructResult(
                inputByCategory,
                outputByCategory,
                missingCategories,
                missingVariables,
                Collections.emptyList(),
                completenessStatus
        );
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

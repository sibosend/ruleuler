package com.caritasem.ruleuler.server.replay;

import com.caritasem.ruleuler.server.replay.model.DiffResult;
import com.caritasem.ruleuler.server.replay.model.FieldDiff;
import com.caritasem.ruleuler.server.replay.model.ToleranceConfig;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DiffComparator {

    /**
     * 比较原始输出和回放输出
     * @param originalOutput category → {varName → value}（从 ClickHouse 日志重建）
     * @param replayOutput   category → {varName → value}（回放执行结果）
     */
    public DiffResult compare(
            Map<String, Map<String, Object>> originalOutput,
            Map<String, Map<String, Object>> replayOutput,
            ToleranceConfig tolerance) {

        List<FieldDiff> fields = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();

        // flatten: category.varName → value
        Map<String, Object> flatOriginal = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> catEntry : originalOutput.entrySet()) {
            for (Map.Entry<String, Object> varEntry : catEntry.getValue().entrySet()) {
                String key = catEntry.getKey() + "." + varEntry.getKey();
                flatOriginal.put(key, varEntry.getValue());
                allKeys.add(key);
            }
        }

        Map<String, Object> flatReplay = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> catEntry : replayOutput.entrySet()) {
            for (Map.Entry<String, Object> varEntry : catEntry.getValue().entrySet()) {
                String key = catEntry.getKey() + "." + varEntry.getKey();
                flatReplay.put(key, varEntry.getValue());
                allKeys.add(key);
            }
        }

        boolean allSame = true;
        for (String key : allKeys) {
            Object oldVal = flatOriginal.get(key);
            Object newVal = flatReplay.get(key);
            int dotIdx = key.indexOf('.');
            String category = dotIdx > 0 ? key.substring(0, dotIdx) : key;
            String name = dotIdx > 0 ? key.substring(dotIdx + 1) : key;

            if (!flatReplay.containsKey(key)) {
                // replay 未返回此字段 = 未被规则修改，视为一致
                fields.add(new FieldDiff(category, name, "SAME", oldVal, oldVal));
            } else if (!flatOriginal.containsKey(key)) {
                fields.add(new FieldDiff(category, name, "ADDED", null, newVal));
                allSame = false;
            } else if (valuesEqual(oldVal, newVal, tolerance)) {
                fields.add(new FieldDiff(category, name, "SAME", oldVal, newVal));
            } else {
                fields.add(new FieldDiff(category, name, "CHANGED", oldVal, newVal));
                allSame = false;
            }
        }

        return new DiffResult(allSame, fields);
    }

    private boolean valuesEqual(Object a, Object b, ToleranceConfig tolerance) {
        if (Objects.equals(a, b)) return true;
        if (a == null || b == null) return false;

        if (a instanceof Number na && b instanceof Number nb) {
            return withinTolerance(na.doubleValue(), nb.doubleValue(), tolerance);
        }
        return a.equals(b);
    }

    private boolean withinTolerance(double a, double b, ToleranceConfig tolerance) {
        if (a == b) return true;
        String mode = tolerance != null ? tolerance.mode() : "exact";
        double tolVal = tolerance != null ? tolerance.value() : 0;

        return switch (mode) {
            case "exact" -> a == b;
            case "absolute" -> Math.abs(a - b) <= tolVal;
            case "relative" -> {
                if (a == 0 && b == 0) yield true;
                double maxAbs = Math.max(Math.abs(a), Math.abs(b));
                yield maxAbs == 0 || Math.abs(a - b) / maxAbs <= tolVal;
            }
            default -> a == b;
        };
    }
}

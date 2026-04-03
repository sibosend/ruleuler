package com.caritasem.ruleuler.monitoring;

import com.bstek.urule.model.library.Datatype;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 根据 Datatype 枚举将运行时值映射为 (valNum, valStr) 二元组。
 * <p>
 * 映射规则：
 * - 数值型（Integer/Double/Float/Long/BigDecimal）→ valNum = Number.doubleValue()
 * - Boolean → valNum = true→1.0, false→0.0
 * - 字符串型（String/Char/Enum）→ valStr = toString()
 * - Date → valStr = "yyyy-MM-dd HH:mm:ss"
 * - 复合类型（List/Set/Map/Object）→ skip=true
 * - null 值 → missing（valNum 和 valStr 均为 null, skip=false）
 */
public final class VarTypeMapper {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 映射结果：skip=true 表示跳过不记录 */
    public record MapResult(Double valNum, String valStr, boolean skip) {}

    private VarTypeMapper() {}

    /**
     * 将 Datatype + 运行时值映射为 MapResult。
     *
     * @param type  变量的 Datatype 枚举
     * @param value 运行时值，可为 null
     * @return 映射结果
     */
    public static MapResult map(Datatype type, Object value) {
        // 复合类型直接跳过
        return switch (type) {
            case List, Set, Map, Object -> new MapResult(null, null, true);
            default -> mapNonComplex(type, value);
        };
    }

    private static MapResult mapNonComplex(Datatype type, Object value) {
        // null → missing
        if (value == null) {
            return new MapResult(null, null, false);
        }
        return switch (type) {
            case Integer, Double, Float, Long, BigDecimal -> toNumeric(value);
            case Boolean -> toBoolean(value);
            case String, Char, Enum -> new MapResult(null, value.toString(), false);
            case Date -> toDateStr(value);
            // 不应到达，但编译器要求穷举
            default -> new MapResult(null, null, true);
        };
    }

    private static MapResult toNumeric(Object value) {
        if (value instanceof Number num) {
            return new MapResult(num.doubleValue(), null, false);
        }
        // 非 Number 类型尝试解析
        return new MapResult(java.lang.Double.parseDouble(value.toString()), null, false);
    }

    private static MapResult toBoolean(Object value) {
        boolean b;
        if (value instanceof java.lang.Boolean bool) {
            b = bool;
        } else {
            b = java.lang.Boolean.parseBoolean(value.toString());
        }
        return new MapResult(b ? 1.0 : 0.0, null, false);
    }

    private static MapResult toDateStr(Object value) {
        if (value instanceof Date date) {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
            return new MapResult(null, sdf.format(date), false);
        }
        // 已经是字符串形式的日期，直接存
        return new MapResult(null, value.toString(), false);
    }
}

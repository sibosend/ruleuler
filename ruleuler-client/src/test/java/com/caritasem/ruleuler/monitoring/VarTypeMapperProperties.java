package com.caritasem.ruleuler.monitoring;

import com.bstek.urule.model.library.Datatype;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * VarTypeMapper 属性测试。
 * Feature: variable-monitoring
 */
class VarTypeMapperProperties {

    // ========== Property 1: 类型映射正确性 ==========

    /**
     * Property 1: 类型映射正确性 — Integer
     * 随机 int 值，验证 valNum = doubleValue(), valStr = null, skip = false
     * Validates: Requirements 1.8
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Integer")
    void integerMapsToValNum(@ForAll int value) {
        var result = VarTypeMapper.map(Datatype.Integer, value);
        assert result.valNum() != null : "Integer 应产生 valNum";
        assert result.valNum() == ((Number) value).doubleValue() : "valNum 应等于 doubleValue()";
        assert result.valStr() == null : "Integer 不应产生 valStr";
        assert !result.skip() : "Integer 不应跳过";
    }

    /**
     * Property 1: 类型映射正确性 — Double
     * Validates: Requirements 1.8
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Double")
    void doubleMapsToValNum(@ForAll double value) {
        Assume.that(Double.isFinite(value));
        var result = VarTypeMapper.map(Datatype.Double, value);
        assert result.valNum() != null : "Double 应产生 valNum";
        assert result.valNum() == value : "valNum 应等于原值";
        assert result.valStr() == null : "Double 不应产生 valStr";
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Float
     * Validates: Requirements 1.8
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Float")
    void floatMapsToValNum(@ForAll float value) {
        Assume.that(Float.isFinite(value));
        var result = VarTypeMapper.map(Datatype.Float, value);
        assert result.valNum() != null : "Float 应产生 valNum";
        assert result.valNum() == ((Number) value).doubleValue() : "valNum 应等于 doubleValue()";
        assert result.valStr() == null;
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Long
     * Validates: Requirements 1.8
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Long")
    void longMapsToValNum(@ForAll long value) {
        var result = VarTypeMapper.map(Datatype.Long, value);
        assert result.valNum() != null : "Long 应产生 valNum";
        assert result.valNum() == ((Number) value).doubleValue();
        assert result.valStr() == null;
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — BigDecimal
     * Validates: Requirements 1.8
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — BigDecimal")
    void bigDecimalMapsToValNum(@ForAll @BigRange(min = "-1E12", max = "1E12") BigDecimal value) {
        var result = VarTypeMapper.map(Datatype.BigDecimal, value);
        assert result.valNum() != null : "BigDecimal 应产生 valNum";
        assert result.valNum() == value.doubleValue();
        assert result.valStr() == null;
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Boolean
     * 随机 boolean，验证 valNum 为 1.0 或 0.0
     * Validates: Requirements 1.9
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Boolean")
    void booleanMapsToValNum(@ForAll boolean value) {
        var result = VarTypeMapper.map(Datatype.Boolean, value);
        double expected = value ? 1.0 : 0.0;
        assert result.valNum() != null : "Boolean 应产生 valNum";
        assert result.valNum() == expected : "true→1.0, false→0.0";
        assert result.valStr() == null : "Boolean 不应产生 valStr";
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — String
     * Validates: Requirements 1.10
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — String")
    void stringMapsToValStr(@ForAll @StringLength(min = 0, max = 200) String value) {
        var result = VarTypeMapper.map(Datatype.String, value);
        assert result.valStr() != null : "String 应产生 valStr";
        assert result.valStr().equals(value) : "valStr 应等于原值";
        assert result.valNum() == null : "String 不应产生 valNum";
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Char
     * Validates: Requirements 1.10
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Char")
    void charMapsToValStr(@ForAll char value) {
        var result = VarTypeMapper.map(Datatype.Char, value);
        assert result.valStr() != null : "Char 应产生 valStr";
        assert result.valStr().equals(String.valueOf(value));
        assert result.valNum() == null;
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Enum（以字符串形式存储）
     * Validates: Requirements 1.10
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Enum")
    void enumMapsToValStr(@ForAll @StringLength(min = 1, max = 50) String value) {
        var result = VarTypeMapper.map(Datatype.Enum, value);
        assert result.valStr() != null : "Enum 应产生 valStr";
        assert result.valStr().equals(value);
        assert result.valNum() == null;
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — Date（验证格式和字段分配）
     * Validates: Requirements 1.12 (skip=false, valNum=null, valStr 非 null)
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — Date")
    void dateMapsToValStr(@ForAll("dates") Date value) {
        var result = VarTypeMapper.map(Datatype.Date, value);
        assert result.valStr() != null : "Date 应产生 valStr";
        assert result.valStr().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
                : "Date valStr 应匹配 yyyy-MM-dd HH:mm:ss 格式";
        assert result.valNum() == null : "Date 不应产生 valNum";
        assert !result.skip();
    }

    /**
     * Property 1: 类型映射正确性 — 复合类型（List/Set/Map/Object）应跳过
     * Validates: Requirements 1.12
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — 复合类型跳过")
    void complexTypesSkip(@ForAll("complexTypes") Datatype type) {
        // 复合类型无论传什么值都应 skip
        var result = VarTypeMapper.map(type, "anything");
        assert result.skip() : type + " 应返回 skip=true";
        assert result.valNum() == null;
        assert result.valStr() == null;
    }

    /**
     * Property 1: 类型映射正确性 — null 值 → missing
     * Validates: Requirements 1.12
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 1: 类型映射正确性 — null 值")
    void nullValueReturnsMissing(@ForAll("nonComplexTypes") Datatype type) {
        var result = VarTypeMapper.map(type, null);
        assert result.valNum() == null : "null 值 valNum 应为 null";
        assert result.valStr() == null : "null 值 valStr 应为 null";
        assert !result.skip() : "null 值不应跳过";
    }

    // ========== Property 2: Date 格式化往返 ==========

    /**
     * Property 2: Date 格式化往返
     * 随机 Date（截断到秒），格式化后再解析应等于原值。
     * Validates: Requirements 1.11
     */
    @Property(tries = 200)
    @Label("Feature: variable-monitoring, Property 2: Date 格式化往返")
    void dateFormatRoundTrip(@ForAll("dates") Date original) throws ParseException {
        // 截断到秒
        long truncatedMs = (original.getTime() / 1000) * 1000;
        Date truncated = new Date(truncatedMs);

        var result = VarTypeMapper.map(Datatype.Date, truncated);
        assert result.valStr() != null;

        // 解析回 Date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date parsed = sdf.parse(result.valStr());

        assert parsed.equals(truncated)
                : "格式化往返失败: 原值=" + truncated.getTime() + ", 解析=" + parsed.getTime();
    }

    // ========== 生成器 ==========

    /** 生成随机 Date，范围 2000-01-01 ~ 2030-12-31 */
    @Provide
    Arbitrary<Date> dates() {
        // 2000-01-01 00:00:00 UTC ~ 2030-12-31 23:59:59 UTC
        long min = 946684800000L;
        long max = 1924991999000L;
        return Arbitraries.longs().between(min, max).map(Date::new);
    }

    /** 复合类型枚举 */
    @Provide
    Arbitrary<Datatype> complexTypes() {
        return Arbitraries.of(Datatype.List, Datatype.Set, Datatype.Map, Datatype.Object);
    }

    /** 非复合类型枚举 */
    @Provide
    Arbitrary<Datatype> nonComplexTypes() {
        return Arbitraries.of(
                Datatype.Integer, Datatype.Double, Datatype.Float, Datatype.Long,
                Datatype.BigDecimal, Datatype.Boolean, Datatype.String, Datatype.Char,
                Datatype.Enum, Datatype.Date
        );
    }
}

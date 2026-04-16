package com.caritasem.ruleuler.server.grayscale.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 条件项，复用 REA Op 枚举操作符。
 * 格式: {"left":"category.field","op":"Equals","right":"VIP"}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionItem {
    private String left;   // category.fieldName
    private String op;     // Op 枚举名: Equals, NotEquals, GreaterThen, In, Contains 等
    private String right;  // 比较值
}

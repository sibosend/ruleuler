package com.caritasem.ruleuler.monitoring;

/**
 * 变量明细行，对应 ClickHouse execution_var_log 表一行。
 * valNum 和 valStr 均为 null 表示 missing（变量值为 null）。
 */
public record VarLogRow(
    String executionId,    // UUID
    String project,
    String packageId,
    String flowId,
    String varCategory,    // 变量类别，如 FlightInfo；失败摘要行为空
    String varName,        // 变量名；失败摘要行为空
    String varType,        // Datatype 枚举名
    Double valNum,         // 数值型值
    String valStr,         // 字符串型值
    String ioType,         // "input" / "output"
    long execMs,           // 执行耗时 ms
    long createdAt         // System.currentTimeMillis()
) {}

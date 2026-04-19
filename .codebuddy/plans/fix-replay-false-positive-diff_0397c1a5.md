---
name: fix-replay-false-positive-diff
overview: 修复回放 diff 误报：回放输出中包含规则未赋值的变量 key（值为 null），导致与原始日志比对时出现虚假的 ADDED
todos:
  - id: fix-replay-output-null-filter
    content: 修改 executeReplay() 过滤 output 中 null 值的 entry
    status: completed
---

## 问题

回放差异详情中 `GateResult.assigned_gate` 显示为 ADDED 状态，但原始值和回放值都为空（null），属于误报。

## 根因

1. ClickHouse 日志只记录非空的 output 变量，`assigned_gate` 为空所以不在日志里
2. `GeneralEntity` 执行后包含变量定义中所有字段（含未赋值的，value=null）
3. `DiffComparator` 发现 original 无此 key 但 replay 有此 key（值为null）→ 错误判定 ADDED

## 修改目标

在 `executeReplay()` 构建 output map 时过滤掉 null 值的 entry，和 ClickHouse 日志记录范围对齐

## 修改方案

在源头 `ReplayService.executeReplay()` 过滤 null 值，而非在 `DiffComparator` 里打补丁。理由：

- ClickHouse 只记录非空值，replay output 应和其对齐
- 源头过滤比下游兼容更干净，不会在 DiffComparator 引入歧义逻辑
- fallback 分支也需要同样过滤

## 修改文件

`ruleuler-server/src/main/java/com/caritasem/ruleuler/server/replay/ReplayService.java`

`executeReplay()` 方法 L417-430，构建 output map 时将 `new LinkedHashMap<>(entity)` 替换为过滤 null 值的逻辑：

- 有 outputCategories 分支（L419-424）：遍历 entity.entrySet()，只放入 value != null 的 entry；过滤后为空的 category 不放入 output
- fallback 分支（L426-429）：同样过滤 null 值
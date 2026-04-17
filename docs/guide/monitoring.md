# 变量监控

## 概述

变量监控模块对规则引擎每次执行的输入/输出变量进行采集、聚合和分析，帮助运维人员发现数据质量问题和规则行为异常。

数据流：规则执行 → ClickHouse 明细写入 → 5 分钟物化视图预聚合 → 每日凌晨 MySQL 日统计聚合。

### 存储架构

| 层级 | 存储 | 说明 |
|------|------|------|
| 明细层 | ClickHouse `execution_var_log` | 每次执行的每个变量一行，TTL 3 个月 |
| 预聚合层 | ClickHouse `execution_var_log_5m` | 物化视图，5 分钟窗口自动聚合 |
| 日统计层 | MySQL `ruleuler_variable_daily_stats` | 每日凌晨 2:00 定时任务聚合 |

### 前置条件

- `application.yml` 中 `monitoring.enabled=true`
- ClickHouse 已部署并初始化（`deploy/clickhouse/init.sql`）
- 用户拥有 `menu:monitoring` 菜单权限

---

## 功能模块

### 1. 实时大盘

路径：监控 → 选择项目和知识包

展示当日与昨日的对比概览：

- 总执行量
- 异常率（缺失值占比）
- 错误率（执行失败占比）

数据来源：ClickHouse 物化视图 `execution_var_log_5m`，按 `today()` / `yesterday()` 分组。

### 2. 变量列表

展示当日所有活跃变量的实时指标：

| 指标 | 说明 |
|------|------|
| sample_count | 采样数 |
| missing_rate | 缺失率 |
| error_rate | 错误率 |
| mean | 均值（数值型） |
| min / max | 极值 |
| alert_flags | 告警标记 |

支持排序方式：异常优先（默认）、缺失率、错误率、采样量。

告警标记由 `AlertChecker` 根据配置阈值自动计算，触发条件：
- 缺失率超过 `missing_rate_max`
- 离群率超过 `outlier_rate_max`
- 偏度绝对值超过 `skewness_abs_max`

### 3. DoD / WoW 对比

变量列表增强模式，展示每个变量相对昨日（Day-over-Day）和上周同日（Week-over-Week）的变化百分比：

- `dod_mean_pct`：均值日环比
- `dod_missing_rate_pct`：缺失率日环比
- `wow_mean_pct`：均值周同比
- `wow_missing_rate_pct`：缺失率周同比

基准数据来源：MySQL `ruleuler_variable_daily_stats` 的昨日和 7 天前记录。

### 4. 变量趋势

路径：变量列表 → 点击变量名

展示单个变量在指定时间范围内的日统计趋势（默认 30 天，最大 3 个月）：

- 均值、标准差、分位数（P25/P50/P75）
- 缺失率、离群率
- 偏度

数据来源：MySQL `ruleuler_variable_daily_stats`。

### 5. 缺失率分时趋势

展示单个变量当日的 5 分钟粒度缺失率走势，支持突增检测：

- 当窗口缺失率相比前一窗口增幅超过 `missing_rate_spike_delta` 时标记为 spike

### 6. PSI 分布稳定性

PSI（Population Stability Index）衡量变量分布是否发生漂移：

- 基准分布：过去 30 天 ClickHouse 原始 `val_num`
- 当前分布：当日原始 `val_num`
- 分 10 个等频 bin 计算

| PSI 值 | 含义 |
|--------|------|
| < 0.1 | 分布稳定 |
| 0.1 ~ 0.2 | 轻微漂移（warning） |
| > 0.2 | 显著漂移（alert） |

阈值可在告警配置中调整（`psi_warning` / `psi_alert`）。

仅对数值型变量计算，类别型变量跳过。

### 7. 枚举漂移检测

针对类别型变量（String / Boolean），检测 top value 及其频率占比是否发生变化：

- 基准：前 7 天 MySQL daily_stats 的 `top_value` 和 `top_freq_ratio` 平均值
- 当前：当日 ClickHouse 原始数据统计
- 漂移判定：top value 变化，或频率占比变化超过 `enum_drift_threshold`

### 8. 周期对比

选择两个时间段，对比指定变量的统计指标差异：

- 支持多变量同时对比
- 每个周期从 ClickHouse 实时聚合：均值、标准差、分位数、缺失率、离群率
- 时间范围限制：单周期不超过 3 个月

### 9. 版本对比

按规则包版本号对比发版前后的变量指标变化：

- 从 MySQL `ruleuler_rule_file_version` 获取版本发布时间
- Period A：版本 A 发布 → 版本 B 发布
- Period B：版本 B 发布 → 当前
- 对比维度：采样量、缺失率、错误率、均值

### 10. 执行记录与追踪

分页查询历史执行记录，按 `execution_id` 聚合：

- 执行时间、耗时、变量数、状态（success / failed）
- 支持时间范围筛选
- 点击可查看单次执行的全部变量明细（输入/输出值）

**执行追踪（Execution Trace）**：
在单次执行的详情页中，系统会展示完整的**系统执行追踪路径**：
- 规则命中节点、流转条件（Condition）
- 变量的实时赋值（VarAssign）与节点耗时
- 绕过 debug 设置的特殊记录机制保证日志不丢失，详细追溯每一笔引擎推断动作，底层落盘至 ClickHouse `execution_trace_log` 表中。

### 11. 异常记录下钻

从变量列表点击告警标记，查看具体异常执行记录：

- 支持异常类型筛选：missing（缺失）、error（错误）、outlier（离群）
- 展示 execution_id、时间、实际值
- 分页查询

### 12. 分时走势

展示当日与前一天的 5 分钟粒度执行量对比：

- 采样量、缺失数、错误数
- 异常率、错误率
- 双线对比（target / previous）

### 13. 近 N 日趋势

展示近 N 天（默认 14 天）的每日执行量走势：

- 总执行量、错误执行量、缺失执行量
- 错误率、异常率

---

## 告警配置

路径：`GET/PUT /api/monitoring/alert-config`

| 参数 | 默认值 | 说明 |
|------|--------|------|
| missing_rate_max | 0.05 | 缺失率告警阈值 |
| missing_rate_spike_delta | 0.1 | 缺失率突增判定阈值 |
| outlier_rate_max | 0.03 | 离群率告警阈值 |
| skewness_abs_max | 2.0 | 偏度绝对值告警阈值 |
| psi_warning | 0.1 | PSI 警告阈值 |
| psi_alert | 0.2 | PSI 告警阈值 |
| enum_drift_threshold | 0.15 | 枚举漂移判定阈值 |

配置存储在 MySQL `ruleuler_monitoring_alert_config` 表，单行设计，内存缓存 5 分钟刷新。

---

## 手动聚合

日统计通常由定时任务（每日凌晨 2:00）自动执行。如需手动触发：

```bash
# 聚合昨天
curl -X POST http://localhost:16009/api/monitoring/aggregate

# 聚合指定日期
curl -X POST http://localhost:16009/api/monitoring/aggregate?date=2026-04-05
```

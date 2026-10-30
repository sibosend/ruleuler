# Variable Monitoring

## Overview

The Variable Monitoring module collects, aggregates, and analyzes input and output variables from every rule engine execution, helping operators detect data quality issues and abnormal rule behavior.

Data pipeline: Rule Execution → ClickHouse raw logs → 5-minute materialized view pre-aggregation → Daily scheduled aggregation into MySQL at 02:00 AM.

### Storage Architecture

| Tier | Storage | Description |
|------|---------|-------------|
| Raw Layer | ClickHouse `execution_var_log` | One row per variable per execution, 3-month TTL |
| Pre-aggregation Layer | ClickHouse `execution_var_log_5m` | Materialized view with automatic 5-minute window aggregation |
| Daily Stats Layer | MySQL `ruleuler_variable_daily_stats` | Aggregated daily at 02:00 AM via scheduled job |

### Prerequisites

- `monitoring.enabled=true` in `application.yml`
- ClickHouse deployed and initialized (`deploy/clickhouse/init.sql`)
- User granted the `menu:monitoring` permission

---

## Feature Modules

### 1. Real-Time Overview

Path: **Monitoring → Select Project and Knowledge Package**

Displays today vs. yesterday summary metrics:

- Total executions
- Anomaly rate (missing value ratio)
- Error rate (failed execution ratio)

Data source: ClickHouse materialized view `execution_var_log_5m`, grouped by `today()` / `yesterday()`.

### 2. Variable List

Displays real-time metrics for all active variables today:

| Metric | Description |
|--------|-------------|
| `sample_count` | Number of samples |
| `missing_rate` | Missing value ratio |
| `error_rate` | Execution error ratio |
| `mean` | Mean value (numeric variables) |
| `min` / `max` | Minimum / Maximum values |
| `alert_flags` | Active alert flags |

Supported sort orders: Anomaly first (default), Missing rate, Error rate, Sample count.

Alert flags are computed by `AlertChecker` against configured thresholds:
- Missing rate exceeds `missing_rate_max`
- Outlier rate exceeds `outlier_rate_max`
- Absolute skewness exceeds `skewness_abs_max`

### 3. DoD / WoW Comparison

Enhanced variable list mode showing Day-over-Day (vs. yesterday) and Week-over-Week (vs. 7 days ago) percentage changes:

- `dod_mean_pct`: Day-over-Day mean change
- `dod_missing_rate_pct`: Day-over-Day missing rate change
- `wow_mean_pct`: Week-over-Week mean change
- `wow_missing_rate_pct`: Week-over-Week missing rate change

Baseline source: MySQL `ruleuler_variable_daily_stats` records from 1 day and 7 days ago.

### 4. Variable Trend

Path: **Variable List → Click Variable Name**

Displays daily statistical trends for a single variable over a selected date range (default 30 days, up to 3 months):

- Mean, standard deviation, quantiles (P25 / P50 / P75)
- Missing rate, outlier rate
- Skewness

Data source: MySQL `ruleuler_variable_daily_stats`.

### 5. Intraday Missing Rate Spike Detection

Displays 5-minute granularity missing rate trends for today, flagging sudden spikes when a window's missing rate increases by more than `missing_rate_spike_delta` over the previous window.

### 6. PSI Distribution Stability

PSI (Population Stability Index) measures distribution drift for numeric variables:

- Baseline distribution: Raw `val_num` over the past 30 days in ClickHouse
- Current distribution: Today's raw `val_num`
- Computed across 10 equal-frequency bins

| PSI Value | Interpretation |
|-----------|----------------|
| `< 0.1` | Stable distribution |
| `0.1 ~ 0.2` | Moderate drift (`warning`) |
| `> 0.2` | Significant drift (`alert`) |

Thresholds are configurable via `psi_warning` and `psi_alert`. Calculated for numeric variables only.

### 7. Enum Drift Detection

For categorical variables (`String` / `Boolean`), detects changes in the top value or its frequency share:

- Baseline: 7-day average of `top_value` and `top_freq_ratio` from MySQL `ruleuler_variable_daily_stats`
- Current: Today's raw distribution from ClickHouse
- Drift triggered when `top_value` changes or frequency share shifts by more than `enum_drift_threshold`

### 8. Period Comparison

Compare statistical metrics of selected variables across two custom time ranges (up to 3 months per period), aggregated in real time from ClickHouse.

### 9. Version Comparison

Compare variable metrics before and after a knowledge package release based on publish timestamps in `ruleuler_rule_file_version`:

- Period A: Release A → Release B
- Period B: Release B → Present
- Dimensions: Sample count, missing rate, error rate, mean

### 10. Execution Logs & Execution Trace

Paginated query of historical executions grouped by `execution_id`:

- Execution timestamp, duration, variable count, status (`success` / `failed`)
- Click an execution to inspect all input/output variable values

**Execution Trace**:
In the execution detail view, the system displays the full execution trace path stored in ClickHouse `execution_trace_log`:
- Matched rule nodes and evaluated conditions
- Real-time variable assignments (`VarAssign`) and per-node execution time

### 11. Anomaly Drill-Down

Click an alert tag in the variable list to inspect specific anomalous execution records filtered by `missing`, `error`, or `outlier`.

### 12. Intraday Volume Trend

5-minute granularity comparison of today vs. yesterday execution volume, missing count, error count, anomaly rate, and error rate.

### 13. Last N Days Trend

Daily execution trend over the past N days (default 14 days), including total executions, error executions, missing executions, error rate, and anomaly rate.

---

## Alert Configuration

Endpoint: `GET/PUT /api/monitoring/alert-config`

| Parameter | Default | Description |
|-----------|---------|-------------|
| `missing_rate_max` | `0.05` | Missing rate alert threshold |
| `missing_rate_spike_delta` | `0.1` | Intraday missing rate spike threshold |
| `outlier_rate_max` | `0.03` | Outlier rate alert threshold |
| `skewness_abs_max` | `2.0` | Absolute skewness alert threshold |
| `psi_warning` | `0.1` | PSI warning threshold |
| `psi_alert` | `0.2` | PSI alert threshold |
| `enum_drift_threshold` | `0.15` | Categorical frequency drift threshold |

Stored in the single-row MySQL table `ruleuler_monitoring_alert_config` with a 5-minute in-memory cache.

---

## Manual Aggregation

Daily stats are aggregated automatically at 02:00 AM. To trigger aggregation manually:

```bash
# Aggregate yesterday's stats
curl -X POST http://localhost:16009/api/monitoring/aggregate

# Aggregate a specific date
curl -X POST http://localhost:16009/api/monitoring/aggregate?date=2026-04-05
```


-- ClickHouse 变量明细表
-- ReplacingMergeTree 以 created_at 为版本列，按 ORDER BY 键去重
-- 按月分区，TTL 3 个月自动清理
CREATE TABLE IF NOT EXISTS execution_var_log (
    execution_id      String,
    project           LowCardinality(String),
    package_id        LowCardinality(String),
    flow_id           String,
    var_category      LowCardinality(String),
    var_name          LowCardinality(String),
    var_type          LowCardinality(String),
    val_num           Nullable(Float64),
    val_str           Nullable(String),
    io_type           LowCardinality(String),
    exec_ms           UInt32,
    created_at        DateTime64(3),
    grayscale_bucket  LowCardinality(String) DEFAULT 'BASE'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (execution_id, var_category, var_name, io_type)
TTL toDateTime(created_at) + INTERVAL 3 MONTH
SETTINGS index_granularity = 8192;

-- ============================================================
-- 物化视图：5分钟窗口预聚合
-- ============================================================

CREATE TABLE IF NOT EXISTS execution_var_log_5m (
    window_start     DateTime,
    project          LowCardinality(String),
    package_id       LowCardinality(String),
    var_category     LowCardinality(String),
    var_name         LowCardinality(String),
    var_type         LowCardinality(String),
    io_type          LowCardinality(String),
    sample_count     UInt64,
    missing_count    UInt64,
    sum_val_num      Float64,
    sum_sq_val_num   Float64,
    min_val_num      SimpleAggregateFunction(min, Nullable(Float64)),
    max_val_num      SimpleAggregateFunction(max, Nullable(Float64)),
    error_count      UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(window_start)
ORDER BY (project, package_id, var_category, var_name, io_type, window_start)
TTL window_start + INTERVAL 3 MONTH
SETTINGS index_granularity = 8192;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_execution_var_log_5m
TO execution_var_log_5m
AS SELECT
    toStartOfFiveMinutes(created_at) AS window_start,
    project, package_id, var_category, var_name, var_type, io_type,
    count()                                       AS sample_count,
    countIf(val_num IS NULL AND val_str IS NULL)   AS missing_count,
    sumIf(val_num, val_num IS NOT NULL)            AS sum_val_num,
    sumIf(val_num * val_num, val_num IS NOT NULL)  AS sum_sq_val_num,
    min(val_num)                                   AS min_val_num,
    max(val_num)                                   AS max_val_num,
    countIf(var_name = '')                         AS error_count
FROM execution_var_log
GROUP BY window_start, project, package_id, var_category, var_name, var_type, io_type;

-- ============================================================
-- 执行追踪表：记录单次执行的推理路径（条件通过/失败、规则命中、变量赋值）
-- ============================================================

CREATE TABLE IF NOT EXISTS execution_trace_log (
    execution_id    String,
    seq             UInt32,
    msg_type        LowCardinality(String),
    msg_text        String,
    parsed_name     Nullable(String),
    pass_fail       Nullable(LowCardinality(String)),
    project         LowCardinality(String),
    package_id      LowCardinality(String),
    flow_id         String,
    created_at      DateTime64(3)
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (execution_id, seq)
TTL toDateTime(created_at) + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

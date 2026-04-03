-- ClickHouse 变量明细表
-- ReplacingMergeTree 以 created_at 为版本列，按 ORDER BY 键去重
-- 按月分区，TTL 3 个月自动清理
CREATE TABLE IF NOT EXISTS execution_var_log (
    execution_id   String,
    project        LowCardinality(String),
    package_id     LowCardinality(String),
    flow_id        String,
    var_category   LowCardinality(String),
    var_name       LowCardinality(String),
    var_type       LowCardinality(String),
    val_num        Nullable(Float64),
    val_str        Nullable(String),
    io_type        LowCardinality(String),
    exec_ms        UInt32,
    created_at     DateTime64(3)
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (execution_id, var_category, var_name, io_type)
TTL toDateTime(created_at) + INTERVAL 3 MONTH
SETTINGS index_granularity = 8192;

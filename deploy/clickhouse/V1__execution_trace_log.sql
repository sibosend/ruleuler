-- 执行追踪表：记录单次执行的推理路径（条件通过/失败、规则命中、变量赋值）
-- 已有部署执行此 migration；全新部署见 init.sql

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

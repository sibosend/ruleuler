-- 为执行变量日志增加灰度桶标记（BASE/GRAY）
ALTER TABLE execution_var_log ADD COLUMN IF NOT EXISTS grayscale_bucket LowCardinality(String) DEFAULT 'BASE';

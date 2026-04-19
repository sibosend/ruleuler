-- ============================================================
-- 流量回放表
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_replay_task` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project` VARCHAR(100) NOT NULL,
    `package_id` VARCHAR(100) NOT NULL,
    `flow_id` VARCHAR(200),
    `traffic_query` JSON NOT NULL COMMENT '查询条件',
    `sample_strategy` VARCHAR(20) NOT NULL COMMENT 'all/random/uniform',
    `sample_size` INT NOT NULL DEFAULT 10000,
    `missing_var_strategy` VARCHAR(20) NOT NULL DEFAULT 'null' COMMENT 'skip/null/segment',
    `total_count` INT NOT NULL DEFAULT 0,
    `executed_count` INT NOT NULL DEFAULT 0,
    `match_count` INT NOT NULL DEFAULT 0,
    `mismatch_count` INT NOT NULL DEFAULT 0,
    `error_count` INT NOT NULL DEFAULT 0,
    `incomplete_count` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/completed/failed',
    `tolerance_config` JSON COMMENT '容差配置',
    `started_at` BIGINT,
    `finished_at` BIGINT,
    `created_at` BIGINT NOT NULL,
    INDEX `idx_project_pkg` (`project`, `package_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_replay_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `task_id` BIGINT NOT NULL,
    `original_execution_id` VARCHAR(100) NOT NULL,
    `replay_input` JSON COMMENT '重组后的请求体',
    `original_output` JSON COMMENT '历史输出',
    `replay_output` JSON COMMENT '回放输出',
    `diff_result` JSON COMMENT '差异比对结果',
    `missing_categories` JSON COMMENT '缺失的变量类别',
    `missing_variables` JSON COMMENT '缺失的具体变量',
    `filled_variables` JSON COMMENT 'segment 策略填充的变量',
    `completeness_status` VARCHAR(20) DEFAULT 'COMPLETE' COMMENT 'COMPLETE/INCOMPLETE',
    `exec_ms` INT,
    `original_exec_ms` INT,
    `status` VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT 'success/error',
    `error_message` TEXT,
    `created_at` BIGINT NOT NULL,
    INDEX `idx_task` (`task_id`),
    INDEX `idx_task_status` (`task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

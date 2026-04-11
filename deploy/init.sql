-- ============================================================
-- RulEuler 完整数据库初始化脚本
-- 数据库: ruleuler_data
-- 包含: JCR 存储表 + DB 存储表 + RBAC 权限表 + 自动测试表
--        + 变量监控表 + 监控告警配置表 + 发布审批表
-- 生成方式: mysqldump --no-data + 初始数据补充
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS `ruleuler_data` DEFAULT CHARACTER SET utf8mb4;

-- ============================================================
-- 1. JCR Jackrabbit 存储表（jcr 模式使用）
-- ============================================================

CREATE TABLE IF NOT EXISTS `JOURNAL_GLOBAL_REVISION` (
  `REVISION_ID` bigint NOT NULL,
  UNIQUE KEY `JOURNAL_GLOBAL_REVISION_IDX` (`REVISION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `JOURNAL_JOURNAL` (
  `REVISION_ID` bigint NOT NULL,
  `JOURNAL_ID` varchar(255) DEFAULT NULL,
  `PRODUCER_ID` varchar(255) DEFAULT NULL,
  `REVISION_DATA` longblob,
  UNIQUE KEY `JOURNAL_JOURNAL_IDX` (`REVISION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `JOURNAL_LOCAL_REVISIONS` (
  `JOURNAL_ID` varchar(255) NOT NULL,
  `REVISION_ID` bigint NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_DEFAULT_FSENTRY` (
  `FSENTRY_PATH` text NOT NULL,
  `FSENTRY_NAME` varchar(255) NOT NULL,
  `FSENTRY_DATA` longblob,
  `FSENTRY_LASTMOD` bigint NOT NULL,
  `FSENTRY_LENGTH` bigint NOT NULL,
  UNIQUE KEY `REPO_DEFAULT_FSENTRY_IDX` (`FSENTRY_PATH`(245),`FSENTRY_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `repo_ds_DATASTORE` (
  `ID` varchar(255) NOT NULL,
  `LENGTH` bigint DEFAULT NULL,
  `LAST_MODIFIED` bigint DEFAULT NULL,
  `DATA` longblob,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_FSENTRY` (
  `FSENTRY_PATH` text NOT NULL,
  `FSENTRY_NAME` varchar(255) NOT NULL,
  `FSENTRY_DATA` longblob,
  `FSENTRY_LASTMOD` bigint NOT NULL,
  `FSENTRY_LENGTH` bigint NOT NULL,
  UNIQUE KEY `REPO_FSENTRY_IDX` (`FSENTRY_PATH`(245),`FSENTRY_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `REPO_FSVER_FSENTRY` (
  `FSENTRY_PATH` text NOT NULL,
  `FSENTRY_NAME` varchar(255) NOT NULL,
  `FSENTRY_DATA` longblob,
  `FSENTRY_LASTMOD` bigint NOT NULL,
  `FSENTRY_LENGTH` bigint NOT NULL,
  UNIQUE KEY `REPO_FSVER_FSENTRY_IDX` (`FSENTRY_PATH`(245),`FSENTRY_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

CREATE TABLE IF NOT EXISTS `REPO_PM_DEFAULT_BINVAL` (
  `BINVAL_ID` varchar(64) NOT NULL,
  `BINVAL_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_PM_DEFAULT_BINVAL_IDX` (`BINVAL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_PM_DEFAULT_BUNDLE` (
  `NODE_ID` varbinary(16) NOT NULL,
  `BUNDLE_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_PM_DEFAULT_BUNDLE_IDX` (`NODE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_PM_DEFAULT_NAMES` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `NAME` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_PM_DEFAULT_REFS` (
  `NODE_ID` varbinary(16) NOT NULL,
  `REFS_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_PM_DEFAULT_REFS_IDX` (`NODE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_VER_BINVAL` (
  `BINVAL_ID` varchar(64) NOT NULL,
  `BINVAL_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_VER_BINVAL_IDX` (`BINVAL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_VER_BUNDLE` (
  `NODE_ID` varbinary(16) NOT NULL,
  `BUNDLE_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_VER_BUNDLE_IDX` (`NODE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_VER_NAMES` (
  `ID` int NOT NULL AUTO_INCREMENT,
  `NAME` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_bin NOT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `REPO_VER_REFS` (
  `NODE_ID` varbinary(16) NOT NULL,
  `REFS_DATA` longblob NOT NULL,
  UNIQUE KEY `REPO_VER_REFS_IDX` (`NODE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. DB 存储表（db 模式使用）
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_project_storage` (
  `project_name` varchar(255) NOT NULL,
  `storage_type` varchar(10) NOT NULL COMMENT 'jcr | db',
  `created_at` bigint NOT NULL COMMENT '创建时间，毫秒时间戳',
  PRIMARY KEY (`project_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_rule_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(255) NOT NULL,
  `path` varchar(500) NOT NULL COMMENT '完整路径 /project/dir/file.rl',
  `name` varchar(255) NOT NULL,
  `is_dir` tinyint(1) DEFAULT 0,
  `folder_type` varchar(50) DEFAULT NULL COMMENT '文件夹所属分类: lib/ruleLib/decisionTableLib/...',
  `content` longtext,
  `create_user` varchar(100) DEFAULT NULL,
  `update_user` varchar(100) DEFAULT NULL,
  `company_id` varchar(100) DEFAULT NULL,
  `created_at` bigint NOT NULL COMMENT '毫秒时间戳',
  `updated_at` bigint NOT NULL COMMENT '毫秒时间戳',
  `lock_user` varchar(100) DEFAULT NULL,
  `lock_time` bigint DEFAULT NULL COMMENT '锁定时间，毫秒时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_rule_file_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL,
  `version` int NOT NULL,
  `version_name` varchar(50) NOT NULL,
  `content` longtext NOT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `create_user` varchar(100) DEFAULT NULL,
  `created_at` bigint NOT NULL COMMENT '毫秒时间戳',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_version` (`file_id`,`version`),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. RBAC 权限表
-- ============================================================

CREATE TABLE IF NOT EXISTS `rbac_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password_hash` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `built_in` tinyint NOT NULL DEFAULT 0 COMMENT '1=内置不可删',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rbac_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `description` varchar(256) DEFAULT '',
  `built_in` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rbac_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(128) NOT NULL,
  `name` varchar(64) NOT NULL,
  `type` enum('menu','api') NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`),
  CONSTRAINT `fk_perm_parent` FOREIGN KEY (`parent_id`) REFERENCES `rbac_permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rbac_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `rbac_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `rbac_role` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rbac_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `rbac_role` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rp_perm` FOREIGN KEY (`permission_id`) REFERENCES `rbac_permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- RBAC 初始数据
-- 默认密码: asdfg@1234  BCrypt cost=10
INSERT IGNORE INTO `rbac_user` (`username`, `password_hash`, `status`, `built_in`)
VALUES ('admin', '$2a$10$XIqDTlizJ.u7yeyMVR0NOO4cyrgjtEYahM/JPUHfJ135OTheANy72', 1, 1);

INSERT IGNORE INTO `rbac_role` (`name`, `description`, `built_in`)
VALUES ('admin', '超级管理员，拥有所有权限', 1);

INSERT IGNORE INTO `rbac_user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT IGNORE INTO `rbac_permission` (`id`, `permission_code`, `name`, `type`, `parent_id`, `sort_order`) VALUES
(1, 'menu:dashboard',      '仪表盘',     'menu', NULL, 1),
(2, 'menu:projects',       '项目管理',   'menu', NULL, 2),
(3, 'menu:console',        '规则编辑器', 'menu', NULL, 3),
(4, 'menu:system',         '系统设置',   'menu', NULL, 4),
(5, 'menu:system:users',   '用户管理',   'menu', 4,    1),
(6, 'menu:system:roles',   '角色管理',   'menu', 4,    2),
(7,  'api:GET:/api/rbac/users',             '查看用户列表',   'api', NULL, 10),
(8,  'api:POST:/api/rbac/users',            '创建用户',       'api', NULL, 11),
(9,  'api:PUT:/api/rbac/users',             '编辑用户',       'api', NULL, 12),
(10, 'api:DELETE:/api/rbac/users',          '删除用户',       'api', NULL, 13),
(11, 'api:PUT:/api/rbac/users/roles',       '分配用户角色',   'api', NULL, 14),
(12, 'api:GET:/api/rbac/roles',             '查看角色列表',   'api', NULL, 20),
(13, 'api:POST:/api/rbac/roles',            '创建角色',       'api', NULL, 21),
(14, 'api:PUT:/api/rbac/roles',             '编辑角色',       'api', NULL, 22),
(15, 'api:DELETE:/api/rbac/roles',          '删除角色',       'api', NULL, 23),
(16, 'api:PUT:/api/rbac/roles/permissions', '分配角色权限',   'api', NULL, 24),
(17, 'api:GET:/api/rbac/roles/permissions', '查看权限列表',   'api', NULL, 30),
(20, 'menu:monitoring',                     '变量监控',       'menu', NULL, 5),
(21, 'api:GET:/api/monitoring/*',           '监控数据查询',   'api', NULL, 40),
(30, 'menu:approvals',                      '审批管理',       'menu', NULL, 6),
(31, 'pack:publish:submit',                 '提交发布审批',   'api',  NULL, 50),
(32, 'pack:publish:approve',                '审批发布',       'api',  NULL, 51);

INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `rbac_permission`;

-- ============================================================
-- 4. 自动测试表
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_test_case_pack` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(100) NOT NULL,
  `package_id` varchar(100) NOT NULL,
  `pack_name` varchar(200) NOT NULL COMMENT '用例包名称',
  `source_type` varchar(20) NOT NULL COMMENT 'auto / manual',
  `total_cases` int NOT NULL DEFAULT 0,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pkg` (`project`,`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_test_case` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pack_id` bigint NOT NULL,
  `project` varchar(100) NOT NULL,
  `package_id` varchar(100) NOT NULL,
  `flow_file` varchar(500) NOT NULL,
  `case_name` varchar(200) NOT NULL,
  `path_description` varchar(2000) DEFAULT NULL,
  `input_data` json NOT NULL,
  `expected_type` varchar(20) NOT NULL COMMENT 'HIT or MISS',
  `flipped_condition` varchar(500) DEFAULT NULL COMMENT '被翻转的条件描述，MISS 类型时填写',
  `test_purpose` varchar(500) DEFAULT NULL,
  `created_at` bigint NOT NULL,
  `updated_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pack` (`pack_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_test_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(100) NOT NULL,
  `package_id` varchar(100) NOT NULL,
  `total_cases` int NOT NULL DEFAULT 0,
  `passed_cases` int NOT NULL DEFAULT 0,
  `failed_cases` int NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'running' COMMENT 'running/completed/failed',
  `started_at` bigint NOT NULL,
  `finished_at` bigint DEFAULT NULL,
  `pack_id` bigint NOT NULL DEFAULT 0 COMMENT '关联的测试用例包',
  `run_type` varchar(20) DEFAULT 'smoke' COMMENT 'smoke / regression',
  `baseline_run_id` bigint DEFAULT NULL COMMENT '回归基准 run',
  `executed_cases` int NOT NULL DEFAULT 0 COMMENT '已执行用例数',
  PRIMARY KEY (`id`),
  KEY `idx_package` (`project`,`package_id`),
  KEY `idx_pack` (`pack_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_test_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `case_id` bigint NOT NULL,
  `passed` tinyint(1) NOT NULL DEFAULT 0,
  `actual_output` json DEFAULT NULL,
  `execution_time_ms` bigint DEFAULT NULL,
  `error_message` text,
  `created_at` bigint NOT NULL,
  `baseline_output` json DEFAULT NULL COMMENT 'baseline 的 actual_output',
  `diff_status` varchar(20) DEFAULT NULL COMMENT 'BASELINE / SAME / CHANGED',
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`),
  KEY `idx_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_test_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `conflict_type` varchar(20) NOT NULL COMMENT 'OVERLAP / INCOMPLETE / OVERRIDE',
  `severity` varchar(10) NOT NULL COMMENT 'ERROR / WARNING',
  `rule_file` varchar(500) NOT NULL,
  `location` varchar(500) DEFAULT NULL,
  `description` text,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_test_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `variable_name` varchar(100) NOT NULL,
  `variable_type` varchar(20) NOT NULL COMMENT 'INPUT / OUTPUT',
  `segment_label` varchar(200) NOT NULL,
  `case_count` int NOT NULL DEFAULT 0,
  `percentage` decimal(5,2) NOT NULL DEFAULT 0.00,
  `baseline_count` int DEFAULT NULL,
  `baseline_percentage` decimal(5,2) DEFAULT NULL,
  `change_pct` decimal(5,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 变量监控表
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_variable_daily_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(255) NOT NULL,
  `package_id` varchar(255) NOT NULL,
  `var_category` varchar(255) NOT NULL,
  `var_name` varchar(255) NOT NULL,
  `var_type` varchar(50) NOT NULL,
  `io_type` varchar(10) NOT NULL COMMENT 'input / output',
  `stat_date` date NOT NULL,
  `sample_count` int NOT NULL DEFAULT 0,
  `missing_rate` double DEFAULT NULL,
  `mean` double DEFAULT NULL,
  `std` double DEFAULT NULL,
  `min_val` double DEFAULT NULL,
  `p25` double DEFAULT NULL,
  `p50` double DEFAULT NULL,
  `p75` double DEFAULT NULL,
  `max_val` double DEFAULT NULL,
  `skewness` double DEFAULT NULL,
  `outlier_count` int DEFAULT NULL,
  `outlier_rate` double DEFAULT NULL,
  `distinct_count` int DEFAULT NULL,
  `top_value` varchar(500) DEFAULT NULL,
  `top_freq_ratio` double DEFAULT NULL,
  `alert_flags` varchar(255) DEFAULT NULL COMMENT '逗号分隔的告警类型',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_var_date` (`project`(80),`package_id`(80),`var_category`(80),`var_name`(80),`io_type`,`stat_date`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_project_pkg` (`project`(80),`package_id`(80))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 监控告警配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_monitoring_alert_config` (
  `id` int NOT NULL DEFAULT 1,
  `missing_rate_max` double NOT NULL DEFAULT 0.05,
  `missing_rate_spike_delta` double NOT NULL DEFAULT 0.1,
  `outlier_rate_max` double NOT NULL DEFAULT 0.03,
  `skewness_abs_max` double NOT NULL DEFAULT 2.0,
  `psi_warning` double NOT NULL DEFAULT 0.1,
  `psi_alert` double NOT NULL DEFAULT 0.2,
  `enum_drift_threshold` double NOT NULL DEFAULT 0.15,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_single_row` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `ruleuler_monitoring_alert_config`
  (`id`, `missing_rate_max`, `missing_rate_spike_delta`, `outlier_rate_max`,
   `skewness_abs_max`, `psi_warning`, `psi_alert`, `enum_drift_threshold`)
VALUES (1, 0.05, 0.1, 0.03, 2.0, 0.1, 0.2, 0.15);

-- ============================================================
-- 7. 发布审批表
-- ============================================================

CREATE TABLE IF NOT EXISTS `ruleuler_publish_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(255) NOT NULL,
  `package_id` varchar(255) NOT NULL COMMENT '知识包 ID',
  `package_name` varchar(255) DEFAULT NULL COMMENT '知识包名称',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'TESTING/PENDING/APPROVED/REJECTED/PUBLISH_FAILED/PUBLISHED',
  `submitter` varchar(100) NOT NULL COMMENT '提交人',
  `approver` varchar(100) DEFAULT NULL COMMENT '审批人',
  `comment` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `fail_reason` text DEFAULT NULL COMMENT '发布失败原因',
  `submitted_at` bigint NOT NULL COMMENT '提交时间（毫秒时间戳）',
  `approved_at` bigint DEFAULT NULL COMMENT '审批时间（毫秒时间戳）',
  `test_run_id` bigint DEFAULT NULL COMMENT '关联自动测试运行 ID',
  `description` varchar(1000) DEFAULT NULL COMMENT '变更说明',
  `publisher` varchar(100) DEFAULT NULL,
  `published_at` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_project_pkg` (`project`,`package_id`),
  KEY `idx_status` (`status`),
  KEY `idx_submitter` (`submitter`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_publish_approval_diff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_id` bigint NOT NULL,
  `component_path` varchar(500) NOT NULL COMMENT '决策组件路径',
  `component_name` varchar(255) NOT NULL COMMENT '决策组件名称',
  `component_type` varchar(50) NOT NULL COMMENT '组件类型',
  `change_type` varchar(20) NOT NULL COMMENT 'ADDED/MODIFIED/DELETED',
  `prev_version` varchar(50) DEFAULT NULL COMMENT '上次发布版本号',
  `curr_version` varchar(50) DEFAULT NULL COMMENT '本次版本引用',
  `details` text DEFAULT NULL COMMENT '规则级变动详情 JSON',
  PRIMARY KEY (`id`),
  KEY `idx_approval_id` (`approval_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_publish_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(255) NOT NULL,
  `package_id` varchar(255) NOT NULL,
  `approval_id` bigint DEFAULT NULL COMMENT '关联审批单 ID',
  `snapshot_data` json NOT NULL COMMENT '{path: xmlContent} 映射',
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_project_pkg` (`project`,`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

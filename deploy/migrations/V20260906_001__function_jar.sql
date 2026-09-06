-- 自定义函数 jar 存储 + 发布依赖 + client 版本上报 + RBAC

CREATE TABLE IF NOT EXISTS `ruleuler_function_jar` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `company_id` VARCHAR(64) NOT NULL DEFAULT '',
    `project` VARCHAR(100) NOT NULL,
    `function_package` VARCHAR(100) NOT NULL,
    `version` VARCHAR(64) NOT NULL,
    `checksum` VARCHAR(64) NOT NULL,
    `blob` LONGBLOB NOT NULL,
    `uploaded_by` VARCHAR(100) NOT NULL,
    `uploaded_at` BIGINT NOT NULL,
    UNIQUE KEY `uk_func_jar` (`company_id`, `project`, `function_package`, `version`),
    KEY `idx_func_jar_proj` (`project`, `function_package`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_function_deps` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `project` VARCHAR(100) NOT NULL,
    `package_id` VARCHAR(100) NOT NULL,
    `function_package` VARCHAR(100) NOT NULL,
    `version` VARCHAR(64) NOT NULL,
    `checksum` VARCHAR(64) NOT NULL,
    UNIQUE KEY `uk_func_deps` (`project`, `package_id`, `function_package`),
    KEY `idx_func_deps_pkg` (`project`, `package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ruleuler_function_client_status` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `client_host` VARCHAR(200) NOT NULL,
    `package_id` VARCHAR(200) NOT NULL,
    `function_package` VARCHAR(100) NOT NULL,
    `expected_version` VARCHAR(64) DEFAULT NULL,
    `actual_version` VARCHAR(64) DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL,
    `reported_at` BIGINT NOT NULL,
    UNIQUE KEY `uk_func_status` (`client_host`, `package_id`, `function_package`),
    KEY `idx_func_status_pkg` (`package_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `rbac_permission` (`id`, `permission_code`, `name`, `type`, `parent_id`, `sort_order`) VALUES
(50, 'project:function:upload', '上传自定义函数', 'api',  NULL, 70),
(51, 'project:function:view',   '查看自定义函数', 'api',  NULL, 71);

INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission_id`) VALUES
(1, 50),
(1, 51);

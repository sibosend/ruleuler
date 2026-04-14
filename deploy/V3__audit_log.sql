-- V3: 审计日志
CREATE TABLE IF NOT EXISTS `ruleuler_audit_log` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `action`      VARCHAR(30) NOT NULL,
  `target_type` VARCHAR(20) NOT NULL,
  `target_id`   BIGINT DEFAULT NULL,
  `target_path` VARCHAR(500) DEFAULT NULL,
  `project`     VARCHAR(100) DEFAULT NULL,
  `operator`    VARCHAR(100) NOT NULL,
  `detail`      JSON DEFAULT NULL,
  `ip`          VARCHAR(50) DEFAULT NULL,
  `created_at`  BIGINT NOT NULL,
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_project_time` (`project`, `created_at`),
  INDEX `idx_operator_time` (`operator`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审计日志菜单权限
INSERT IGNORE INTO `rbac_permission` (`id`, `permission_code`, `name`, `type`, `parent_id`, `sort_order`)
VALUES (33, 'menu:system:audit', '审计日志', 'menu', 4, 3);
INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission_id`) VALUES (1, 33);

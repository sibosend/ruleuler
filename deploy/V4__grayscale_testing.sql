-- V4: 系统版本号 + 灰度发布/A/B 测试

-- 审批表加版本号
ALTER TABLE ruleuler_publish_approval ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '系统版本号' AFTER description;
CREATE INDEX idx_version ON ruleuler_publish_approval(project, package_id, version);

-- 灰度路由规则
CREATE TABLE IF NOT EXISTS ruleuler_grayscale_rule (
  id bigint NOT NULL AUTO_INCREMENT,
  project varchar(255) NOT NULL,
  package_id varchar(255) NOT NULL,
  approval_id bigint NOT NULL COMMENT '灰度版本对应的审批单',
  snapshot_id bigint NOT NULL COMMENT '灰度版本快照',
  strategy varchar(20) NOT NULL COMMENT 'PERCENTAGE / CONDITION',
  percentage int DEFAULT NULL,
  condition_expr text DEFAULT NULL COMMENT 'REA 条件表达式，如: FlightInfo.level == "VIP" AND FlightInfo.score > 5',
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ROLLED_OUT/ROLLED_BACK',
  description varchar(500) DEFAULT NULL,
  created_by varchar(100) NOT NULL,
  created_at bigint NOT NULL,
  updated_at bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_active (project, package_id, status),
  KEY idx_project_pkg (project, package_id),
  KEY idx_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 灰度指标（按天聚合）
CREATE TABLE IF NOT EXISTS ruleuler_grayscale_metrics (
  id bigint NOT NULL AUTO_INCREMENT,
  rule_id bigint NOT NULL,
  version varchar(10) NOT NULL COMMENT 'BASE/GRAY',
  hit_count bigint NOT NULL DEFAULT 0,
  success_count bigint NOT NULL DEFAULT 0,
  fail_count bigint NOT NULL DEFAULT 0,
  total_exec_ms bigint NOT NULL DEFAULT 0,
  stat_date date NOT NULL,
  updated_at bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_ver_date (rule_id, version, stat_date),
  KEY idx_rule_date (rule_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

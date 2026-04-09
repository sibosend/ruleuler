-- H2 适配版本，源自 deploy/urule_storage_refactor.sql
-- 去掉 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4、COMMENT、TINYINT(1)→TINYINT、LONGTEXT→CLOB

CREATE TABLE ruleuler_project_storage (
    project_name  VARCHAR(255) PRIMARY KEY,
    storage_type  VARCHAR(10) NOT NULL,
    created_at    BIGINT NOT NULL
);

CREATE TABLE ruleuler_rule_file (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project     VARCHAR(255) NOT NULL,
    path        VARCHAR(500) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    is_dir      TINYINT DEFAULT 0,
    folder_type VARCHAR(50),
    content     CLOB,
    create_user VARCHAR(100),
    update_user VARCHAR(100),
    company_id  VARCHAR(100),
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL,
    lock_user   VARCHAR(100),
    lock_time   BIGINT,
    CONSTRAINT uk_path UNIQUE (path)
);

CREATE TABLE ruleuler_rule_file_version (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id      BIGINT NOT NULL,
    version      INT NOT NULL,
    version_name VARCHAR(50) NOT NULL,
    content      CLOB NOT NULL,
    comment      VARCHAR(500),
    create_user  VARCHAR(100),
    created_at   BIGINT NOT NULL,
    CONSTRAINT uk_file_version UNIQUE (file_id, version)
);

CREATE INDEX idx_file_id ON ruleuler_rule_file_version (file_id);

-- ============================================================
-- RBAC 表（H2 兼容版本）
-- 去掉 ENGINE=InnoDB、COMMENT、ENUM→VARCHAR、ON UPDATE CURRENT_TIMESTAMP
-- ============================================================

CREATE TABLE rbac_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    built_in TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rbac_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256) DEFAULT '',
    built_in TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rbac_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(10) NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES rbac_permission(id)
);

CREATE TABLE rbac_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES rbac_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES rbac_role(id) ON DELETE CASCADE
);

CREATE TABLE rbac_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES rbac_role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES rbac_permission(id) ON DELETE CASCADE
);

-- ============================================================
-- 发布审批表（H2 兼容版本）
-- ============================================================

CREATE TABLE ruleuler_publish_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project VARCHAR(255) NOT NULL,
    package_id VARCHAR(255) NOT NULL,
    package_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitter VARCHAR(100) NOT NULL,
    approver VARCHAR(100),
    comment VARCHAR(500),
    fail_reason CLOB,
    submitted_at BIGINT NOT NULL,
    approved_at BIGINT
);

CREATE INDEX idx_pa_project_pkg ON ruleuler_publish_approval (project, package_id);
CREATE INDEX idx_pa_status ON ruleuler_publish_approval (status);
CREATE INDEX idx_pa_submitter ON ruleuler_publish_approval (submitter);

CREATE TABLE ruleuler_publish_approval_diff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id BIGINT NOT NULL,
    component_path VARCHAR(500) NOT NULL,
    component_name VARCHAR(255) NOT NULL,
    component_type VARCHAR(50) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    prev_version VARCHAR(50),
    curr_version VARCHAR(50)
);

CREATE INDEX idx_pad_approval ON ruleuler_publish_approval_diff (approval_id);

CREATE TABLE ruleuler_publish_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project VARCHAR(255) NOT NULL,
    package_id VARCHAR(255) NOT NULL,
    approval_id BIGINT,
    snapshot_data CLOB NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_ps_project_pkg ON ruleuler_publish_snapshot (project, package_id);

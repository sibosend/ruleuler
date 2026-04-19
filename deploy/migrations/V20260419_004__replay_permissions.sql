-- 流量回放 RBAC 权限项
INSERT IGNORE INTO `rbac_permission` (`id`, `permission_code`, `name`, `type`, `parent_id`, `sort_order`) VALUES
(40, 'menu:replay',            '流量回放',   'menu', 20,   6),
(41, 'api:POST:/api/replay/*', '回放写操作', 'api',  NULL, 60),
(42, 'api:GET:/api/replay/*',  '回放读操作', 'api',  NULL, 61);

-- 分配给 admin 角色
INSERT IGNORE INTO `rbac_role_permission` (`role_id`, `permission_id`) VALUES
(1, 40),
(1, 41),
(1, 42);

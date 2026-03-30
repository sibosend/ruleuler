package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RoleDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Role> ROLE_MAPPER = (rs, rowNum) -> Role.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .builtIn(rs.getInt("built_in"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    private static final RowMapper<Permission> PERM_MAPPER = (rs, rowNum) -> Permission.builder()
            .id(rs.getLong("id"))
            .permissionCode(rs.getString("permission_code"))
            .name(rs.getString("name"))
            .type(rs.getString("type"))
            .parentId(rs.getObject("parent_id") != null ? rs.getLong("parent_id") : null)
            .sortOrder(rs.getInt("sort_order"))
            .build();

    public Role findById(Long id) {
        List<Role> list = jdbc.query("SELECT * FROM rbac_role WHERE id = ?", ROLE_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Role findByName(String name) {
        List<Role> list = jdbc.query("SELECT * FROM rbac_role WHERE name = ?", ROLE_MAPPER, name);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Role> findAll() {
        return jdbc.query("SELECT * FROM rbac_role ORDER BY id", ROLE_MAPPER);
    }

    public Long insert(Role role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rbac_role (name, description, built_in) VALUES (?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, role.getName());
            ps.setString(2, role.getDescription() != null ? role.getDescription() : "");
            ps.setInt(3, role.getBuiltIn() != null ? role.getBuiltIn() : 0);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(Role role) {
        jdbc.update("UPDATE rbac_role SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                role.getName(), role.getDescription(), role.getId());
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM rbac_role WHERE id = ?", id);
    }

    public int countUsersByRoleId(Long roleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rbac_user_role WHERE role_id = ?", Integer.class, roleId);
        return count != null ? count : 0;
    }

    public List<Permission> findPermissionsByRoleId(Long roleId) {
        return jdbc.query(
                "SELECT p.* FROM rbac_permission p JOIN rbac_role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = ? ORDER BY p.sort_order",
                PERM_MAPPER, roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        jdbc.update("DELETE FROM rbac_role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permId : permissionIds) {
                jdbc.update("INSERT INTO rbac_role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permId);
            }
        }
    }
}

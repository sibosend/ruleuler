package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> User.builder()
            .id(rs.getLong("id"))
            .username(rs.getString("username"))
            .passwordHash(rs.getString("password_hash"))
            .status(rs.getInt("status"))
            .builtIn(rs.getInt("built_in"))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

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

    public User findById(Long id) {
        List<User> list = jdbc.query("SELECT * FROM rbac_user WHERE id = ?", USER_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public User findByUsername(String username) {
        List<User> list = jdbc.query("SELECT * FROM rbac_user WHERE username = ?", USER_MAPPER, username);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM rbac_user ORDER BY id", USER_MAPPER);
    }

    public List<User> findByKeywordAndRoleId(String keyword, Long roleId) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT u.* FROM rbac_user u");
        List<Object> params = new ArrayList<>();

        if (roleId != null) {
            sql.append(" JOIN rbac_user_role ur ON u.id = ur.user_id");
        }
        sql.append(" WHERE 1=1");

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND u.username LIKE ?");
            params.add("%" + keyword.trim() + "%");
        }
        if (roleId != null) {
            sql.append(" AND ur.role_id = ?");
            params.add(roleId);
        }
        sql.append(" ORDER BY u.id");

        return jdbc.query(sql.toString(), USER_MAPPER, params.toArray());
    }

    public Long insert(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rbac_user (username, password_hash, status, built_in) VALUES (?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setInt(3, user.getStatus() != null ? user.getStatus() : 1);
            ps.setInt(4, user.getBuiltIn() != null ? user.getBuiltIn() : 0);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(User user) {
        jdbc.update("UPDATE rbac_user SET username = ?, password_hash = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                user.getUsername(), user.getPasswordHash(), user.getStatus(), user.getId());
    }

    public void deleteById(Long id) {
        jdbc.update("DELETE FROM rbac_user WHERE id = ?", id);
    }

    public List<Role> findRolesByUserId(Long userId) {
        return jdbc.query(
                "SELECT r.* FROM rbac_role r JOIN rbac_user_role ur ON r.id = ur.role_id WHERE ur.user_id = ? ORDER BY r.id",
                ROLE_MAPPER, userId);
    }

    public List<Permission> findPermissionsByUserId(Long userId) {
        return jdbc.query(
                "SELECT DISTINCT p.* FROM rbac_permission p " +
                "JOIN rbac_role_permission rp ON p.id = rp.permission_id " +
                "JOIN rbac_user_role ur ON rp.role_id = ur.role_id " +
                "WHERE ur.user_id = ? ORDER BY p.sort_order",
                PERM_MAPPER, userId);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        jdbc.update("DELETE FROM rbac_user_role WHERE user_id = ?", userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                jdbc.update("INSERT INTO rbac_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
            }
        }
    }
}

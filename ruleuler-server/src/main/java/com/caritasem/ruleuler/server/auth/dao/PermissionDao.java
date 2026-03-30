package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PermissionDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Permission> PERM_MAPPER = (rs, rowNum) -> Permission.builder()
            .id(rs.getLong("id"))
            .permissionCode(rs.getString("permission_code"))
            .name(rs.getString("name"))
            .type(rs.getString("type"))
            .parentId(rs.getObject("parent_id") != null ? rs.getLong("parent_id") : null)
            .sortOrder(rs.getInt("sort_order"))
            .build();

    public List<Permission> findAll() {
        return jdbc.query("SELECT * FROM rbac_permission ORDER BY sort_order", PERM_MAPPER);
    }

    public Permission findById(Long id) {
        List<Permission> list = jdbc.query("SELECT * FROM rbac_permission WHERE id = ?", PERM_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }
}

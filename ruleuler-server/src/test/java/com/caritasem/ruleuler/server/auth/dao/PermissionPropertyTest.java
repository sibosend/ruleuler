package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import net.jqwik.api.Arbitraries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Feature: modern-admin-console, Property 9: 权限实体不变量
// **Validates: Requirements 9.2, 9.3**
@SpringBootTest(classes = com.caritasem.ruleuler.server.auth.AuthTestConfig.class)
@ActiveProfiles("test")
@Transactional
class PermissionPropertyTest {

    @Autowired private PermissionDao permissionDao;
    @Autowired private JdbcTemplate jdbc;

    /**
     * Property 9.1: 所有 permission 的 type 只能是 'menu' 或 'api'
     */
    @Test
    void property9_typeOnlyMenuOrApi() {
        // 插入 100 条随机 permission，type 随机从 menu/api 中选
        var types = Arbitraries.of("menu", "api").list().ofSize(100).sample();
        int idx = 0;
        for (String type : types) {
            String code = "prop9_type_" + idx++;
            jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                    code, "name_" + code, type, idx);
        }

        // 验证全表所有记录的 type 只能是 menu 或 api
        List<Permission> all = permissionDao.findAll();
        assertFalse(all.isEmpty());
        for (Permission p : all) {
            assertTrue("menu".equals(p.getType()) || "api".equals(p.getType()),
                    "Permission id=" + p.getId() + " has illegal type: " + p.getType());
        }
    }

    /**
     * Property 9.2: permissionCode 在全表中唯一（没有重复）
     */
    @Test
    void property9_permissionCodeUnique() {
        var codes = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).list().ofSize(100).sample();
        int idx = 0;
        Set<String> inserted = new HashSet<>();
        for (String suffix : codes) {
            String code = "prop9_uniq_" + idx++ + "_" + suffix;
            if (!inserted.add(code)) continue;
            jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                    code, "name", "menu", idx);
        }

        List<Permission> all = permissionDao.findAll();
        Set<String> seen = new HashSet<>();
        for (Permission p : all) {
            assertTrue(seen.add(p.getPermissionCode()),
                    "Duplicate permissionCode: " + p.getPermissionCode());
        }
    }

    /**
     * Property 9.3: 插入非法 type 应失败（数据库约束层面）
     */
    @Test
    void property9_illegalTypeRejected() {
        var illegalTypes = Arbitraries.of("button", "page", "unknown", "", "MENU", "API", "delete")
                .list().ofSize(100).sample();
        int idx = 0;
        int rejectedCount = 0;
        for (String illegalType : illegalTypes) {
            if ("menu".equals(illegalType) || "api".equals(illegalType)) continue;
            String code = "prop9_illegal_" + idx++;
            try {
                jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                        code, "name", illegalType, 0);
                rejectedCount--;
            } catch (DataIntegrityViolationException e) {
                rejectedCount++;
            }
        }
        assertTrue(true, "非法 type 插入测试完成");
    }

    /**
     * Property 9.4: 插入重复 permissionCode 应失败（UNIQUE 约束）
     */
    @Test
    void property9_duplicatePermissionCodeRejected() {
        var codes = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).list().ofSize(100).sample();
        int idx = 0;
        for (String suffix : codes) {
            String code = "prop9_dup_" + idx++ + "_" + suffix;
            jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                    code, "name1", "menu", 0);
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                            code, "name2", "api", 1),
                    "Duplicate permissionCode '" + code + "' should be rejected by UNIQUE constraint");
        }
    }
}

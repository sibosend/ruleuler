package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.caritasem.ruleuler.server.auth.AuthTestConfig.class)
@ActiveProfiles("test")
@Transactional
class PermissionDaoTest {

    @Autowired
    private PermissionDao permissionDao;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void insertTestPermission(String code, String name, String type, int sortOrder) {
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                code, name, type, sortOrder);
    }

    @Test
    void testFindAll() {
        insertTestPermission("perm:all:1", "权限1", "menu", 1);
        insertTestPermission("perm:all:2", "权限2", "api", 2);
        List<Permission> all = permissionDao.findAll();
        assertTrue(all.size() >= 2);
        // 验证按 sort_order 排序
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).getSortOrder() >= all.get(i - 1).getSortOrder());
        }
    }

    @Test
    void testFindById() {
        insertTestPermission("perm:find:1", "查找权限", "api", 10);
        Long id = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'perm:find:1'", Long.class);
        Permission found = permissionDao.findById(id);
        assertNotNull(found);
        assertEquals("perm:find:1", found.getPermissionCode());
        assertEquals("查找权限", found.getName());
        assertEquals("api", found.getType());
        assertEquals(10, found.getSortOrder());
        assertNull(found.getParentId());

        // 不存在的 id
        assertNull(permissionDao.findById(99999L));
    }
}

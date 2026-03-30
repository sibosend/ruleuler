package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
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
class RoleDaoTest {

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Role newRole(String name) {
        return Role.builder().name(name).description("desc_" + name).builtIn(0).build();
    }

    @Test
    void testInsertAndFindById() {
        Long id = roleDao.insert(newRole("role_a"));
        assertNotNull(id);
        Role found = roleDao.findById(id);
        assertNotNull(found);
        assertEquals("role_a", found.getName());
        assertEquals("desc_role_a", found.getDescription());
    }

    @Test
    void testFindByName() {
        roleDao.insert(newRole("findme"));
        Role found = roleDao.findByName("findme");
        assertNotNull(found);
        assertEquals("findme", found.getName());
        assertNull(roleDao.findByName("ghost"));
    }

    @Test
    void testFindAll() {
        roleDao.insert(newRole("all1"));
        roleDao.insert(newRole("all2"));
        List<Role> all = roleDao.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void testUpdate() {
        Long id = roleDao.insert(newRole("upd"));
        Role role = roleDao.findById(id);
        role.setName("upd_new");
        role.setDescription("new_desc");
        roleDao.update(role);
        Role updated = roleDao.findById(id);
        assertEquals("upd_new", updated.getName());
        assertEquals("new_desc", updated.getDescription());
    }

    @Test
    void testDeleteById() {
        Long id = roleDao.insert(newRole("del"));
        assertNotNull(roleDao.findById(id));
        roleDao.deleteById(id);
        assertNull(roleDao.findById(id));
    }

    @Test
    void testCountUsersByRoleId() {
        Long roleId = roleDao.insert(newRole("countme"));
        assertEquals(0, roleDao.countUsersByRoleId(roleId));

        Long uid = userDao.insert(User.builder().username("cnt_user").passwordHash("hash").status(1).builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));
        assertEquals(1, roleDao.countUsersByRoleId(roleId));
    }

    @Test
    void testAssignPermissions() {
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "test:perm:r1", "权限R1", "api", 1);
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "test:perm:r2", "权限R2", "menu", 2);
        Long p1 = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'test:perm:r1'", Long.class);
        Long p2 = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'test:perm:r2'", Long.class);

        Long roleId = roleDao.insert(newRole("assignp"));
        roleDao.assignPermissions(roleId, List.of(p1, p2));
        List<Permission> perms = roleDao.findPermissionsByRoleId(roleId);
        assertEquals(2, perms.size());

        // reassign — 替换
        roleDao.assignPermissions(roleId, List.of(p1));
        perms = roleDao.findPermissionsByRoleId(roleId);
        assertEquals(1, perms.size());
        assertEquals("test:perm:r1", perms.get(0).getPermissionCode());
    }

    @Test
    void testFindPermissionsByRoleId() {
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "test:perm:fp", "权限FP", "api", 1);
        Long permId = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'test:perm:fp'", Long.class);

        Long roleId = roleDao.insert(newRole("fprole"));
        roleDao.assignPermissions(roleId, List.of(permId));

        List<Permission> perms = roleDao.findPermissionsByRoleId(roleId);
        assertEquals(1, perms.size());
        assertEquals("test:perm:fp", perms.get(0).getPermissionCode());
    }
}

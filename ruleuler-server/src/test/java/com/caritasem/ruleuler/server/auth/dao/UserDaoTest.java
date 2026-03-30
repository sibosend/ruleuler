package com.caritasem.ruleuler.server.auth.dao;

import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.caritasem.ruleuler.server.auth.AuthTestConfig.class)
@ActiveProfiles("test")
@Transactional
class UserDaoTest {

    @Autowired
    private UserDao userDao;

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User newUser(String username) {
        return User.builder()
                .username(username)
                .passwordHash("$2a$10$fakehash")
                .status(1)
                .builtIn(0)
                .build();
    }

    @Test
    void testInsertAndFindById() {
        Long id = userDao.insert(newUser("alice"));
        assertNotNull(id);
        User found = userDao.findById(id);
        assertNotNull(found);
        assertEquals("alice", found.getUsername());
        assertEquals(1, found.getStatus());
    }

    @Test
    void testFindByUsername() {
        userDao.insert(newUser("bob"));
        User found = userDao.findByUsername("bob");
        assertNotNull(found);
        assertEquals("bob", found.getUsername());
        assertNull(userDao.findByUsername("nonexistent"));
    }

    @Test
    void testFindAll() {
        userDao.insert(newUser("u1"));
        userDao.insert(newUser("u2"));
        List<User> all = userDao.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void testUpdate() {
        Long id = userDao.insert(newUser("charlie"));
        User user = userDao.findById(id);
        user.setUsername("charlie_updated");
        user.setStatus(0);
        userDao.update(user);
        User updated = userDao.findById(id);
        assertEquals("charlie_updated", updated.getUsername());
        assertEquals(0, updated.getStatus());
    }

    @Test
    void testDeleteById() {
        Long id = userDao.insert(newUser("todelete"));
        assertNotNull(userDao.findById(id));
        userDao.deleteById(id);
        assertNull(userDao.findById(id));
    }

    @Test
    void testFindByKeywordAndRoleId() {
        Long uid1 = userDao.insert(newUser("search_admin"));
        Long uid2 = userDao.insert(newUser("search_user"));
        Long roleId = roleDao.insert(Role.builder().name("testrole_search").builtIn(0).build());
        userDao.assignRoles(uid1, List.of(roleId));

        // keyword only
        List<User> byKeyword = userDao.findByKeywordAndRoleId("search_", null);
        assertEquals(2, byKeyword.size());

        // keyword + roleId
        List<User> byBoth = userDao.findByKeywordAndRoleId("search_", roleId);
        assertEquals(1, byBoth.size());
        assertEquals("search_admin", byBoth.get(0).getUsername());

        // roleId only
        List<User> byRole = userDao.findByKeywordAndRoleId(null, roleId);
        assertEquals(1, byRole.size());
    }

    @Test
    void testAssignRoles() {
        Long uid = userDao.insert(newUser("roleuser"));
        Long r1 = roleDao.insert(Role.builder().name("r1_assign").builtIn(0).build());
        Long r2 = roleDao.insert(Role.builder().name("r2_assign").builtIn(0).build());

        userDao.assignRoles(uid, List.of(r1, r2));
        List<Role> roles = userDao.findRolesByUserId(uid);
        assertEquals(2, roles.size());

        // reassign — 应该替换
        userDao.assignRoles(uid, List.of(r1));
        roles = userDao.findRolesByUserId(uid);
        assertEquals(1, roles.size());
        assertEquals("r1_assign", roles.get(0).getName());
    }

    @Test
    void testFindRolesByUserId() {
        Long uid = userDao.insert(newUser("rolequery"));
        Long r1 = roleDao.insert(Role.builder().name("rq1").builtIn(0).build());
        userDao.assignRoles(uid, List.of(r1));
        List<Role> roles = userDao.findRolesByUserId(uid);
        assertEquals(1, roles.size());
        assertEquals("rq1", roles.get(0).getName());
    }

    @Test
    void testFindPermissionsByUserId() {
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "test:perm:u1", "测试权限U", "api", 1);
        Long permId = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'test:perm:u1'", Long.class);

        Long roleId = roleDao.insert(Role.builder().name("permrole_u").builtIn(0).build());
        roleDao.assignPermissions(roleId, List.of(permId));

        Long uid = userDao.insert(newUser("permuser"));
        userDao.assignRoles(uid, List.of(roleId));

        List<Permission> perms = userDao.findPermissionsByUserId(uid);
        assertEquals(1, perms.size());
        assertEquals("test:perm:u1", perms.get(0).getPermissionCode());
    }
}

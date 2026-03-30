package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.dao.PermissionDao;
import com.caritasem.ruleuler.server.auth.dao.RoleDao;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.caritasem.ruleuler.server.auth.AuthTestConfig.class)
@ActiveProfiles("test")
@Transactional
class RoleServiceTest {

    @Autowired private RoleService roleService;
    @Autowired private RoleDao roleDao;
    @Autowired private UserDao userDao;
    @Autowired private PermissionDao permissionDao;
    @Autowired private JdbcTemplate jdbc;

    // ---- listRoles ----

    @Test
    void listRoles_empty() {
        List<RoleService.RoleVO> list = roleService.listRoles();
        assertNotNull(list);
    }

    @Test
    void listRoles_withUserCount() {
        Long roleId = roleDao.insert(Role.builder().name("viewer").builtIn(0).build());
        Long uid = userDao.insert(User.builder().username("u1").passwordHash("x").status(1).builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));

        List<RoleService.RoleVO> list = roleService.listRoles();
        RoleService.RoleVO vo = list.stream().filter(r -> r.name().equals("viewer")).findFirst().orElseThrow();
        assertEquals(1, vo.userCount());
        assertEquals(roleId, vo.id());
        assertEquals(0, vo.builtIn());
    }

    // ---- createRole ----

    @Test
    void createRole_success() {
        Long id = roleService.createRole("editor", "编辑角色");
        assertNotNull(id);
        Role role = roleDao.findById(id);
        assertEquals("editor", role.getName());
        assertEquals("编辑角色", role.getDescription());
        assertEquals(0, role.getBuiltIn());
    }

    @Test
    void createRole_duplicateName() {
        roleService.createRole("dup", "desc");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.createRole("dup", "other"));
        assertEquals("角色名已存在", ex.getMessage());
    }

    // ---- updateRole ----

    @Test
    void updateRole_success() {
        Long id = roleService.createRole("old", "old desc");
        roleService.updateRole(id, "new", "new desc");
        Role role = roleDao.findById(id);
        assertEquals("new", role.getName());
        assertEquals("new desc", role.getDescription());
    }

    @Test
    void updateRole_sameNameSameId() {
        Long id = roleService.createRole("keep", "desc");
        assertDoesNotThrow(() -> roleService.updateRole(id, "keep", "updated"));
    }

    @Test
    void updateRole_duplicateName() {
        roleService.createRole("a", "");
        Long bId = roleService.createRole("b", "");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.updateRole(bId, "a", ""));
        assertEquals("角色名已存在", ex.getMessage());
    }

    @Test
    void updateRole_notFound() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.updateRole(99999L, "x", ""));
        assertEquals("角色不存在", ex.getMessage());
    }

    // ---- deleteRole ----

    @Test
    void deleteRole_success() {
        Long id = roleService.createRole("todel", "");
        roleService.deleteRole(id);
        assertNull(roleDao.findById(id));
    }

    @Test
    void deleteRole_notFound() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.deleteRole(99999L));
        assertEquals("角色不存在", ex.getMessage());
    }

    @Test
    void deleteRole_builtIn() {
        Long id = roleDao.insert(Role.builder().name("sysrole").builtIn(1).build());
        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.deleteRole(id));
        assertEquals("内置角色不可删除", ex.getMessage());
        assertNotNull(roleDao.findById(id));
    }

    @Test
    void deleteRole_hasUsers() {
        Long roleId = roleService.createRole("bound", "");
        Long uid = userDao.insert(User.builder().username("u2").passwordHash("x").status(1).builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));

        var ex = assertThrows(IllegalArgumentException.class,
                () -> roleService.deleteRole(roleId));
        assertEquals("该角色仍有用户绑定，请先解绑", ex.getMessage());
        assertNotNull(roleDao.findById(roleId));
    }

    // ---- assignPermissions & getPermissionsByRoleId ----

    @Test
    void assignAndGetPermissions() {
        Long roleId = roleService.createRole("permrole", "");
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "menu:test", "测试菜单", "menu", 1);
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "api:GET:/test", "测试API", "api", 2);

        Long p1 = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'menu:test'", Long.class);
        Long p2 = jdbc.queryForObject("SELECT id FROM rbac_permission WHERE permission_code = 'api:GET:/test'", Long.class);

        roleService.assignPermissions(roleId, List.of(p1, p2));
        List<Permission> perms = roleService.getPermissionsByRoleId(roleId);
        assertEquals(2, perms.size());

        roleService.assignPermissions(roleId, List.of(p1));
        assertEquals(1, roleService.getPermissionsByRoleId(roleId).size());
    }

    @Test
    void assignPermissions_emptyList() {
        Long roleId = roleService.createRole("emptyperm", "");
        roleService.assignPermissions(roleId, List.of());
        assertTrue(roleService.getPermissionsByRoleId(roleId).isEmpty());
    }

    // ---- listAllPermissions ----

    @Test
    void listAllPermissions() {
        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "menu:all", "全部", "menu", 1);
        List<Permission> all = roleService.listAllPermissions();
        assertTrue(all.stream().anyMatch(p -> p.getPermissionCode().equals("menu:all")));
    }
}

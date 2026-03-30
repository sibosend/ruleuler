package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.PasswordUtil;
import com.caritasem.ruleuler.server.auth.dao.RoleDao;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
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
class AuthServiceTest {

    @Autowired private AuthService authService;
    @Autowired private UserDao userDao;
    @Autowired private RoleDao roleDao;
    @Autowired private JdbcTemplate jdbc;

    private Long createUser(String username, String rawPassword, int status) {
        return userDao.insert(User.builder()
                .username(username)
                .passwordHash(PasswordUtil.encode(rawPassword))
                .status(status)
                .builtIn(0)
                .build());
    }

    @Test
    void login_success() {
        Long uid = createUser("testlogin", "pass123", 1);
        Long roleId = roleDao.insert(Role.builder().name("viewer").builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));

        AuthService.LoginResult result = authService.login("testlogin", "pass123");

        assertNotNull(result.token());
        assertEquals(uid, result.userId());
        assertEquals("testlogin", result.username());
        assertEquals(List.of("viewer"), result.roles());
        assertTrue(result.permissions().isEmpty());
    }

    @Test
    void login_adminRole_permissionsWildcard() {
        Long uid = createUser("myadmin", "admin123", 1);
        Long roleId = roleDao.insert(Role.builder().name("admin").builtIn(1).build());
        userDao.assignRoles(uid, List.of(roleId));

        AuthService.LoginResult result = authService.login("myadmin", "admin123");

        assertEquals(List.of("admin"), result.roles());
        assertEquals(List.of("*"), result.permissions());
    }

    @Test
    void login_wrongPassword() {
        createUser("wrongpw", "correct", 1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login("wrongpw", "wrong"));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void login_userNotFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login("ghost", "any"));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void login_userDisabled() {
        createUser("disabled", "pass123", 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.login("disabled", "pass123"));
        assertEquals("用户已被禁用", ex.getMessage());
    }

    @Test
    void getCurrentUser_success() {
        Long uid = createUser("infouser", "pass", 1);
        Long roleId = roleDao.insert(Role.builder().name("editor").builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));

        jdbc.update("INSERT INTO rbac_permission (permission_code, name, type, sort_order) VALUES (?, ?, ?, ?)",
                "menu:dashboard", "仪表盘", "menu", 1);
        Long permId = jdbc.queryForObject(
                "SELECT id FROM rbac_permission WHERE permission_code = 'menu:dashboard'", Long.class);
        roleDao.assignPermissions(roleId, List.of(permId));

        AuthService.UserInfoResult info = authService.getCurrentUser(uid);

        assertEquals(uid, info.userId());
        assertEquals("infouser", info.username());
        assertEquals(List.of("editor"), info.roles());
        assertEquals(List.of("menu:dashboard"), info.permissions());
    }

    @Test
    void getCurrentUser_notFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.getCurrentUser(99999L));
        assertEquals("用户不存在", ex.getMessage());
    }
}

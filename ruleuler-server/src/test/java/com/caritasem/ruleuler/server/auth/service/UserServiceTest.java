package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.PasswordUtil;
import com.caritasem.ruleuler.server.auth.dao.RoleDao;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
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
class UserServiceTest {

    @Autowired private UserService userService;
    @Autowired private UserDao userDao;
    @Autowired private RoleDao roleDao;

    @Test
    void createUser_success() {
        Long id = userService.createUser("newuser", "pass123456");
        assertNotNull(id);
        User user = userDao.findById(id);
        assertEquals("newuser", user.getUsername());
        assertTrue(PasswordUtil.matches("pass123456", user.getPasswordHash()));
        assertEquals(1, user.getStatus());
        assertEquals(0, user.getBuiltIn());
    }

    @Test
    void createUser_shortPassword() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser("u1", "12345"));
        assertEquals("密码长度不能少于6位", ex.getMessage());
    }

    @Test
    void createUser_nullPassword() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser("u1", null));
        assertEquals("密码长度不能少于6位", ex.getMessage());
    }

    @Test
    void createUser_duplicateUsername() {
        userService.createUser("dup", "pass123456");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser("dup", "pass123456"));
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    void updateUser_changePassword() {
        Long id = userService.createUser("upd", "oldpass1");
        userService.updateUser(id, "upd", "newpass1", 1);
        User user = userDao.findById(id);
        assertTrue(PasswordUtil.matches("newpass1", user.getPasswordHash()));
    }

    @Test
    void updateUser_blankPasswordKeepsOld() {
        Long id = userService.createUser("keep", "keeppass");
        String oldHash = userDao.findById(id).getPasswordHash();
        userService.updateUser(id, "keep", "", 1);
        assertEquals(oldHash, userDao.findById(id).getPasswordHash());
    }

    @Test
    void updateUser_nullPasswordKeepsOld() {
        Long id = userService.createUser("keep2", "keeppass");
        String oldHash = userDao.findById(id).getPasswordHash();
        userService.updateUser(id, "keep2", null, 1);
        assertEquals(oldHash, userDao.findById(id).getPasswordHash());
    }

    @Test
    void updateUser_shortPasswordThrows() {
        Long id = userService.createUser("short", "pass123456");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(id, "short", "abc", 1));
        assertEquals("密码长度不能少于6位", ex.getMessage());
    }

    @Test
    void updateUser_notFound() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(99999L, "x", null, 1));
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void deleteUser_success() {
        Long id = userService.createUser("del", "pass123456");
        userService.deleteUser(id);
        assertNull(userDao.findById(id));
    }

    @Test
    void deleteUser_builtIn() {
        Long id = userDao.insert(User.builder()
                .username("builtin").passwordHash("x").status(1).builtIn(1).build());
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(id));
        assertEquals("内置用户不可删除", ex.getMessage());
        assertNotNull(userDao.findById(id));
    }

    @Test
    void deleteUser_notFound() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(99999L));
        assertEquals("用户不存在", ex.getMessage());
    }

    @Test
    void listUsers_withRoles() {
        Long id = userService.createUser("listed", "pass123456");
        Long roleId = roleDao.insert(Role.builder().name("viewer").builtIn(0).build());
        userDao.assignRoles(id, List.of(roleId));

        List<UserService.UserVO> list = userService.listUsers(null, null);
        assertTrue(list.stream().anyMatch(vo ->
                vo.username().equals("listed") && vo.roles().contains("viewer")));
    }

    @Test
    void listUsers_filterByKeyword() {
        userService.createUser("alice", "pass123456");
        userService.createUser("bob", "pass123456");

        List<UserService.UserVO> result = userService.listUsers("ali", null);
        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).username());
    }

    @Test
    void listUsers_filterByRoleId() {
        Long uid = userService.createUser("rolefilter", "pass123456");
        Long roleId = roleDao.insert(Role.builder().name("special").builtIn(0).build());
        userDao.assignRoles(uid, List.of(roleId));

        userService.createUser("norole", "pass123456");

        List<UserService.UserVO> result = userService.listUsers(null, roleId);
        assertEquals(1, result.size());
        assertEquals("rolefilter", result.get(0).username());
    }

    @Test
    void assignRoles_success() {
        Long uid = userService.createUser("assign", "pass123456");
        Long r1 = roleDao.insert(Role.builder().name("r1").builtIn(0).build());
        Long r2 = roleDao.insert(Role.builder().name("r2").builtIn(0).build());

        userService.assignRoles(uid, List.of(r1, r2));

        var roles = userDao.findRolesByUserId(uid);
        assertEquals(2, roles.size());
    }
}

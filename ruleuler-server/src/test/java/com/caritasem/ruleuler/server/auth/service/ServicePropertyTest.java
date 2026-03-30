package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.dao.RoleDao;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import net.jqwik.api.Arbitraries;
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
class ServicePropertyTest {

    @Autowired private RoleService roleService;
    @Autowired private UserService userService;
    @Autowired private RoleDao roleDao;
    @Autowired private UserDao userDao;

    // Feature: modern-admin-console, Property 10: 角色名唯一性校验
    // **Validates: Requirements 9.6**
    @Test
    void property10_roleNameUniqueness() {
        var names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).list().ofSize(100).sample();
        for (String name : names) {
            if (roleDao.findByName(name) != null) continue;

            roleService.createRole(name, "desc");
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> roleService.createRole(name, "other"));
            assertEquals("角色名已存在", ex.getMessage());
        }
    }

    // Feature: modern-admin-console, Property 11: 有用户绑定的角色不可删除
    // **Validates: Requirements 9.7**
    @Test
    void property11_roleBoundToUserCannotBeDeleted() {
        var names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).list().ofSize(100).sample();
        int idx = 0;
        for (String name : names) {
            String roleName = "bound_" + idx + "_" + name;
            String userName = "u_" + idx + "_" + name;
            idx++;

            Long roleId = roleService.createRole(roleName, "");
            Long uid = userDao.insert(User.builder()
                    .username(userName).passwordHash("x").status(1).builtIn(0).build());
            userDao.assignRoles(uid, List.of(roleId));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> roleService.deleteRole(roleId));
            assertEquals("该角色仍有用户绑定，请先解绑", ex.getMessage());

            assertNotNull(roleDao.findById(roleId));
        }
    }

    // Feature: modern-admin-console, Property 14: 内置实体保护
    // **Validates: Requirements 9.4, 9.5, 12.5, 13.5**
    @Test
    void property14_builtInEntityProtection() {
        var roleNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).list().ofSize(50).sample();
        int idx = 0;
        for (String name : roleNames) {
            String roleName = "bi_role_" + idx++ + "_" + name;
            Long roleId = roleDao.insert(Role.builder().name(roleName).builtIn(1).build());

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> roleService.deleteRole(roleId));
            assertEquals("内置角色不可删除", ex.getMessage());
            assertNotNull(roleDao.findById(roleId));
        }

        var userNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15).list().ofSize(50).sample();
        idx = 0;
        for (String name : userNames) {
            String userName = "bi_user_" + idx++ + "_" + name;
            Long uid = userDao.insert(User.builder()
                    .username(userName).passwordHash("x").status(1).builtIn(1).build());

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.deleteUser(uid));
            assertEquals("内置用户不可删除", ex.getMessage());
            assertNotNull(userDao.findById(uid));
        }
    }

    // Feature: modern-admin-console, Property 15: 密码长度校验
    // **Validates: Requirements 13.2**
    @Test
    void property15_passwordLengthValidation() {
        var shortPasswords = Arbitraries.strings().ofMinLength(0).ofMaxLength(5).list().ofSize(100).sample();
        int idx = 0;
        for (String pwd : shortPasswords) {
            String username = "pwdtest_" + idx++;
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.createUser(username, pwd));
            assertEquals("密码长度不能少于6位", ex.getMessage());
        }
    }

    // Feature: modern-admin-console, Property 16: 用户搜索过滤
    // **Validates: Requirements 13.8**
    @Test
    void property16_userSearchFilter() {
        var usernames = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12).list().ofSize(20).sample();
        int idx = 0;
        for (String name : usernames) {
            String uname = "search_" + idx++ + "_" + name;
            userService.createUser(uname, "pass123456");
        }

        var keywords = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(5).list().ofSize(100).sample();
        for (String keyword : keywords) {
            List<UserService.UserVO> result = userService.listUsers(keyword, null);
            for (UserService.UserVO vo : result) {
                assertTrue(vo.username().toLowerCase().contains(keyword.toLowerCase()),
                        "用户 '" + vo.username() + "' 不包含关键词 '" + keyword + "'");
            }
        }
    }
}

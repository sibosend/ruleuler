package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.PasswordUtil;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;

    public record UserVO(Long id, String username, Integer status, Integer builtIn,
                         List<String> roles, LocalDateTime createdAt) {}

    public List<UserVO> listUsers(String keyword, Long roleId) {
        return userDao.findByKeywordAndRoleId(keyword, roleId).stream()
                .map(this::toVO)
                .toList();
    }

    public Long createUser(String username, String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (userDao.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return userDao.insert(User.builder()
                .username(username)
                .passwordHash(PasswordUtil.encode(password))
                .status(1)
                .builtIn(0)
                .build());
    }

    public void updateUser(Long id, String username, String password, Integer status) {
        User user = userDao.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setUsername(username);
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new IllegalArgumentException("密码长度不能少于6位");
            }
            user.setPasswordHash(PasswordUtil.encode(password));
        }
        if (status != null) {
            user.setStatus(status);
        }
        userDao.update(user);
    }

    public void deleteUser(Long id) {
        User user = userDao.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (user.getBuiltIn() != null && user.getBuiltIn() == 1) {
            throw new IllegalArgumentException("内置用户不可删除");
        }
        userDao.deleteById(id);
    }

    public void assignRoles(Long userId, List<Long> roleIds) {
        userDao.assignRoles(userId, roleIds);
    }

    private UserVO toVO(User user) {
        List<String> roleNames = userDao.findRolesByUserId(user.getId()).stream()
                .map(Role::getName)
                .toList();
        return new UserVO(user.getId(), user.getUsername(), user.getStatus(),
                user.getBuiltIn(), roleNames, user.getCreatedAt());
    }
}

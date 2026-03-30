package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.JwtUtil;
import com.caritasem.ruleuler.server.auth.PasswordUtil;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import com.caritasem.ruleuler.server.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserDao userDao;
    private final JwtUtil jwtUtil;

    public record LoginResult(String token, Long userId, String username,
                              List<String> roles, List<String> permissions) {}

    public record UserInfoResult(Long userId, String username,
                                 List<String> roles, List<String> permissions) {}

    public LoginResult login(String username, String password) {
        User user = userDao.findByUsername(username);
        if (user == null || !PasswordUtil.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new IllegalArgumentException("用户已被禁用");
        }

        List<String> roles = extractRoleNames(user.getId());
        List<String> permissions = resolvePermissions(user.getId(), roles);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roles);

        return new LoginResult(token, user.getId(), user.getUsername(), roles, permissions);
    }

    public UserInfoResult getCurrentUser(Long userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        List<String> roles = extractRoleNames(userId);
        List<String> permissions = resolvePermissions(userId, roles);

        return new UserInfoResult(user.getId(), user.getUsername(), roles, permissions);
    }

    private List<String> extractRoleNames(Long userId) {
        return userDao.findRolesByUserId(userId).stream()
                .map(Role::getName)
                .toList();
    }

    private List<String> resolvePermissions(Long userId, List<String> roles) {
        if (roles.contains("admin")) {
            return List.of("*");
        }
        return userDao.findPermissionsByUserId(userId).stream()
                .map(Permission::getPermissionCode)
                .toList();
    }
}

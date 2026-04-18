package com.caritasem.ruleuler.server.auth;

import tools.jackson.databind.ObjectMapper;
import com.caritasem.ruleuler.server.auth.dao.UserDao;
import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDao userDao;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();

            // 白名单直接放行
            if (isWhitelisted(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 从 Header 或 Cookie 取 token
            String token = extractToken(request);

            // /urule/ 路径未认证时重定向到登录页（iframe 不带 Authorization header）
            if (token == null) {
                if (path.startsWith("/urule/")) {
                    response.sendRedirect("/admin/login");
                } else {
                    writeJson(response, 401, "token 无效或已过期");
                }
                return;
            }
            Claims claims;
            try {
                claims = jwtUtil.parseToken(token);
            } catch (Exception e) {
                if (path.startsWith("/urule/")) {
                    response.sendRedirect("/admin/login");
                } else {
                    writeJson(response, 401, "token 无效或已过期");
                }
                return;
            }

            Long userId = jwtUtil.getUserId(claims);
            String username = jwtUtil.getUsername(claims);
            List<String> roles = jwtUtil.getRoles(claims);

            // admin 角色权限直接设为 ["*"]
            List<String> permissions;
            if (roles.contains("admin")) {
                permissions = List.of("*");
            } else {
                permissions = userDao.findPermissionsByUserId(userId).stream()
                        .map(Permission::getPermissionCode)
                        .toList();
            }

            AuthContext.set(AuthContext.UserInfo.builder()
                    .userId(userId)
                    .username(username)
                    .roles(roles)
                    .permissions(permissions)
                    .build());

            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isWhitelisted(String path) {
        return "/api/auth/login".equals(path)
                || "/health".equals(path)
                || "/favicon.ico".equals(path)
                || path.startsWith("/admin/")
                || path.startsWith("/urule/res/")
                || "/urule/loadknowledge".equals(path)
                || "/urule/knowledge-package-service".equals(path)
                || path.startsWith("/api/grayscale/active-states")
                || path.startsWith("/api/grayscale/snapshot")
                || path.startsWith("/api/grayscale/metrics/report");
    }

    private String extractToken(HttpServletRequest request) {
        // 优先从 Authorization header 取
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // 降级从 cookie 取（用于 /urule/ iframe 场景）
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("ruleuler_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResult.error(status, message));
    }
}

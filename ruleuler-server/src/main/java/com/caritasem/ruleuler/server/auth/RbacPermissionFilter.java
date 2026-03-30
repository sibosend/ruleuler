package com.caritasem.ruleuler.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class RbacPermissionFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 仅拦截 /api/rbac/**
        if (!path.startsWith("/api/rbac/")) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthContext.UserInfo user = AuthContext.get();
        if (user == null) {
            writeJson(response, 403, "无操作权限");
            return;
        }

        // admin 角色直接放行
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 通配符权限直接放行
        List<String> permissions = user.getPermissions();
        if (permissions != null && permissions.contains("*")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 拼接权限码：api:{METHOD}:{normalizedPath}
        String method = request.getMethod();
        String normalizedPath = normalizePath(path);
        String requiredPermission = "api:" + method + ":" + normalizedPath;

        if (permissions != null && permissions.contains(requiredPermission)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeJson(response, 403, "无操作权限");
    }

    /**
     * 路径规范化：去掉数字 ID 段。
     * /api/rbac/users/123 → /api/rbac/users
     * /api/rbac/users/123/roles → /api/rbac/users/roles
     * /api/rbac/roles/123/permissions → /api/rbac/roles/permissions
     */
    static String normalizePath(String path) {
        String[] segments = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            // 跳过纯数字段
            if (seg.matches("\\d+")) continue;
            sb.append('/').append(seg);
        }
        return sb.toString();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResult.error(status, message));
    }
}

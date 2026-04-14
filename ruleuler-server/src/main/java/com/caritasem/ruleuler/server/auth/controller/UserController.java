package com.caritasem.ruleuler.server.auth.controller;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import com.caritasem.ruleuler.server.auth.service.UserService;
import com.caritasem.ruleuler.server.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    @RequirePermission("api:GET:/api/rbac/users")
    public ApiResult listUsers(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Long roleId) {
        return ApiResult.ok(userService.listUsers(keyword, roleId));
    }

    @PostMapping
    @RequirePermission("api:POST:/api/rbac/users")
    public ApiResult createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Long id = userService.createUser(username, password);
        auditLogService.log("USER_CREATE", "USER", id, null, null,
                AuthContext.get().getUsername(), Map.of("username", username), null);
        return ApiResult.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @RequirePermission("api:PUT:/api/rbac/users")
    public ApiResult updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        userService.updateUser(id, username, password, status);
        auditLogService.log("USER_UPDATE", "USER", id, null, null,
                AuthContext.get().getUsername(), Map.of("username", username), null);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("api:DELETE:/api/rbac/users")
    public ApiResult deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        auditLogService.log("USER_DELETE", "USER", id, null, null,
                AuthContext.get().getUsername(), null, null);
        return ApiResult.ok(null);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}/roles")
    @RequirePermission("api:PUT:/api/rbac/users/roles")
    public ApiResult assignRoles(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Number> roleIdNumbers = (List<Number>) body.get("roleIds");
        List<Long> roleIds = roleIdNumbers != null
                ? roleIdNumbers.stream().map(Number::longValue).toList()
                : List.of();
        userService.assignRoles(id, roleIds);
        auditLogService.log("ROLE_ASSIGN", "USER", id, null, null,
                AuthContext.get().getUsername(), Map.of("roleIds", roleIds), null);
        return ApiResult.ok(null);
    }
}

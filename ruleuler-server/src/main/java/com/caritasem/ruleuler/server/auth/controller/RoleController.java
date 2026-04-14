package com.caritasem.ruleuler.server.auth.controller;

import com.caritasem.ruleuler.server.auth.ApiResult;
import com.caritasem.ruleuler.server.auth.AuthContext;
import com.caritasem.ruleuler.server.auth.RequirePermission;
import com.caritasem.ruleuler.server.auth.service.RoleService;
import com.caritasem.ruleuler.server.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rbac/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final AuditLogService auditLogService;

    @GetMapping
    @RequirePermission("api:GET:/api/rbac/roles")
    public ApiResult listRoles() {
        return ApiResult.ok(roleService.listRoles());
    }

    @PostMapping
    @RequirePermission("api:POST:/api/rbac/roles")
    public ApiResult createRole(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        Long id = roleService.createRole(name, description);
        auditLogService.log("ROLE_CREATE", "ROLE", id, null, null,
                AuthContext.get().getUsername(), Map.of("name", name), null);
        return ApiResult.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @RequirePermission("api:PUT:/api/rbac/roles")
    public ApiResult updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        roleService.updateRole(id, name, description);
        auditLogService.log("ROLE_UPDATE", "ROLE", id, null, null,
                AuthContext.get().getUsername(), Map.of("name", name), null);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("api:DELETE:/api/rbac/roles")
    public ApiResult deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        auditLogService.log("ROLE_DELETE", "ROLE", id, null, null,
                AuthContext.get().getUsername(), null, null);
        return ApiResult.ok(null);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}/permissions")
    @RequirePermission("api:PUT:/api/rbac/roles/permissions")
    public ApiResult assignPermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Number> permIdNumbers = (List<Number>) body.get("permissionIds");
        List<Long> permissionIds = permIdNumbers != null
                ? permIdNumbers.stream().map(Number::longValue).toList()
                : List.of();
        roleService.assignPermissions(id, permissionIds);
        auditLogService.log("PERM_ASSIGN", "ROLE", id, null, null,
                AuthContext.get().getUsername(), Map.of("permissionIds", permissionIds), null);
        return ApiResult.ok(null);
    }

    @GetMapping("/permissions")
    @RequirePermission("api:GET:/api/rbac/roles/permissions")
    public ApiResult listAllPermissions() {
        return ApiResult.ok(roleService.listAllPermissions());
    }
}

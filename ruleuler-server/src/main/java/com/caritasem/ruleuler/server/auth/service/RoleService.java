package com.caritasem.ruleuler.server.auth.service;

import com.caritasem.ruleuler.server.auth.dao.PermissionDao;
import com.caritasem.ruleuler.server.auth.dao.RoleDao;
import com.caritasem.ruleuler.server.auth.entity.Permission;
import com.caritasem.ruleuler.server.auth.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleDao roleDao;
    private final PermissionDao permissionDao;

    public record RoleVO(Long id, String name, String description, Integer builtIn,
                         int userCount, LocalDateTime createdAt) {}

    public List<RoleVO> listRoles() {
        return roleDao.findAll().stream()
                .map(r -> new RoleVO(r.getId(), r.getName(), r.getDescription(),
                        r.getBuiltIn(), roleDao.countUsersByRoleId(r.getId()), r.getCreatedAt()))
                .toList();
    }

    public Long createRole(String name, String description) {
        if (roleDao.findByName(name) != null) {
            throw new IllegalArgumentException("角色名已存在");
        }
        return roleDao.insert(Role.builder().name(name).description(description).builtIn(0).build());
    }

    public void updateRole(Long id, String name, String description) {
        Role role = roleDao.findById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        Role existing = roleDao.findByName(name);
        if (existing != null && !existing.getId().equals(id)) {
            throw new IllegalArgumentException("角色名已存在");
        }
        role.setName(name);
        role.setDescription(description);
        roleDao.update(role);
    }

    public void deleteRole(Long id) {
        Role role = roleDao.findById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            throw new IllegalArgumentException("内置角色不可删除");
        }
        if (roleDao.countUsersByRoleId(id) > 0) {
            throw new IllegalArgumentException("该角色仍有用户绑定，请先解绑");
        }
        roleDao.deleteById(id);
    }

    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        roleDao.assignPermissions(roleId, permissionIds);
    }

    public List<Permission> getPermissionsByRoleId(Long roleId) {
        return roleDao.findPermissionsByRoleId(roleId);
    }

    public List<Permission> listAllPermissions() {
        return permissionDao.findAll();
    }
}

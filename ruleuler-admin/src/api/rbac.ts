import request from './request';

// ---- Users ----

export function getUsers(params?: { keyword?: string; roleId?: number }) {
  return request.get('/api/rbac/users', { params });
}

export function createUser(data: { username: string; password: string; status?: number }) {
  return request.post('/api/rbac/users', data);
}

export function updateUser(id: number, data: { username?: string; password?: string; status?: number }) {
  return request.put(`/api/rbac/users/${id}`, data);
}

export function deleteUser(id: number) {
  return request.delete(`/api/rbac/users/${id}`);
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.put(`/api/rbac/users/${userId}/roles`, { roleIds });
}

// ---- Roles ----

export function getRoles() {
  return request.get('/api/rbac/roles');
}

export function createRole(data: { name: string; description?: string }) {
  return request.post('/api/rbac/roles', data);
}

export function updateRole(id: number, data: { name?: string; description?: string }) {
  return request.put(`/api/rbac/roles/${id}`, data);
}

export function deleteRole(id: number) {
  return request.delete(`/api/rbac/roles/${id}`);
}

export function assignRolePermissions(roleId: number, permissionIds: number[]) {
  return request.put(`/api/rbac/roles/${roleId}/permissions`, { permissionIds });
}

// ---- Permissions ----

export function getPermissions() {
  return request.get('/api/rbac/roles/permissions');
}

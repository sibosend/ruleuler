# RBAC 权限管理

## 概述

系统采用 RBAC（Role-Based Access Control）模型实现权限控制，支持菜单级和 API 操作级两层鉴权。

模型关系：**用户 → 角色 → 权限**（多对多）。

---

## 数据模型

### 用户（rbac_user）

| 字段 | 说明 |
|------|------|
| username | 登录名，唯一 |
| password_hash | BCrypt 加密密码 |
| status | 1=启用，0=禁用 |
| built_in | 1=内置用户，不可删除 |

默认内置用户：`admin`，密码 `asdfg@1234`。

### 角色（rbac_role）

| 字段 | 说明 |
|------|------|
| name | 角色名，唯一 |
| description | 角色描述 |
| built_in | 1=内置角色，不可删除 |

默认内置角色：`admin`（超级管理员，拥有所有权限）。

### 权限（rbac_permission）

| 字段 | 说明 |
|------|------|
| permission_code | 权限码，唯一标识 |
| name | 权限名称（中文） |
| type | `menu`（菜单权限）或 `api`（接口权限） |
| parent_id | 父权限 ID，支持树形结构 |

---

## 权限码规范

### 菜单权限

格式：`menu:{模块}`，控制前端菜单可见性。

| 权限码 | 说明 |
|--------|------|
| `menu:dashboard` | 仪表盘 |
| `menu:projects` | 项目管理 |
| `menu:console` | 规则编辑器 |
| `menu:system` | 系统设置 |
| `menu:system:users` | 用户管理 |
| `menu:system:roles` | 角色管理 |
| `menu:monitoring` | 变量监控 |
| `menu:approvals` | 审批管理 |

### API 权限

格式：`api:{HTTP方法}:{路径}`，控制后端接口访问。

| 权限码 | 说明 |
|--------|------|
| `api:GET:/api/rbac/users` | 查看用户列表 |
| `api:POST:/api/rbac/users` | 创建用户 |
| `api:PUT:/api/rbac/users` | 编辑用户 |
| `api:DELETE:/api/rbac/users` | 删除用户 |
| `api:PUT:/api/rbac/users/roles` | 分配用户角色 |
| `api:GET:/api/rbac/roles` | 查看角色列表 |
| `api:POST:/api/rbac/roles` | 创建角色 |
| `api:PUT:/api/rbac/roles` | 编辑角色 |
| `api:DELETE:/api/rbac/roles` | 删除角色 |
| `api:PUT:/api/rbac/roles/permissions` | 分配角色权限 |
| `api:GET:/api/rbac/roles/permissions` | 查看权限列表 |
| `api:GET:/api/monitoring/*` | 监控数据查询 |
| `pack:publish:submit` | 提交发布审批 |
| `pack:publish:approve` | 审批发布 |

---

## 鉴权机制

### 1. JWT 认证（JwtAuthFilter）

所有 `/api/**` 请求需携带 JWT Token：

- Header：`Authorization: Bearer {token}`
- 或 Cookie：`ruleuler_token={token}`

白名单路径（无需认证）：
- `/api/auth/login`
- `/api/auth/register`
- 静态资源路径

Token 中包含：`userId`、`username`、`roles`。

### 2. RBAC 过滤器（RbacPermissionFilter）

拦截 `/api/rbac/**` 路径的请求：

1. `admin` 角色直接放行
2. 拥有 `*` 通配符权限直接放行
3. 拼接权限码 `api:{METHOD}:{normalizedPath}`，与用户权限列表匹配
4. 路径规范化：去掉数字 ID 段（`/api/rbac/users/123` → `/api/rbac/users`）

### 3. 注解鉴权（@RequirePermission）

用于非 `/api/rbac/` 路径的接口，通过 AOP 切面校验：

```java
@RequirePermission("pack:publish:submit")
public ApiResult submit(...) { ... }
```

校验逻辑：
1. 检查用户是否已认证
2. 拥有 `*` 通配符权限直接放行
3. 匹配具体权限码

### 4. 前端菜单控制

前端通过 `usePermission` hook 判断菜单可见性：

```tsx
const canMonitoring = usePermission('menu:monitoring');
const canApproval = usePermission('menu:approvals');
```

用户登录后，Token 解析出角色列表，前端据此控制菜单渲染和操作按钮显示。

---

## 操作流程

### 创建角色并分配权限

1. 系统设置 → 角色管理 → 新建角色
2. 选择角色 → 分配权限（勾选菜单权限和 API 权限）
3. 保存

### 创建用户并分配角色

1. 系统设置 → 用户管理 → 新建用户
2. 填写用户名和密码
3. 选择用户 → 分配角色（可多选）
4. 保存

### 典型角色配置示例

**规则开发者**：
- `menu:dashboard`、`menu:projects`、`menu:console`
- `pack:publish:submit`（可提交发布）

**审批人**：
- `menu:dashboard`、`menu:approvals`
- `pack:publish:approve`（可审批）

**运维人员**：
- `menu:dashboard`、`menu:monitoring`
- `api:GET:/api/monitoring/*`（可查看监控数据）

**管理员**：
- 使用内置 `admin` 角色，自动拥有所有权限

---

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rbac/users` | 用户列表（支持 keyword/roleId 筛选） |
| POST | `/api/rbac/users` | 创建用户 |
| PUT | `/api/rbac/users/{id}` | 编辑用户 |
| DELETE | `/api/rbac/users/{id}` | 删除用户 |
| PUT | `/api/rbac/users/{id}/roles` | 分配用户角色 |
| GET | `/api/rbac/roles` | 角色列表 |
| POST | `/api/rbac/roles` | 创建角色 |
| PUT | `/api/rbac/roles/{id}` | 编辑角色 |
| DELETE | `/api/rbac/roles/{id}` | 删除角色 |
| PUT | `/api/rbac/roles/{id}/permissions` | 分配角色权限 |
| GET | `/api/rbac/roles/permissions` | 全部权限列表 |

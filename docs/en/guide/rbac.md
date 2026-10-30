# RBAC Permission Management

## Overview

RulEuler uses Role-Based Access Control (RBAC) to enforce two-tier authorization across frontend menus and backend REST APIs.

Model relationship: **User → Role → Permission** (many-to-many).

---

## Data Model

### User (`rbac_user`)

| Field | Description |
|-------|-------------|
| `username` | Unique login username |
| `password_hash` | BCrypt password hash |
| `status` | `1` = Enabled, `0` = Disabled |
| `built_in` | `1` = Built-in user (cannot be deleted) |

Default built-in user: `admin` / `asdfg@1234`.

### Role (`rbac_role`)

| Field | Description |
|-------|-------------|
| `name` | Unique role name |
| `description` | Role description |
| `built_in` | `1` = Built-in role (cannot be deleted) |

Default built-in role: `admin` (super administrator with all permissions).

### Permission (`rbac_permission`)

| Field | Description |
|-------|-------------|
| `permission_code` | Unique permission identifier |
| `name` | Display name |
| `type` | `menu` (frontend menu visibility) or `api` (backend operation access) |
| `parent_id` | Parent permission ID for hierarchical grouping |

---

## Permission Code Conventions

### Menu Permissions

Format: `menu:{module}`, controlling frontend navigation visibility.

| Permission Code | Description |
|-----------------|-------------|
| `menu:dashboard` | Dashboard |
| `menu:projects` | Project Management |
| `menu:console` | Rule Editor |
| `menu:system` | System Settings |
| `menu:system:users` | User Management |
| `menu:system:roles` | Role Management |
| `menu:system:audit` | Audit Logs |
| `menu:monitoring` | Variable Monitoring |
| `menu:approvals` | Release Approvals |

### API & Action Permissions

| Permission Code | Description |
|-----------------|-------------|
| `api:GET:/api/rbac/users` | View user list |
| `api:POST:/api/rbac/users` | Create user |
| `api:PUT:/api/rbac/users` | Edit user |
| `api:DELETE:/api/rbac/users` | Delete user |
| `api:PUT:/api/rbac/users/roles` | Assign user roles |
| `api:GET:/api/rbac/roles` | View role list |
| `api:POST:/api/rbac/roles` | Create role |
| `api:PUT:/api/rbac/roles` | Edit role |
| `api:DELETE:/api/rbac/roles` | Delete role |
| `api:PUT:/api/rbac/roles/permissions` | Assign role permissions |
| `api:GET:/api/rbac/roles/permissions` | View permission list |
| `api:GET:/api/monitoring/*` | Query monitoring data |
| `pack:publish:submit` | Submit & publish release requests |
| `pack:publish:approve` | Approve or reject release requests |
| `project:function:upload` | Upload custom function JARs |
| `project:function:view` | View custom functions |

---

## Authorization Mechanism

### 1. JWT Authentication (`JwtAuthFilter`)

All `/api/**` requests require a valid JWT token via:

- Header: `Authorization: Bearer {token}`
- Or Cookie: `ruleuler_token={token}`

Whitelisted public paths: `/api/auth/login`, `/api/auth/register`, and static assets.

### 2. RBAC Filter (`RbacPermissionFilter`)

Intercepts `/api/rbac/**` requests:

1. Users with the `admin` role or `*` wildcard permission are granted access immediately
2. Normalizes numeric ID segments (`/api/rbac/users/123` → `/api/rbac/users`) and matches `api:{METHOD}:{normalizedPath}` against the user's permission set

### 3. Annotation Guard (`@RequirePermission`)

Protects non-`/api/rbac/` endpoints via AOP:

```java
@RequirePermission("pack:publish:submit")
public ApiResult submit(...) { ... }
```

### 4. Frontend Menu Control

The frontend checks permissions via the `usePermission` hook:

```tsx
const canMonitoring = usePermission('menu:monitoring');
const canApproval = usePermission('menu:approvals');
```

---

## API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/rbac/users` | List users (filter by `keyword`/`roleId`) |
| `POST` | `/api/rbac/users` | Create user |
| `PUT` | `/api/rbac/users/{id}` | Update user |
| `DELETE` | `/api/rbac/users/{id}` | Delete user |
| `PUT` | `/api/rbac/users/{id}/roles` | Assign roles to user |
| `GET` | `/api/rbac/roles` | List roles |
| `POST` | `/api/rbac/roles` | Create role |
| `PUT` | `/api/rbac/roles/{id}` | Update role |
| `DELETE` | `/api/rbac/roles/{id}` | Delete role |
| `PUT` | `/api/rbac/roles/{id}/permissions` | Assign permissions to role |
| `GET` | `/api/rbac/roles/permissions` | List all permissions |


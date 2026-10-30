# Audit Log

## Overview

The **Audit Log** module records key administrative operations across the system to support security auditing, troubleshooting, and compliance verification — tracking **who** performed **what action** on **which resource** at **what time**.

---

## Covered Operations

Audit logs are automatically recorded for:

1. **User & Role Management**:
   - Creating, updating, and deleting users
   - Creating, updating, and deleting roles
   - Assigning roles and permissions

2. **Resource & File Management**:
   - Creating, saving, modifying, and deleting decision components (decision tables, rule sets, decision trees, etc.), variable libraries, parameter libraries, and custom function JARs

3. **Release & Approval Workflow**:
   - Submitting release approval requests
   - Approving or rejecting release requests
   - Publishing knowledge packages and configuring gray release rules

---

## Viewing Audit Logs

Navigate to **System Settings → Audit Logs** in the Admin Console (requires the `menu:system:audit` permission) to view audit records in reverse chronological order.

Each audit entry captures:

- **Operator**: Username of the actor
- **Action**: Operation type (e.g., `CREATE`, `UPDATE`, `DELETE`, `APPROVE`, `PUBLISH`)
- **Target Resource**: Target type, ID, and resource path (e.g., knowledge package name, file path, username)
- **Detail**: Structured JSON details of the change (e.g., approval comments, modified fields)
- **Timestamp & IP**: Exact execution timestamp and client IP address

Supports filtering by project, operator, action type, and time range.

---

## Storage Design

Audit records are persisted in the `ruleuler_audit_log` MySQL table via `AuditLogService` and non-intrusive controller aspects without blocking core request processing.


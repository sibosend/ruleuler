# Storage Modes

RulEuler supports two storage backends for rule files:

| Mode | Resource Prefix | Description |
|------|-----------------|-------------|
| `db` | `dbr:` | Stored in the `ruleuler_rule_file` relational table; supports nested subfolders |
| `jcr` | No prefix | Stored in an Apache Jackrabbit JCR repository |

## Recommendation

`db` mode is recommended:

- All rule files and version histories are stored directly in MySQL for straightforward backup and migration
- Supports organizing rule files into nested subfolders
- Shares the same database as RBAC, release approvals, and automated testing

## Important Notes

!!! warning
    The storage mode is selected when creating a project and cannot be changed afterward. Do not mix `db` and `jcr` resource paths within the same project.


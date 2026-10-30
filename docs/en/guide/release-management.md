# Release Management

## Overview

The Release Management module provides an approval workflow for publishing knowledge packages so that rule changes go through automated testing and human review before going live.

Core workflow: **Submit → Auto Test → Review → Publish**.

---

## Approval Workflow

```
Submit Release Request
    │
    ├─ Has Test Case Pack ──→ TESTING (Async Auto Test) ──→ PENDING (Awaiting Review)
    │
    └─ No Test Case Pack  ──→ PENDING (Awaiting Review)
                                  │
                        ┌─────────┼─────────┐
                        ↓                   ↓
                   APPROVED            REJECTED
              (Ready to Publish)       (Rejected)
                        │
                        ↓
                 Execute Publish
                        │
                  ┌─────┼─────┐
                  ↓           ↓
              PUBLISHED   PUBLISH_FAILED
              (Live)      (Failed, Retryable)
```

### Status Definitions

| Status | Description |
|--------|-------------|
| `TESTING` | Automated test suite is running; transitions to `PENDING` upon completion |
| `PENDING` | Awaiting reviewer approval |
| `APPROVED` | Approved and ready to publish |
| `REJECTED` | Rejected by reviewer |
| `PUBLISHED` | Live in production |
| `PUBLISH_FAILED` | Publish failed; can be retried |

---

## Submitting a Release

Required permission: `pack:publish:submit`

When a release request is submitted, the system automatically performs:

1. **Uniqueness Check**: Only one active `PENDING` approval request is allowed per knowledge package at a time
2. **Automatic Versioning**: Every save of a decision component creates an immutable version record (`V1`, `V2`, `V3`...) in `ruleuler_rule_file_version`
3. **Version Locking**: Traverses the decision flow XML and replaces all `version="LATEST"` references with the exact current version number (e.g., `V3`)
4. **Content Snapshot**: Saves the version-locked XML content of all decision resources in the project into `ruleuler_publish_snapshot`
5. **Content-Level Diff**: Compares the current snapshot against the previous published snapshot to compute component-, row-, and field-level diffs
6. **Automated Testing**: If the knowledge package has an associated test case pack, triggers an asynchronous test run

### Version Locking Mechanism

Decision flows reference child rule files via `file` + `version` attributes. In the editor, `version="LATEST"` is used by default.

Without version locking, edits made after submitting an approval request would silently slip into the built knowledge package when published.

Version locking pipeline:

```
Save aircraft_gate_map.dt.xml in editor
    → Automatically creates version V5 in ruleuler_rule_file_version

Submit Approval
    → Reads decision flow XML: file="dbr:/xxx/aircraft_gate_map.dt.xml" version="LATEST"
    → Queries latest version of that resource: V5
    → Rewrites reference to: file="dbr:/xxx/aircraft_gate_map.dt.xml" version="V5"
    → Saves locked XML into ruleuler_publish_snapshot

Publish
    → Builds knowledge package from the locked snapshot
    → Decision flow references version="V5"
    → DatabaseResourceProvider reads V5 from ruleuler_rule_file_version
    → Even if the file was later edited to V6, the published package strictly runs V5
```

### Content-Level Diff

Diffs are computed directly from XML content rather than version numbers across three levels of granularity:

- **Component Level**: Identifies `ADDED`, `MODIFIED`, and `DELETED` decision components
- **Rule Row Level**: For `MODIFIED` components, parses individual rules by resource type:
  - Decision Table (`.dt.xml`): Grouped by cell `row` attribute
  - Rule Set (`.rs.xml`): Matched by rule `name` attribute
  - REA Rule (`.rea.xml`): Matched by `rea-item` `name` attribute
  - Decision Flow (`.rl.xml`): Matched by node `name` attribute
  - Decision Tree (`.dtree.xml`): Matched by node `var` / `var-label` attribute
- **Field Level**: Extracts exact condition, assignment, and cell changes (e.g., `arrival_time >= : 6.8 → 6.9`)

---

## Review

Required permission: `pack:publish:approve`

Reviewers can:

- **Approve**: Transitions status to `APPROVED` with an optional comment (≤500 characters)
- **Reject**: Transitions status to `REJECTED` with a rejection reason

The approval detail page displays basic metadata, submitter change notes, automated test summary, and expandable component/row/field diffs.

---

## Publishing

Required permission: `pack:publish:submit`

Only requests in `APPROVED` or `PUBLISH_FAILED` status can be published.

Publishing builds the knowledge package from the locked snapshot, pushes it to `ruleuler-client`, and records the snapshot as the new baseline for future diffs.

---

## Gray Release / A/B Testing

In addition to full rollouts, RulEuler supports Gray Release (A/B Testing) to split live traffic between the baseline (`BASE`) and gray (`GRAY`) versions of a knowledge package.

### Routing Strategies

1. **`PERCENTAGE` (Percentage Split)**:
   - Routes a configured percentage of traffic (e.g., 10% to `GRAY`, 90% to `BASE`).
2. **`CONDITION` (Condition Expression)**:
   - Routes requests matching an REA condition expression (e.g., `FlightInfo.is_international == TRUE` or `user_type == "VIP"`) to the `GRAY` version.

### Gray Lifecycle

While a gray rule is active (`ACTIVE`), operators can monitor hit/success/fail counts and average latency for `BASE` vs. `GRAY`, then either:

- **Full Rollout (`ROLLED_OUT`)**: Promote the gray version to become the primary baseline version
- **Rollback (`ROLLED_BACK`)**: Deactivate the gray routing rule and revert all traffic to the baseline version

---

## UI Views

| View | Route | Description |
|------|-------|-------------|
| Pending Review | `/releases/pending` | Reviewer view showing `PENDING` requests |
| Ready to Publish | `/releases/pending-publish` | Shows `APPROVED` requests |
| My Requests | `/releases/my` | Requests submitted by the current user |
| All Requests | `/releases/all` | All approval requests with status filtering |

---

## Database Tables

| Table | Description |
|-------|-------------|
| `ruleuler_publish_approval` | Primary approval request table |
| `ruleuler_publish_approval_diff` | Component-level diff records with rule-level JSON `details` |
| `ruleuler_publish_snapshot` | Locked XML snapshots (`path → xmlContent` JSON map) |
| `ruleuler_grayscale_rule` | Gray release routing rules |
| `ruleuler_grayscale_metrics` | Daily execution metrics per version (`BASE` / `GRAY`) |

---

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/approvals` | Submit a release approval request |
| `GET` | `/api/approvals` | List approval requests (filter by `project`/`status`/`submitter`) |
| `GET` | `/api/approvals/{id}` | Get approval details (including diffs and test results) |
| `PUT` | `/api/approvals/{id}/approve` | Approve a request |
| `PUT` | `/api/approvals/{id}/reject` | Reject a request |
| `PUT` | `/api/approvals/{id}/publish` | Publish an approved request |
| `POST` | `/api/approvals/{id}/recalc-diff` | Recalculate diff for an approval request |


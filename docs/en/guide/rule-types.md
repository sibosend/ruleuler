# Rule Types

## Rule Set

A Rule Set (`.rs.xml`) is the foundational rule type, containing multiple `if-then-else` rules.

**Use case**: Straightforward conditional checks and variable assignments.

```
IF FlightInfo.passenger_count > 200 AND FlightInfo.is_international
THEN GateResult.gate_type = "Contact Gate"; GateResult.reason = "International flight priority"
```

Supports two editing modes:

- **Wizard Mode** — Configure conditions and actions through form selectors
- **REA Expression Editor** — Write rules directly in concise text syntax (see [REA Expressions](rea-expression.md))

## Decision Table

A Decision Table (`.dt.xml`) organizes rules as a matrix where rows represent condition combinations and columns represent conditions and output actions.

**Use case**: Multi-condition combinations that benefit from side-by-side tabular comparison.

| Flight Type | Passenger Count | Gate Type | Reason |
|-------------|-----------------|-----------|--------|
| International | > 200 | Contact Gate | High-volume international flight |
| International | ≤ 200 | Contact Gate | International flight priority |
| Domestic | > 300 | Contact Gate | Ultra-high passenger volume |
| Domestic | ≤ 300 | Remote Stand | Standard domestic flight |

## Decision Tree

A Decision Tree (`.dtree.xml`) organizes conditional branches in a tree hierarchy.

**Use case**: Conditions with a clear hierarchical order, evaluating broad categories before finer subdivisions.

## Decision Flow

A Decision Flow (`.rl.xml`) orchestrates multiple rule nodes using a visual flowchart.

**Use case**: Complex business workflows requiring multiple rule components to execute sequentially or along conditional branches.

Decision flows are the primary entry point for external API invocations via `/process/{project}/{packageId}/{flowId}`.

## Scorecard

A Scorecard (`.sc`) is used for scoring models where each condition contributes a weighted score toward a final aggregated total.

**Use case**: Credit scoring, risk assessment, and priority ranking.

## Script Decision Table

A Script Decision Table (`.dts.xml`) extends decision tables with script expressions for custom calculation logic.


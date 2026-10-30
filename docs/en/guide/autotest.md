# Auto Testing

RulEuler includes a built-in automated testing system that generates test cases for knowledge packages and executes verification runs.

## How It Works

Auto testing parses condition expressions across all nodes in a decision flow DAG and generates test data using path coverage + MC/DC strategies:

1. Parses conditions across all nodes in the decision flow (`DecisionNode`, `RuleNode`, `ForkNode`)
2. Traverses all `start → end` paths in the DAG
3. Generates **HIT cases** (satisfying all path conditions) and **MISS cases** (flipping conditions one by one) for each path
4. Executes the test cases and records outputs and conflicts

## Testing Tiers

| Tier | Name | Description |
|------|------|-------------|
| L0 | Static Conflict Detection | Detects overlapping rows in decision tables, overlapping conditions in rule sets, and incomplete branches in decision trees |
| L1 | Condition Coverage Testing | Parses all rule types (rule sets, decision tables, decision trees, scorecards) to generate coverage cases |
| L2 | Output Distribution Regression | Records output distributions and compares changes across subsequent test runs |

## Usage

In the Admin Console, navigate to **Project → Auto Testing**:

![Auto Testing Report](../assets/images/test.png)

1. Click **Generate Test Cases** to analyze the knowledge package and create a test case pack
2. Click **Run** to execute the test suite
3. View the test report, including pass rate, static conflicts, output distributions, and regression diffs

## Test Case Packs

Test cases are bundled into immutable **Test Case Packs**, supporting:

- Automatic generation (via rule static analysis)
- Manual import (CSV / JSON formats)
- Repeated regression runs against the same test case pack

## Regression Testing

The first run is automatically marked as the `baseline`. Subsequent runs compare outputs against the baseline:

- Identical output → `SAME`
- Modified output → `CHANGED` (highlighting changed fields)
- Distribution shift percentage calculated per output variable

Re-running tests after modifying rules immediately reveals which outputs changed.

## API

| Endpoint | Description |
|----------|-------------|
| `POST /api/autotest/generate` | Generate a test case pack |
| `POST /api/autotest/run` | Execute a test run |
| `GET /api/autotest/run-status?runId=X` | Query test run progress |
| `GET /api/autotest/report?runId=X` | Retrieve the test report |


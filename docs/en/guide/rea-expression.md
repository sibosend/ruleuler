# REA Expression Syntax

REA (Rule Expression Assistant) is RulEuler's text-based rule editor that compiles human-readable expressions into executable rule XML.

System keywords and operators are all uppercase. Variable names, parameter names, and string literals preserve their exact case as defined in libraries.

## Expression Structure

Each rule consists of three clauses:

| Clause | Meaning | Syntax |
|--------|---------|--------|
| When (`如果`) | Condition | `variable OPERATOR value [AND/OR ...]`, supports value functions |
| Then (`那么`) | Matched actions | `variable = value` or action functions, separated by `;` |
| Else (`否则`) | Unmatched actions | Same as Then |

## Reference Format

| Type | Format | Example |
|------|--------|---------|
| Variable | `Category.fieldName` | `FlightInfo.arrival_time` |
| Parameter | Bare parameter name | `can_score` |

Property names may coincide with reserved words (`FlightInfo.WEEK` and `Order.MONTH` are valid). Category names and parameter names cannot use reserved namespace identifiers (`WEEK.foo` or bare `DATE` is invalid).

## Operators

| Operator | Meaning | Applicable Types |
|----------|---------|------------------|
| `==` | Equal to | All |
| `!=` | Not equal to | All |
| `>` `>=` `<` `<=` | Numeric / date comparison | Number, Date |
| `CONTAIN` / `NOTCONTAIN` | Contains / does not contain substring | String |
| `IN` / `NOTIN` | In / not in list | All |
| `MATCH` / `NOTMATCH` | Regex match / non-match | String |
| `STARTWITH` / `ENDWITH` | Prefix / suffix match | String |
| `EQUALSIGNORECASE` | Case-insensitive equality | String |

Operators must be uppercase. `Contain`, `In`, or `and` will fail validation.

## Value Types

| Type | Syntax | Example |
|------|--------|---------|
| String | Double-quoted | `"International"` |
| Number | Numeric literal | `5`, `-3.14` |
| Boolean | `TRUE` / `FALSE` | `TRUE` |
| Variable reference | `Category.fieldName` | `FlightInfo.gate_id` |
| Parameter reference | Bare parameter name | `threshold` |
| List | Comma-separated in parentheses | `("A", "B", "C")` |
| Function call | See below | `TRIM(FlightInfo.name)` |
| Arithmetic | `+ - * / %` with spaces around operators | `FlightInfo.score + 10` |

## Boolean Type

Boolean variables and boolean-returning functions support both implicit and explicit forms:

In conditions:

```
# Implicit — equivalent to == TRUE
FlightInfo.is_international
LISTEMPTY(FlightInfo.tags)

# Explicit
FlightInfo.is_international == TRUE
FlightInfo.is_international == FALSE
```

In assignments:

```
FlightInfo.is_international = TRUE
```

!!! warning
    Boolean literals must be uppercase `TRUE` / `FALSE`. `true` or `True` will report a syntax error.

## Null Checks: `ISNULL` / `ISNOTNULL`

Null predicates in conditions (aligned with `LISTEMPTY`). They compile to the engine's native null / not-null assertors rather than functions, so they cannot be assigned or compared with `==`.

```
ISNULL(FlightInfo.gate)
ISNOTNULL(FlightInfo.gate)
ISNULL(FlightInfo.gate) AND FlightInfo.score > 0
ISNULL(TRIM(FlightInfo.name))
ISNULL(FlightInfo.score) OR FlightInfo.score == -999
```

Invalid forms: `FlightInfo.gate NULL` (no postfix syntax), `ISNULL(x) == TRUE`, `flag = ISNULL(x)`, `ISNULL(1)`, `ISNULL(score + 10)`.

For sentinel default values (e.g., `-999` representing missing data), use `== -999`.

!!! warning "Empty String Behavior in `ISNULL` Between Engine and Gray Evaluator"
    For `ISNULL("")`: the core engine's `NullAssertor` treats blank strings as null (`isBlank` → **true**), whereas the gray release `ConditionEvaluator` checks strict `== null` (**false**). Missing fields and `null` evaluate to **true** in both paths.

### Gray Release SDK Compatibility

`condition_expr` is evaluated locally on `ruleuler-client`. Legacy postfix `x NULL` / `x Null` remains supported in the gray evaluator, while new `ISNULL(...)` or arithmetic expressions require an up-to-date `ruleuler-client`.

## Arithmetic: `+ - * / %`

On the left side of a comparison, only "one atom + numeric constants" is allowed: `FlightInfo.score + 10 > 80`. Adding two variables on the left side is not allowed — assign to a parameter first or place the expression on the right side. Numeric literals cannot appear on the left side of a comparison.

Full arithmetic expressions are supported on the right side of assignments, the right side of comparisons, and inside function arguments:

```
can_score = FlightInfo.score + 10
can_score = FlightInfo.amount * 1.1
can_score = ABS(FlightInfo.score) + 10
can_score = 1.1 * (FlightInfo.score + 10)
ABS(FlightInfo.score + 10) > 5
```

Operator precedence: `* / %` bind tighter than `+ -` (MVEL rules). Parentheses may only appear on the **right** side of an arithmetic operator: write `1.1 * (score + 10)` instead of `(score + 10) * 1.1`.

Binary arithmetic operators require spaces on both sides: `score - 10` is valid; `score-10` and `score -10` are invalid.

Comparing two compound arithmetic expressions (`score + a > bonus + b`) is invalid — assign one side to a parameter first.

!!! warning "No Numeric Literal on the Left of a Comparison"
    `80 < score + bonus` is invalid in both full and gray execution because the engine's left-hand slot does not accept literals. Use a variable or parameter on the left side.

## Functions

REA exposes a unified function call syntax that compiles down to native engine `Method` / `CommonFunction` nodes without altering runtime execution.

Value functions can be used on either side of a condition or on the right side of an assignment. Action functions (`void`) can only be used as statements in Then / Else blocks (`LISTSORT` works in both contexts).

```
# Flat call (recommended)
TRIM(FlightInfo.name) == ""
LISTEMPTY(FlightInfo.tags) AND MAPHAS(FlightInfo.extra, "k")
ABS(FlightInfo.score) >= 10
NOW()

# Namespaced call (for autocomplete grouping / disambiguation)
STRING.TRIM(FlightInfo.name)
MATH.ABS(FlightInfo.score)
DATE.NOW()
LIST.SIZE(FlightInfo.tags)

# Collection aggregation (second argument is a bare property name)
COUNT(Order.items) > 0
SUM(Order.items, amount) > 1000

# Assignments
gate_type = TRIM(FlightInfo.gate_id)
can_score = ABS(MIN(FlightInfo.score, 100))

# Then / Else action functions (void return)
LISTADD(FlightInfo.tags, "INTL"); risk_level = "high"
MAPPUT(FlightInfo.extra, "level", "VIP")
```

Invalid examples:

```
LISTADD(FlightInfo.tags, "VIP") == TRUE      # Action functions cannot be used in conditions
can_score = LISTADD(FlightInfo.tags, "VIP")  # Action functions do not return a value
TRIM(FlightInfo.name)                        # Value functions must be assigned, not standalone statements
listAdd(xs, "VIP")                           # Function names must be uppercase
SUM(Order.items.amount)                      # Second argument is a bare property name, not a dotted path
```

Namespace prefixes `STRING`, `MATH`, `DATE`, `LIST`, and `MAP` are reserved (case-insensitive) and cannot be used as category or parameter names.

### String Functions → `urule.stringAction`

`TRIM` `SUBSTRING` `SUBSTRINGFROM` `SUBSTRINGTO` `LOWER` `UPPER` `LENGTH` `CHARAT` `INDEXOF` `LASTINDEXOF` `REPLACE` `SPLIT`

### Math Functions → `urule.mathAction`

`ABS` `MAX` `MIN` (two scalars, not collections) `SIN` `COS` `TAN` `COT` `LN` `LOG10` `ROUND`

### Date Functions → `urule.dateAction`

`NOW` `PARSEDATE` `FORMATDATE` `ADDYEARS` / `ADDMONTHS` / `ADDDAYS` / `ADDHOURS` / `ADDMINUTES` / `ADDSECONDS` and corresponding `SUB*`. `YEAR` `MONTH` `WEEK` `DAY` `HOUR` `MINUTE` `SECOND`. `DIFFMILLIS` / `DIFFSECONDS` / `DIFFMINUTES` / `DIFFHOURS` / `DIFFDAYS` / `DIFFWEEKS` / `DIFFMONTHS`.

!!! warning "Engine Date Quirks"
    - `MONTH` returns **0–11** (`0` = January, so `MONTH(NOW()) == 9` is October)
    - `WEEK` returns `Calendar.DAY_OF_WEEK`: **1 = Sunday … 7 = Saturday**
    - `DAY` maps to the engine's `getay` method

### List / Map Functions

Value functions: `LISTSIZE` `LISTMAX` `LISTMIN` `LISTCONTAINS` `LISTEMPTY` `LISTSORT` `LISTRETRIVE` `MAPGET` `MAPSIZE` `MAPHAS`

Action functions (Then / Else only): `LISTADD` `LISTREMOVE` `MAPPUT` `MAPREMOVE`

For `LISTSORT` / `LISTRETRIVE`, pass the property name as a bare identifier or string literal: `LISTSORT(Order.items, amount, "正序")`. Note that ascending sort in the core engine only recognizes `"1"`, `"true"`, or `"正序"`; any other string (including `"asc"`) sorts in descending order.

### Collection Aggregation → `CommonFunction`

`COUNT(xs)` takes a collection. `SUM`, `AVG`, `MAXOF`, and `MINOF` take a collection and a bare property name: `SUM(Order.items, amount)`.

### Custom Functions (Project Action Library)

Spring Bean methods registered in an action library (`.al.xml`) use the same `BEAN.METHOD(...)` uppercase syntax in REA (matched case-insensitively against the action library):

```
RISKSERVICE.SCORE(FlightInfo.airline, 10)
```

For packaging and uploading custom function JARs, see [Custom Functions](rea-function-extension.md).

## Condition Expression Examples

```
FlightInfo.arrival_time > 5
FlightInfo.arrival_time > 5 AND FlightInfo.is_international
FlightInfo.airline IN ("CA", "MU", "CZ")
FlightInfo.arrival_time > 5 AND (FlightInfo.flight_type == "Domestic" OR FlightInfo.flight_type == "International")
ABS(FlightInfo.score) >= 10 AND LISTCONTAINS(FlightInfo.tags, "VIP")
ISNULL(FlightInfo.gate) OR FlightInfo.score + 10 > 80
threshold < FlightInfo.score + FlightInfo.bonus
```

!!! note
    Mixing `AND` and `OR` at the same nesting level is not allowed; use parentheses to group them explicitly.

## Action Expression Examples

```
can_score = 5
can_score = 5; risk_level = "high"
can_score = ABS(FlightInfo.score)
can_score = FlightInfo.amount * 1.1
can_score = 1.1 * (FlightInfo.score + 10)
LISTADD(FlightInfo.tags, "INTL"); risk_level = "high"
```

## Autocomplete

- Without `.`: suggests variable categories, parameters, operators, `AND`/`OR`, `TRUE`/`FALSE`, `ISNULL()`, and functions
- After `.`: suggests fields within the selected variable category
- After `STRING.` / `MATH.` / `DATE.` / `LIST.` / `MAP.` or custom Bean prefixes: suggests methods in that namespace
- Selecting a function inserts `FUNC()` with the cursor placed inside the parentheses
- Action functions such as `LISTADD` are filtered out in condition clauses


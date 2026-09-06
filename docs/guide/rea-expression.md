# REA 表达式语法

REA（Rule Expression Assistant）是 RulEuler 的规则文本编辑器，将人类可读的表达式编译为引擎可执行格式。

系统符号全大写。变量 / 参数 / 字符串字面量照库原样，不大写。

## 表达式结构

规则由三部分组成：

| 区域 | 含义 | 语法 |
|------|------|------|
| 如果 | 条件 | `变量 操作符 值 [AND/OR ...]`，可用值函数 |
| 那么 | 命中动作 | `变量 = 值` 或动作函数，分号分隔 |
| 否则 | 未命中动作 | 同那么 |

## 引用格式

| 类型 | 格式 | 示例 |
|------|------|------|
| 变量 | `类别.变量名` | `FlightInfo.arrival_time` |
| 参数 | 直接写参数名 | `can_score` |

属性名可以是保留字：`FlightInfo.WEEK`、`Order.MONTH` 合法。类别名和参数名不行：`WEEK.foo`、裸 `DATE` 非法。

## 操作符

| 文本 | 含义 | 适用类型 |
|------|------|----------|
| `==` | 等于 | 所有 |
| `!=` | 不等于 | 所有 |
| `>` `>=` `<` `<=` | 比较 | 数值、日期 |
| `CONTAIN` / `NOTCONTAIN` | 包含 | 字符串 |
| `IN` / `NOTIN` | 在列表中 | 所有 |
| `MATCH` / `NOTMATCH` | 正则匹配 | 字符串 |
| `STARTWITH` / `ENDWITH` | 前缀/后缀 | 字符串 |
| `EQUALSIGNORECASE` | 忽略大小写相等 | 字符串 |

只认全大写。`Contain`、`In`、`and` 会报错。

## 值类型

| 类型 | 写法 | 示例 |
|------|------|------|
| 字符串 | 双引号包裹 | `"国际"` |
| 数字 | 直接写 | `5`、`-3.14` |
| 布尔 | `TRUE` / `FALSE` | `TRUE` |
| 变量引用 | `类别.变量名` | `FlightInfo.gate_id` |
| 参数引用 | 裸参数名 | `threshold` |
| 列表 | 圆括号逗号分隔 | `("A", "B", "C")` |
| 函数 | 见下方 | `TRIM(FlightInfo.name)` |
| 运算 | `+ - * / %`，两侧空格 | `FlightInfo.score + 10` |

## Boolean 类型

Boolean 变量和返回 Boolean 的函数支持隐式和显式两种写法：

条件区：

```
# 隐式 — 等价于 == TRUE
FlightInfo.is_international
LISTEMPTY(FlightInfo.tags)

# 显式
FlightInfo.is_international == TRUE
FlightInfo.is_international == FALSE
```

赋值区：

```
FlightInfo.is_international = TRUE
```

!!! warning
    布尔值只认 `TRUE` / `FALSE`，`true`、`True` 会报错。

## 空值：`ISNULL` / `ISNOTNULL`

条件里的谓词，对齐 `LISTEMPTY`。编成引擎「为空 / 不为空」，不是函数，不能赋值、不能再比较。

```
ISNULL(FlightInfo.gate)
ISNOTNULL(FlightInfo.gate)
ISNULL(FlightInfo.gate) AND FlightInfo.score > 0
ISNULL(TRIM(FlightInfo.name))
ISNULL(FlightInfo.score) OR FlightInfo.score == -999
```

这些非法：`FlightInfo.gate NULL`（不要后缀）、`ISNULL(x) == TRUE`、`flag = ISNULL(x)`、`ISNULL(1)`、`ISNULL(score + 10)`。

哨兵默认值（业务用 `-999` 表示缺失）写 `==`，不要另造函数。

!!! warning "同一条 ISNULL，空串在两条路径结果不同"
    `ISNULL("")`：引擎 `NullAssertor` 当空（`isBlank` → **true**，全量命中）。灰度 `ConditionEvaluator` 只认 `== null`（**false**，灰度放过）。`x=""` 时灰度切全量会翻案。不要改灰度去对齐。缺字段 / `null` 两边都是 true。

### 灰度 SDK

`condition_expr` 是跨进程契约：服务端下发文本，业务方 `ruleuler-client` 本地求值。存量后缀 `x NULL` / `x Null` 求值器永久认，不会改写成 `ISNULL`。

新写 `ISNULL(...)` 或四则的灰度规则，要求业务方 `ruleuler-client` 升到本期及以后。未升级的老 SDK 遇到 `ISNULL(` 会报「灰度条件不支持函数」，遇到 `*` 会报意外字符。

## 四则：`+ - * / %`

条件左边只能是「一个原子 + 一串数字」：`FlightInfo.score + 10 > 80`。两个变量相加不能放左边，先赋值到参数再比较。比较左边不能是数字。

赋值右边、比较右边、函数实参可以挂完整运算：

```
can_score = FlightInfo.score + 10
can_score = FlightInfo.amount * 1.1
can_score = ABS(FlightInfo.score) + 10
can_score = 1.1 * (FlightInfo.score + 10)
ABS(FlightInfo.score + 10) > 5
```

优先级 `* / %` 先于 `+ -`（引擎 MVEL）。括号只出现在运算符**右边**。值不能以括号开头：`(score + 10) * 1.1` 写成 `1.1 * (score + 10)`；`(score + 10) > 80` 去掉括号。

二元运算符两侧都要空格：`score - 10` 合法；`score-10`、`score -10` 非法（后者 `-10` 被认成负数）。

`+` 不做字符串拼接承诺。`IN ("A1", "A2")` 的括号是列表，不是运算。

比较两边都是复合运算（`score + a > bonus + b`）非法，先赋值。不会自动翻转比较。

!!! warning "比较左边禁止数字"
    `80 < score + bonus` 全量、灰度都非法。引擎 left 没有字面量槽，编成参数名会炸。两个变量相加也一样：先赋值到参数再比较。

## 函数

REA 对用户只暴露一套调用语法。编译期落到引擎已有的 Method / CommonFunction，不改运行时。

值函数进条件左/右、赋值右边。动作函数只进那么/否则。`LISTSORT` 两种都能写。

```
# 扁平（推荐）
TRIM(FlightInfo.name) == ""
LISTEMPTY(FlightInfo.tags) AND MAPHAS(FlightInfo.extra, "k")
ABS(FlightInfo.score) >= 10
NOW()

# 命名空间（补全分组 / 消歧）
STRING.TRIM(FlightInfo.name)
MATH.ABS(FlightInfo.score)
DATE.NOW()
LIST.SIZE(FlightInfo.tags)

# 集合聚合（第二参是属性名，不是变量）
COUNT(Order.items) > 0
SUM(Order.items, amount) > 1000

# 赋值
gate_type = TRIM(FlightInfo.gate_id)
can_score = ABS(MIN(FlightInfo.score, 100))

# 那么 / 否则（动作函数，无返回值）
LISTADD(FlightInfo.tags, "INTL"); risk_level = "high"
MAPPUT(FlightInfo.extra, "level", "VIP")
```

这些会报错：

```
LISTADD(FlightInfo.tags, "VIP") == TRUE   # 动作函数不能进条件
can_score = LISTADD(FlightInfo.tags, "VIP")  # 动作函数没有返回值
TRIM(FlightInfo.name)                    # 值函数不能当一句动作，必须赋值
listAdd(xs, "VIP")                       # 只认全大写
SUM(Order.items.amount)                  # 第二参是属性名，不要写成点号路径
```

命名空间前缀 `STRING` `MATH` `DATE` `LIST` `MAP` 硬保留，大小写不敏感拦类别名/参数名。`Date` / `date` / `DATE` 都不能当类别。不拦中文：`日期.arrival` 合法。`DateInfo` 合法。

### 字符串 → `urule.stringAction`

`TRIM` `SUBSTRING` `SUBSTRINGFROM` `SUBSTRINGTO` `LOWER` `UPPER` `LENGTH` `CHARAT` `INDEXOF` `LASTINDEXOF` `REPLACE` `SPLIT`

### 数学 → `urule.mathAction`

`ABS` `MAX` `MIN`（两标量，不是集合）`SIN` `COS` `TAN` `COT` `LN` `LOG10` `ROUND`

### 日期 → `urule.dateAction`

`NOW` `PARSEDATE` `FORMATDATE` `ADDYEARS` / `ADDMONTHS` / `ADDDAYS` / `ADDHOURS` / `ADDMINUTES` / `ADDSECONDS` 以及对应 `SUB*`。`YEAR` `MONTH` `WEEK` `DAY` `HOUR` `MINUTE` `SECOND`。`DIFFMILLIS` / `DIFFSECONDS` / `DIFFMINUTES` / `DIFFHOURS` / `DIFFDAYS` / `DIFFWEEKS` / `DIFFMONTHS`。

!!! warning "日期怪癖（不改引擎）"
    - `MONTH` 返回 **0-11**，0=一月。`MONTH(NOW()) == 9` 是十月不是九月
    - `WEEK` 是 `DAY_OF_WEEK`：**1=周日 … 7=周六**，不是 ISO 周一=1
    - `DAY` 对应引擎方法 `getay`（历史拼写错误）

### List / Map

值：`LISTSIZE` `LISTMAX` `LISTMIN` `LISTCONTAINS` `LISTEMPTY` `LISTSORT` `LISTRETRIVE` `MAPGET` `MAPSIZE` `MAPHAS`

动作（只进那么/否则）：`LISTADD` `LISTREMOVE` `MAPPUT` `MAPREMOVE`

`LISTRETRIVE` 对应引擎方法 `retrive`（拼写如此）。

`LISTSORT` / `LISTRETRIVE` 的**属性名参数**写裸名（或字符串字面量），编译成字符串：
`LISTSORT(Order.items, amount, "正序")`。

引擎排序方式的**升序词面只认 `1` / `true` / `正序`**，其它（包括英文 `"asc"`）一律倒序。

`LISTREMOVE` 的字面量实参按字符串处理：从数字元素集合删除时，用变量传要删的值。

### 集合聚合 → CommonFunction

`COUNT(xs)` 无属性。`SUM` `AVG` `MAXOF` `MINOF` 第二参是属性 **name**：`SUM(Order.items, amount)`。

`MAX`/`MIN` 留给两参数值比较。集合对象属性用 `MAXOF`/`MINOF`。

灰度条件不支持普通函数（`ISNULL` / `ISNOTNULL` 除外），四则按上面的规则可以写。

### 自定义（项目动作库）

动作库里的 Spring Bean 方法，语法和内置命名空间相同。REA 里 Bean、方法都大写，查库忽略大小写：

```
RISKSERVICE.SCORE(FlightInfo.airline, 10)
```

动作库仍是 `riskService` / `score`。没导入或对不上直接报错，不猜。规则集需要 `import-action-library`。

自定义函数（模板打 jar 上传）见 [自定义函数](rea-function-extension.md)。

不在编辑器里写脚本当函数。本期不重做动作库编辑器，只消费已有 `.al.xml` 元数据。

## 条件表达式示例

```
FlightInfo.arrival_time > 5
FlightInfo.arrival_time > 5 AND FlightInfo.is_international
FlightInfo.airline IN ("CA", "MU", "CZ")
FlightInfo.arrival_time > 5 AND (FlightInfo.flight_type == "国内" OR FlightInfo.flight_type == "国际")
ABS(FlightInfo.score) >= 10 AND LISTCONTAINS(FlightInfo.tags, "VIP")
ISNULL(FlightInfo.gate) OR FlightInfo.score + 10 > 80
threshold < FlightInfo.score + FlightInfo.bonus
```

!!! note
    同层不支持 AND/OR 混用，需要混用时请用括号分组。

## 赋值表达式示例

```
can_score = 5
can_score = 5; risk_level = "high"
can_score = ABS(FlightInfo.score)
can_score = FlightInfo.amount * 1.1
can_score = 1.1 * (FlightInfo.score + 10)
LISTADD(FlightInfo.tags, "INTL"); risk_level = "high"
```

## 自动补全

- 无 `.` 时提示：变量类别名、参数名、操作符、`AND`/`OR`、`TRUE`/`FALSE`、`ISNULL()`、函数
- 输入 `.` 后提示：该类别下的变量属性
- `STRING.` / `MATH.` / `DATE.` / `LIST.` / `MAP.` 以及自定义 Bean 名后只出该组方法
- 选中函数插入 `TRIM()`，光标进括号
- 条件区不出 `LISTADD` 等动作函数

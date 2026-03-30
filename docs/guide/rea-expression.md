# REA 表达式语法

REA（Rule Expression Assistant）是 RulEuler 的规则文本编辑器，将人类可读的表达式编译为引擎可执行格式。

## 表达式结构

规则由三部分组成：

| 区域 | 含义 | 语法 |
|------|------|------|
| 如果 | 条件 | `变量 操作符 值 [AND/OR ...]` |
| 那么 | 命中动作 | `变量 = 值 [; ...]` |
| 否则 | 未命中动作 | `变量 = 值 [; ...]` |

## 引用格式

| 类型 | 格式 | 示例 |
|------|------|------|
| 变量 | `类别.变量名` | `FlightInfo.arrival_time` |
| 参数 | 直接写参数名 | `can_score` |

## 操作符

| 文本 | 含义 | 适用类型 |
|------|------|----------|
| `==` | 等于 | 所有 |
| `!=` | 不等于 | 所有 |
| `>` `>=` `<` `<=` | 比较 | 数值、日期 |
| `Contain` / `NotContain` | 包含 | 字符串 |
| `In` / `NotIn` | 在列表中 | 所有 |
| `Match` / `NotMatch` | 正则匹配 | 字符串 |
| `Startwith` / `Endwith` | 前缀/后缀 | 字符串 |
| `EqualsIgnoreCase` | 忽略大小写相等 | 字符串 |

## 值类型

| 类型 | 写法 | 示例 |
|------|------|------|
| 字符串 | 双引号包裹 | `"国际"` |
| 数字 | 直接写 | `5`、`-3.14` |
| 布尔 | `true` / `false` | `true` |
| 变量引用 | `类别.变量名` | `FlightInfo.gate_id` |
| 参数引用 | 裸参数名 | `threshold` |
| 列表 | 圆括号逗号分隔 | `("A", "B", "C")` |

## Boolean 类型

Boolean 变量支持隐式和显式两种写法（需要库中声明变量类型为 Boolean）：

条件区：

```
# ✅ 隐式 — 等价于 == true
FlightInfo.is_international

# ✅ 显式
FlightInfo.is_international == true
FlightInfo.is_international == false
```

赋值区：

```
FlightInfo.is_international = true
```

!!! warning
    布尔值只认小写 `true` / `false`，`True`、`TRUE` 等会报错。

## 条件表达式示例

单条件：

```
FlightInfo.arrival_time > 5
```

多条件（AND）：

```
FlightInfo.arrival_time > 5 AND FlightInfo.is_international
```

多条件（OR）：

```
FlightInfo.flight_type == "国内" OR FlightInfo.flight_type == "国际"
```

列表匹配：

```
FlightInfo.airline In ("CA", "MU", "CZ")
```

括号分组（混合 AND/OR）：

```
FlightInfo.arrival_time > 5 AND (FlightInfo.flight_type == "国内" OR FlightInfo.flight_type == "国际")
```

嵌套分组：

```
(A.x > 1 AND A.y < 10) OR (B.x == "test" AND B.y != "skip")
```

!!! note
    同层不支持 AND/OR 混用，需要混用时请用括号分组。

## 赋值表达式示例

单赋值：

```
can_score = 5
```

多赋值（分号分隔）：

```
can_score = 5; risk_level = "high"
```

## 自动补全

编辑器支持：

![REA 表达式编辑器](../assets/images/rea.png)

- 无 `.` 时提示：变量类别名、参数名、操作符、`AND`/`OR`、`true`/`false`
- 输入 `.` 后提示：该类别下的变量属性

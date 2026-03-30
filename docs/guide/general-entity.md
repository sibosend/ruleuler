# GeneralEntity 变量类型

## 背景

传统方式下，规则引擎要求先定义 Java POJO 类或自定义变量结构，规则条件才能引用这些变量。GeneralEntity 类型简化了这个流程——基于 HashMap 包装类，只需定义字段名和数据类型即可。

## 三种变量分类类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| GeneralEntity | 基于 HashMap，只需字段名 + 类型 | 新项目推荐，最简单 |
| Custom | 自定义字段 | 旧项目兼容 |
| Clazz | Java POJO 类 | 需要强类型绑定 |

新建变量库时默认使用 GeneralEntity 类型。

## 使用方式

### 1. 创建变量库

在管理后台中创建变量库文件（`.vl.xml`），新建分类时：

- 类型默认为 GeneralEntity
- 填写 targetClass 名称（如 `FlightInfo`）
- 添加变量，只需填写字段名和数据类型，label 自动等于字段名

### 2. 变量库 XML 格式

```xml
<variable-library>
  <category name="FlightInfo" type="GeneralEntity" clazz="FlightInfo">
    <var act="InOut" name="aircraft_type" label="aircraft_type" type="String"/>
    <var act="InOut" name="arrival_time" label="arrival_time" type="Integer"/>
    <var act="InOut" name="is_international" label="is_international" type="Boolean"/>
    <var act="InOut" name="passenger_count" label="passenger_count" type="Integer"/>
  </category>
</variable-library>
```

### 3. 在规则中引用

GeneralEntity 类型的变量在规则编辑器中的使用方式与 Custom 类型完全一致——从变量选择菜单中选择分类，再选择字段。

### 4. 调用时传参

请求体按分类名（即 targetClass）分组：

```json
{
  "FlightInfo": {
    "aircraft_type": "A380",
    "arrival_time": 8,
    "is_international": true,
    "passenger_count": 260
  }
}
```

## 支持的数据类型

| 类型 | 说明 |
|------|------|
| String | 字符串 |
| Integer | 整数 |
| Double | 双精度浮点 |
| Long | 长整数 |
| Float | 单精度浮点 |
| BigDecimal | 高精度数值 |
| Boolean | 布尔值 |
| Date | 日期 |

## 兼容性

- 旧项目使用的 Custom 和 Clazz 类型完全不受影响
- GeneralEntity 类型的变量库可以与 Custom/Clazz 类型的变量库在同一项目中共存

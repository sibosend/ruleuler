# GeneralEntity Variables

## Background

Traditionally, rule engines require pre-defining Java POJO classes before rule conditions can reference variables. The `GeneralEntity` type simplifies this workflow — backed by a `HashMap` wrapper, it only requires field names and data types.

## Three Variable Category Types

| Type | Description | Use Case |
|------|-------------|----------|
| `GeneralEntity` | `HashMap`-backed, only requires field name + type | Recommended for new projects |
| `Custom` | Custom field definitions | Legacy project compatibility |
| `Clazz` | Java POJO class | Strict Java type binding |

New variable libraries default to `GeneralEntity`.

## Usage

### 1. Create a Variable Library

Create a variable library file (`.vl.xml`) in the admin console. When adding a category:

- The type defaults to `GeneralEntity`
- Enter the category / `targetClass` name (e.g., `FlightInfo`)
- Add variables by specifying only the field name and data type (`label` defaults to the field name)

### 2. Variable Library XML Format

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

### 3. Reference in Rules

`GeneralEntity` variables are selected in rule editors in the exact same way as `Custom` variables — pick the category first, then pick the field.

### 4. Pass Input Data in API Calls

Group request JSON fields by category name (`targetClass`):

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

## Supported Data Types

| Type | Description |
|------|-------------|
| `String` | Text string |
| `Integer` | 32-bit integer |
| `Double` | Double-precision floating point |
| `Long` | 64-bit integer |
| `Float` | Single-precision floating point |
| `BigDecimal` | Arbitrary-precision decimal |
| `Boolean` | Boolean (`true` / `false`) |
| `Date` | Date / timestamp |

## Compatibility

- Existing projects using `Custom` and `Clazz` types remain unaffected
- `GeneralEntity` variable libraries can coexist with `Custom` / `Clazz` variable libraries within the same project


# 客户端 API

> Base URL: `http://{host}:{port}` 默认端口: 16001（Docker）/ 9281（本地开发）

## 执行决策流

`POST /process/{project}/{knowledge}/{process}`

请求体按变量类别分组，类别名需与知识包中定义的一致。

### 请求示例

```bash
curl -X POST 'http://127.0.0.1:16001/process/airport_gate_allocation_db/gate_pkg/gate_allocation_flow' \
  -H 'Content-Type: application/json' \
  -d '{
  "FlightInfo": {
    "aircraft_type": "A380",
    "arrival_time": 8,
    "is_international": true,
    "passenger_count": 260
  },
  "GateResult": {}
}'
```

### 路径参数

| 参数 | 说明 |
|------|------|
| `project` | 项目名称 |
| `knowledge` | 知识包 ID |
| `process` | 决策流 ID |

### 返回示例

```json
{
  "status": 200,
  "msg": "ok",
  "data": {
    "can_score": 80,
    "can_reason": "高峰时段大客流优先近机位",
    "GateResult": {
      "priority_score": 110,
      "reason": "中客流国际航班近机位",
      "assigned_gate": "B区-202",
      "gate_type": "近机位"
    }
  }
}
```

!!! note
    返回只包含参数输出和被规则引擎修改的变量字段，未变化的输入字段不返回。

## 变量提取

`POST /inspector/variables/{project}/{packageId}`

提取知识包使用的变量列表。

```bash
curl -X POST http://127.0.0.1:16001/inspector/variables/airport_gate_allocation_db/gate_pkg
```

## 健康检查

`GET /health`

返回服务状态信息。

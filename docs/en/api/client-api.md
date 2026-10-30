# Client API

> Base URL: `http://{host}:{port}` Default port: 16001 (Docker / local dev)

## Execute Decision Flow

`POST /process/{project}/{knowledge}/{process}`

The request body is grouped by variable category names, which must match the categories defined in the knowledge package.

### Sample Request

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

### Path Parameters

| Parameter | Description |
|-----------|-------------|
| `project` | Project name |
| `knowledge` | Knowledge package ID |
| `process` | Decision flow ID |

### Sample Response

```json
{
  "status": 200,
  "msg": "ok",
  "data": {
    "can_score": 80,
    "can_reason": "Peak hour high passenger volume prioritized for contact gate",
    "GateResult": {
      "priority_score": 110,
      "reason": "Medium passenger international flight contact gate",
      "assigned_gate": "Zone B-202",
      "gate_type": "Contact Gate"
    }
  }
}
```

!!! note
    The response only includes output parameters and variable fields modified during rule execution. Unchanged input fields are omitted.

## Inspect Variables

`POST /inspector/variables/{project}/{packageId}`

Extracts the list of variables used by a knowledge package.

```bash
curl -X POST http://127.0.0.1:16001/inspector/variables/airport_gate_allocation_db/gate_pkg
```

## Health Check

`GET /health`

Returns service health status.


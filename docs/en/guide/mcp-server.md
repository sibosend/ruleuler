# RulEuler MCP Server Guide

## Overview

RulEuler MCP Server is built into `ruleuler-client` on top of Spring AI MCP, exposing two MCP Tools over the Streamable HTTP protocol so AI assistants can directly inspect and invoke the rule engine.

| Tool | Description |
|------|-------------|
| `execute_rule` | Execute a decision flow with input variables and return only the fields modified by the rules |
| `inspect_variables` | Inspect variable definitions of a knowledge package (categories, names, associated files) |

## Server Configuration

### Enable / Disable

```yaml
ruleuler:
  mcp:
    enabled: true   # Set to false in production to disable
```

Disabled (`false`) by default in `application.yml`, and enabled (`true`) in `application-dev.yml`.

### Spring AI MCP Settings

```yaml
spring:
  ai:
    mcp:
      server:
        name: ruleuler-mcp-server
        version: 1.0.0
        type: SYNC
        stdio: false
        protocol: STREAMABLE
        annotation-scanner:
          enabled: true
```

- `protocol: STREAMABLE` — Uses the Streamable HTTP transport protocol
- `annotation-scanner.enabled: true` — Automatically scans `@McpTool` annotations

### Endpoint URL

MCP Server shares the same port as `ruleuler-client` (default `16001`) at `POST /mcp`:

```
http://<host>:16001/mcp
```

## Client Integration

### Claude Desktop

Edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

```json
{
  "mcpServers": {
    "ruleuler": {
      "url": "http://localhost:16001/mcp"
    }
  }
}
```

Restart Claude Desktop after saving.

### Claude Code (CLI)

Configure in `.claude/settings.json` (project) or `~/.claude/settings.json` (global):

```json
{
  "mcpServers": {
    "ruleuler": {
      "url": "http://localhost:16001/mcp"
    }
  }
}
```

### Cursor IDE

Settings → MCP → Add new MCP Server:

- Name: `ruleuler`
- Type: `streamable-http`
- URL: `http://localhost:16001/mcp`

### MCP SDK Clients

Node.js example:

```typescript
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamablehttp.js";

const client = new Client({ name: "my-app", version: "1.0.0" });
const transport = new StreamableHTTPClientTransport(new URL("http://localhost:16001/mcp"));
await client.connect(transport);

const tools = await client.listTools();

const result = await client.callTool({
  name: "execute_rule",
  arguments: {
    project: "airport_gate_allocation_db",
    knowledge: "gate_pkg",
    process: "gate_flow",
    data: '{"FlightInfo":{"aircraft_type":"A380"}}'
  }
});
```

Python example:

```python
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

async def main():
    async with streamablehttp_client("http://localhost:16001/mcp") as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            result = await session.call_tool("execute_rule", {
                "project": "airport_gate_allocation_db",
                "knowledge": "gate_pkg",
                "process": "gate_flow",
                "data": '{"FlightInfo":{"aircraft_type":"A380"}}'
            })
```

## Tool Reference

### `execute_rule`

Executes a RulEuler decision flow.

**Arguments:**

| Argument | Required | Description |
|----------|----------|-------------|
| `project` | Yes | Project name |
| `knowledge` | Yes | Knowledge package ID |
| `process` | Yes | Decision flow ID |
| `data` | Yes | JSON input string grouped by variable category, e.g., `{"FlightInfo":{"aircraft_type":"A380"}}` |

**Success Response:**

```json
{
  "status": 200,
  "executionId": "uuid",
  "packageId": "project/pkg",
  "route": "BASE",
  "data": {"GateResult": {"gate_type": "wide_body", "assigned_gate": "A12"}}
}
```

- `route`: Gray release routing indicator (`BASE` or `GRAY`)
- `data`: Contains only fields modified by the rules

### `inspect_variables`

Inspects variable definitions of a knowledge package.

**Arguments:**

| Argument | Required | Description |
|----------|----------|-------------|
| `project` | Yes | Project name |
| `packageId` | Yes | Knowledge package ID |

**Success Response:**

```json
{
  "status": 200,
  "project": "your_project",
  "packageId": "your_pkg",
  "totalVariables": 5,
  "variables": [
    {
      "category": "FlightInfo",
      "name": "aircraft_type",
      "fullName": "FlightInfo.aircraft_type",
      "categoryClass": "com.bstek.urule.model.GeneralEntity",
      "usedByFiles": ["rule1.xml", "flow1.xml"]
    }
  ],
  "variableCategoryMap": {
    "FlightInfo": "com.bstek.urule.model.GeneralEntity"
  }
}
```


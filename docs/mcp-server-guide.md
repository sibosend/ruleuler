# RulEuler MCP Server 接入指南

## 概述

RulEuler MCP Server 集成在 ruleuler-client 模块中，通过 Streamable HTTP 协议暴露两个 MCP Tool，供 AI 助手调用规则引擎。

| Tool | 功能 |
|------|------|
| `execute_rule` | 执行决策流，传入变量数据，返回决策结果 |
| `inspect_variables` | 查看知识包的变量定义（类别、名称、关联文件） |

## 服务端配置

### 开关

```yaml
ruleuler:
  mcp:
    enabled: true   # 生产环境设为 false 可关闭
```

默认 `application.yml` 中为 `false`，`application-dev.yml` 中为 `true`。

### 服务信息

```yaml
spring:
  ai:
    mcp:
      server:
        name: ruleuler-mcp-server
        version: 1.0.0
        type: SYNC
```

### 端点地址

MCP Server 与 ruleuler-client 共享端口（默认 16001），端点路径为 `POST /mcp`。

```
http://<host>:16001/mcp
```

## 客户端接入

### Claude Desktop

编辑配置文件（macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "ruleuler": {
      "url": "http://localhost:16001/mcp"
    }
  }
}
```

保存后重启 Claude Desktop。

### Claude Code (CLI)

在项目 `.claude/settings.json` 中配置：

```json
{
  "mcpServers": {
    "ruleuler": {
      "url": "http://localhost:16001/mcp"
    }
  }
}
```

或全局配置 `~/.claude/settings.json`。

### Cursor IDE

Settings → MCP → Add new MCP Server：

- Name: `ruleuler`
- Type: `streamable-http`
- URL: `http://localhost:16001/mcp`

### 任意 MCP 客户端（SDK）

Node.js 示例：

```typescript
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamablehttp.js";

const client = new Client({ name: "my-app", version: "1.0.0" });
const transport = new StreamableHTTPClientTransport(new URL("http://localhost:16001/mcp"));
await client.connect(transport);

// 列出工具
const tools = await client.listTools();

// 执行规则
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

Python 示例：

```python
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client

async def main():
    async with streamablehttp_client("http://localhost:16001/mcp") as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()

            # 列出工具
            tools = await session.list_tools()

            # 执行规则
            result = await session.call_tool("execute_rule", {
                "project": "airport_gate_allocation_db",
                "knowledge": "gate_pkg",
                "process": "gate_flow",
                "data": '{"FlightInfo":{"aircraft_type":"A380"}}'
            })
```

## curl 测试

### 1. 初始化

```bash
curl -X POST http://localhost:16001/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {"name": "test", "version": "1.0"}
    }
  }'
```

### 2. 列出 Tools

```bash
curl -X POST http://localhost:16001/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

### 3. 执行决策流

```bash
curl -X POST http://localhost:16001/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "execute_rule",
      "arguments": {
        "project": "your_project",
        "knowledge": "your_pkg",
        "process": "your_flow",
        "data": "{\"FlightInfo\":{\"aircraft_type\":\"A380\"}}"
      }
    }
  }'
```

成功返回：

```json
{
  "status": 200,
  "executionId": "uuid",
  "packageId": "your_project/your_pkg",
  "route": "BASE",
  "data": {"GateResult": {"gate_type": "wide_body", "assigned_gate": "A12"}}
}
```

失败返回：

```json
{"status": 400, "error": "Unknown variable category: BadCat"}
{"status": 404, "error": "Knowledge package not found: project/pkg"}
{"status": 500, "error": "异常信息"}
```

### 4. 查看变量定义

```bash
curl -X POST http://localhost:16001/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "inspect_variables",
      "arguments": {
        "project": "your_project",
        "packageId": "your_pkg"
      }
    }
  }'
```

成功返回：

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

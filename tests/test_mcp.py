#!/usr/bin/env python3
"""
MCP Server 单元测试

用法：
  python tests/test_mcp.py
  python tests/test_mcp.py --base-url http://localhost:16001/mcp
  python tests/test_mcp.py --project airport_gate_allocation_db --package gate_pkg
"""

import argparse
import json
import sys
import urllib.request
import urllib.error

# ─── MCP STREAMABLE 客户端 ─────────────────────────────────────

class McpClient:
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.session_id = None
        self._req_id = 0

    def _next_id(self) -> int:
        self._req_id += 1
        return self._req_id

    def call(self, method: str, params: dict = None, *, notification: bool = False):
        payload = {"jsonrpc": "2.0", "method": method}
        if not notification:
            payload["id"] = self._next_id()
        if params is not None:
            payload["params"] = params

        req = urllib.request.Request(
            self.base_url,
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "Accept": "text/event-stream, application/json",
            },
            method="POST",
        )
        if self.session_id:
            req.add_header("Mcp-Session-Id", self.session_id)

        try:
            resp = urllib.request.urlopen(req, timeout=30)
            sid = resp.headers.get("Mcp-Session-Id")
            if sid:
                self.session_id = sid
            ct = resp.headers.get("Content-Type", "")
            # notification: 关闭连接即可
            if notification:
                resp.close()
                return None
            body = resp.read().decode()
            if not body.strip():
                return None
            if "text/event-stream" in ct or body.startswith("event:"):
                for line in reversed(body.split("\n")):
                    if line.startswith("data:"):
                        d = line[5:].strip()
                        if d:
                            return json.loads(d)
                return None
            return json.loads(body)
        except urllib.error.HTTPError as e:
            return {"jsonrpc": "2.0", "error": {"code": e.code, "message": e.read().decode()[:300]}}

    def initialize(self) -> dict:
        r = self.call("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "mcp-test", "version": "1.0"},
        })
        assert r and "result" in r, f"initialize 失败: {r}"
        # 发 initialized 通知
        self.call("notifications/initialized", notification=True)
        return r["result"]

    def list_tools(self) -> list:
        r = self.call("tools/list", {})
        if r is None or "result" not in r:
            # 可能 session 失效，重新 initialize
            self.initialize()
            r = self.call("tools/list", {})
        assert r and "result" in r, f"tools/list 失败: {r}"
        return r["result"].get("tools", [])

    def call_tool(self, name: str, arguments: dict) -> dict:
        r = self.call("tools/call", {"name": name, "arguments": arguments})
        assert r and "result" in r, f"tools/call {name} 失败: {r}"
        return r["result"]


# ─── 测试用例 ──────────────────────────────────────────────────

def test_initialize(client: McpClient):
    result = client.initialize()
    info = result.get("serverInfo", {})
    assert info.get("name") == "ruleuler-mcp-server", f"server name 不匹配: {info.get('name')}"
    assert client.session_id, "session id 未获取"
    print(f"  server: {info['name']} v{info['version']}")
    print(f"  session: {client.session_id}")
    return True


def test_tools_list(client: McpClient):
    tools = client.list_tools()
    names = [t["name"] for t in tools]
    assert "execute_rule" in names, f"execute_rule 未注册, 实际: {names}"
    assert "inspect_variables" in names, f"inspect_variables 未注册, 实际: {names}"
    for t in tools:
        desc = t.get("description", "")[:60]
        params = list(t.get("inputSchema", {}).get("properties", {}).keys())
        print(f"  {t['name']}: {desc}... params={params}")
    return True


def test_execute_rule(client: McpClient, project: str, package: str, process: str):
    result = client.call_tool("execute_rule", {
        "project": project,
        "knowledge": package,
        "process": process,
        "data": json.dumps({
            "FlightInfo": {
                "aircraft_type": "A380",
                "arrival_time": 8,
                "is_international": True,
                "passenger_count": 350,
            },
            "GateResult": {},
        }),
    })
    content = result.get("content", [])
    assert content, "execute_rule 返回空 content"
    parsed = json.loads(content[0]["text"])
    assert parsed.get("status") == 200, f"execute_rule 状态异常: {parsed}"
    assert parsed.get("data"), "execute_rule 返回空 data"
    assert parsed.get("executionId"), "execute_rule 缺少 executionId"
    gate = parsed["data"].get("GateResult", {})
    print(f"  executionId: {parsed['executionId'][:12]}...")
    print(f"  route: {parsed.get('route')}")
    print(f"  result: gate_type={gate.get('gate_type')} gate={gate.get('assigned_gate')} score={gate.get('priority_score')}")
    return True


def test_execute_rule_invalid_json(client: McpClient, project: str, package: str, process: str):
    result = client.call_tool("execute_rule", {
        "project": project,
        "knowledge": package,
        "process": process,
        "data": "not-json{{{",
    })
    content = result.get("content", [])
    parsed = json.loads(content[0]["text"]) if content else {}
    assert parsed.get("status") == 400, f"期望 status=400, 实际: {parsed}"
    print(f"  正确返回 400: {parsed.get('error', '')[:60]}")
    return True


def test_execute_rule_missing_project(client: McpClient):
    result = client.call_tool("execute_rule", {
        "project": "nonexistent_project",
        "knowledge": "nonexistent_pkg",
        "process": "nonexistent_flow",
        "data": "{}",
    })
    content = result.get("content", [])
    parsed = json.loads(content[0]["text"]) if content else {}
    assert parsed.get("status") in (404, 500), f"期望 404/500, 实际: {parsed}"
    print(f"  正确返回 {parsed.get('status')}: {parsed.get('error', '')[:60]}")
    return True


def test_inspect_variables_not_found(client: McpClient):
    result = client.call_tool("inspect_variables", {
        "project": "nonexistent",
        "packageId": "nonexistent",
    })
    content = result.get("content", [])
    parsed = json.loads(content[0]["text"]) if content else {}
    assert parsed.get("status") in (404, 500), f"期望 404/500, 实际: {parsed}"
    print(f"  正确返回 {parsed.get('status')}: {parsed.get('error', '')[:60]}")
    return True


def test_inspect_variables(client: McpClient, project: str, package: str):
    result = client.call_tool("inspect_variables", {
        "project": project,
        "packageId": package,
    })
    content = result.get("content", [])
    assert content, "inspect_variables 返回空 content"
    parsed = json.loads(content[0]["text"])
    assert parsed.get("status") == 200, f"inspect_variables 状态异常: {parsed}"
    total = parsed.get("totalVariables", 0)
    assert total > 0, "变量数为 0"
    print(f"  totalVariables: {total}")
    for v in parsed.get("variables", [])[:3]:
        print(f"  - {v['fullName']} via {v.get('usedByFiles', [])[:1]}")
    if total > 3:
        print(f"  ... 共 {total} 个")
    return True


def test_inspect_variables_not_found(client: McpClient):
    result = client.call_tool("inspect_variables", {
        "project": "nonexistent",
        "packageId": "nonexistent",
    })
    content = result.get("content", [])
    parsed = json.loads(content[0]["text"]) if content else {}
    assert parsed.get("status") in (404, 500), f"期望 404/500, 实际: {parsed}"
    print(f"  正确返回 {parsed.get('status')}: {parsed.get('error', '')[:60]}")
    return True


# ─── Runner ────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="MCP Server 单元测试")
    parser.add_argument("--base-url", default="http://localhost:16001/mcp")
    parser.add_argument("--project", default="airport_gate_allocation_db")
    parser.add_argument("--package", default="gate_pkg")
    parser.add_argument("--process", default="gate_allocation_flow")
    args = parser.parse_args()

    client = McpClient(args.base_url)
    passed = failed = 0

    # 先 initialize
    print("[initialize]")
    try:
        test_initialize(client)
        print("  PASS\n")
        passed += 1
        initialized = True
    except (AssertionError, Exception) as e:
        print(f"  FAIL: {e}\n")
        failed += 1

    # (名称, 测试函数)
    tests = [
        ("tools/list", lambda: test_tools_list(client)),
        ("execute_rule (正常)", lambda: test_execute_rule(client, args.project, args.package, args.process)),
        ("execute_rule (非法JSON)", lambda: test_execute_rule_invalid_json(client, args.project, args.package, args.process)),
        ("execute_rule (不存在项目)", lambda: test_execute_rule_missing_project(client)),
        ("inspect_variables (正常)", lambda: test_inspect_variables(client, args.project, args.package)),
        ("inspect_variables (不存在)", lambda: test_inspect_variables_not_found(client)),
    ]

    print("=" * 55)
    print("  MCP Server 单元测试")
    print("=" * 55)
    print(f"  endpoint: {args.base_url}")
    print(f"  project:  {args.project}")
    print(f"  package:  {args.package}")
    print()

    passed = failed = 0
    initialized = False

    for name, fn in tests:
        try:
            print(f"[{name}]")
            fn()
            print(f"  PASS\n")
            passed += 1
        except (AssertionError, Exception) as e:
            print(f"  FAIL: {e}\n")
            failed += 1

    print("=" * 55)
    print(f"  结果: {passed} passed, {failed} failed, {passed + failed} total")
    print("=" * 55)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()

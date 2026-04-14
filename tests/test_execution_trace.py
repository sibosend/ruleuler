#!/usr/bin/env python3
"""
执行追踪功能测试脚本

通过 HTTP 请求调用规则引擎，验证 execution_trace_log 是否正确写入 ClickHouse。

用法：
  python tests/test_execution_trace.py
  python tests/test_execution_trace.py --client http://localhost:16001 --clickhouse http://localhost:8123
"""

import argparse
import json
import time
import urllib.request
import urllib.error
import urllib.parse


def send_request(client_url: str, payload: dict) -> dict:
    """发送规则引擎请求，返回响应"""
    url = f"{client_url}/process/airport_gate_allocation_db/gate_pkg/gate_allocation_flow"
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        return {"status": e.code, "msg": body}


def ch_query(ch_url: str, sql: str) -> list:
    """查询 ClickHouse，返回 JSONEachRow 解析结果"""
    url = f"{ch_url}/?query={urllib.parse.quote(sql)}"
    req = urllib.request.Request(url, data=b"", method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        text = resp.read().decode().strip()
        if not text:
            return []
        return [json.loads(line) for line in text.split("\n")]


def main():
    parser = argparse.ArgumentParser(description="执行追踪功能测试")
    parser.add_argument("--client", default="http://localhost:16001")
    parser.add_argument("--clickhouse", default="http://localhost:8123")
    parser.add_argument("--flush-wait", type=float, default=2.0, help="等待 flush 的秒数")
    args = parser.parse_args()

    # 两个测试用例：国际航班 vs 国内航班
    test_cases = [
        {
            "name": "国际航班·大客流",
            "payload": {
                "FlightInfo": {
                    "aircraft_type": "B777",
                    "arrival_time": 8,
                    "is_international": True,
                    "passenger_count": 300,
                },
                "GateResult": {},
            },
        },
        {
            "name": "国内航班·小客流",
            "payload": {
                "FlightInfo": {
                    "aircraft_type": "A320",
                    "arrival_time": 14,
                    "is_international": False,
                    "passenger_count": 120,
                },
                "GateResult": {},
            },
        },
    ]

    # 清空旧追踪数据
    print("[清数据] TRUNCATE execution_trace_log ...")
    try:
        ch_query(args.clickhouse, "TRUNCATE TABLE execution_trace_log")
    except Exception as e:
        print(f"  清表失败（可能表为空）: {e}")
    print()

    # 发送请求
    execution_ids = []
    for tc in test_cases:
        print(f"[请求] {tc['name']}")
        print(f"  payload: {json.dumps(tc['payload'], ensure_ascii=False)}")
        resp = send_request(args.client, tc["payload"])
        status = resp.get("status", resp.get("code", -1))
        print(f"  响应 status={status}")
        if status == 200:
            print(f"  结果: {json.dumps(resp.get('data', {}), ensure_ascii=False, indent=2)}")
        else:
            print(f"  失败: {resp}")
        print()

    # 等待 TraceLogFlusher 刷盘
    print(f"[等待] {args.flush_wait}s，等待 TraceLogFlusher 刷盘 ...")
    time.sleep(args.flush_wait)
    print()

    # 查询 execution_trace_log
    print("[查询] execution_trace_log 最近记录:")
    sql = """
        SELECT execution_id, seq, msg_type, parsed_name, pass_fail, msg_text, created_at
        FROM execution_trace_log
        ORDER BY created_at DESC, seq ASC
        LIMIT 100
    """
    try:
        rows = ch_query(args.clickhouse, sql)
    except Exception as e:
        print(f"  查询失败: {e}")
        return

    if not rows:
        print("  无数据！execution_trace_log 为空")
        print("  可能原因：")
        print("    1. monitoring.enabled 未设置为 true")
        print("    2. monitoring.clickhouse.jdbc-url 配置错误")
        print("    3. TraceLogFlusher 未启动")
        return

    # 按 execution_id 分组展示
    exec_groups = {}
    for row in rows:
        eid = row["execution_id"]
        exec_groups.setdefault(eid, []).append(row)

    print(f"  共 {len(rows)} 条追踪记录，{len(exec_groups)} 次执行\n")
    for eid, traces in exec_groups.items():
        # 取公共信息
        first = traces[0]
        print(f"  execution_id: {eid}")
        print(f"  project: {first.get('project', '')}  package_id: {first.get('package_id', '')}  flow: {first.get('flow_id', '')}")
        print(f"  created_at: {first.get('created_at', '')}")
        print(f"  追踪步骤 ({len(traces)} 条):")
        for t in traces:
            pf = t.get("pass_fail", "") or ""
            name = t.get("parsed_name", "") or ""
            msg_type = t.get("msg_type", "")
            # 截断 msg_text 避免太长
            msg = (t.get("msg_text", "") or "")[:80]
            print(f"    [{t['seq']:3d}] {msg_type:10s}  {pf:5s}  {name:20s}  {msg}")
        print()

    # 统计 msg_type 分布
    msg_type_counts = {}
    for row in rows:
        mt = row.get("msg_type", "unknown")
        msg_type_counts[mt] = msg_type_counts.get(mt, 0) + 1

    print("[统计] msg_type 分布:")
    for mt, cnt in sorted(msg_type_counts.items()):
        print(f"  {mt}: {cnt}")

    # 验证
    print()
    has_condition = any(r["msg_type"] == "Condition" for r in rows)
    has_rule_match = any(r["msg_type"] == "RuleMatch" for r in rows)
    has_var_assign = any(r["msg_type"] == "VarAssign" for r in rows)

    checks = [
        ("Condition 记录", has_condition),
        ("RuleMatch 记录", has_rule_match),
        ("VarAssign 记录", has_var_assign),
        ("执行次数 >= 2", len(exec_groups) >= 2),
    ]

    all_pass = True
    for label, ok in checks:
        status = "PASS" if ok else "FAIL"
        if not ok:
            all_pass = False
        print(f"  [{status}] {label}")

    print()
    if all_pass:
        print("执行追踪功能测试通过！")
    else:
        print("执行追踪功能测试失败！")


if __name__ == "__main__":
    main()

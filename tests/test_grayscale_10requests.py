#!/usr/bin/env python3
"""
灰度效果测试 — 发送 10 个请求，观察灰度路由分布

用法:
  python tests/test_grayscale_10requests.py
  python tests/test_grayscale_10requests.py --client http://localhost:16001
"""

import json
import random
import time
import urllib.request
import urllib.error
import argparse
from collections import Counter

# ─── Payload 生成（与 simulate_14days_monitoring.py 一致）─────────

AIRCRAFT_TYPES = ["A320", "B737", "A380", "B777", "A350"]
AIRCRAFT_WEIGHTS = [0.30, 0.30, 0.10, 0.15, 0.15]


def generate_payload(hour: int = None):
    if hour is None:
        hour = random.randint(6, 22)

    aircraft_type = random.choices(AIRCRAFT_TYPES, weights=AIRCRAFT_WEIGHTS, k=1)[0]
    passenger_count = max(50, min(400, int(random.gauss(200, 70))))
    is_international = random.random() < 0.4

    return {
        "FlightInfo": {
            "aircraft_type": aircraft_type,
            "arrival_time": hour,
            "is_international": is_international,
            "passenger_count": passenger_count,
        },
        "GateResult": {}
    }


# ─── 发送请求 ──────────────────────────────────────────────────

def send_request(api_url: str, payload: dict):
    req = urllib.request.Request(
        api_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            elapsed = time.time() - start
            return json.loads(body), elapsed, None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        elapsed = time.time() - start
        try:
            return json.loads(body), elapsed, f"HTTP {e.code}"
        except Exception:
            return {"code": e.code, "msg": body[:200]}, elapsed, f"HTTP {e.code}"
    except Exception as e:
        elapsed = time.time() - start
        return {"code": -1, "msg": str(e)}, elapsed, str(e)


# ─── 主入口 ────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="灰度效果测试 — 10 个请求")
    parser.add_argument("--client", default="http://localhost:16001")
    parser.add_argument("--project", default="airport_gate_allocation_db")
    parser.add_argument("--package-id", default="gate_pkg")
    parser.add_argument("--process", default="gate_allocation_flow")
    parser.add_argument("--count", type=int, default=10)
    args = parser.parse_args()

    api_url = f"{args.client}/process/{args.project}/{args.package_id}/{args.process}"

    print("=" * 65)
    print("  灰度效果测试")
    print("=" * 65)
    print(f"  端点: POST {api_url}")
    print(f"  请求数: {args.count}")
    print()

    # 先检查服务是否可达
    try:
        health_req = urllib.request.Request(f"{args.client}/health", method="GET")
        with urllib.request.urlopen(health_req, timeout=5) as resp:
            health = json.loads(resp.read().decode("utf-8"))
            print(f"  服务状态: {health.get('status')} (port={health.get('port')})")
    except Exception as e:
        print(f"  ⚠ 服务不可达: {e}")
        print("  请确认 client 已启动")
        return

    print()
    print("-" * 65)
    print(f"  {'#':>2}  {'状态':>4}  {'版本':>6}  {'耗时':>6}  {'机位类型':>6}  {'机位':>10}  {'优先级':>4}  {'航班'}")
    print("-" * 65)

    results = []
    versions = Counter()
    gate_types = Counter()
    success_count = 0
    fail_count = 0

    for i in range(args.count):
        payload = generate_payload()
        flight = payload["FlightInfo"]

        resp, elapsed, error = send_request(api_url, payload)

        if error:
            fail_count += 1
            print(f"  {i+1:>2}  ✗ {error:<30}  {elapsed*1000:.0f}ms")
            results.append({"idx": i+1, "status": "fail", "error": error})
            continue

        success_count += 1
        meta = resp.get("meta", {})
        version = meta.get("version", "N/A")
        execution_id = meta.get("executionId", "N/A")

        # 提取输出
        data = resp.get("data", {})
        gate_result = data.get("GateResult", {})
        gate_type = gate_result.get("gate_type", "?")
        assigned_gate = gate_result.get("assigned_gate", "?")
        priority_score = gate_result.get("priority_score", "?")
        reason = gate_result.get("reason", "")

        versions[version] += 1
        gate_types[gate_type] += 1

        flight_info = f"{flight['aircraft_type']} PAX={flight['passenger_count']} {'国际' if flight['is_international'] else '国内'}"
        print(f"  {i+1:>2}  ✓ 200  {version:>6}  {elapsed*1006:>5.0f}ms  {gate_type:>6}  {assigned_gate:>10}  {priority_score:>4}  {flight_info}")

        results.append({
            "idx": i+1,
            "status": "ok",
            "version": version,
            "execution_id": execution_id,
            "gate_type": gate_type,
            "assigned_gate": assigned_gate,
            "priority_score": priority_score,
            "elapsed_ms": elapsed * 1000,
        })

    # ─── 汇总 ────────────────────────────────────────────────
    print("-" * 65)
    print()
    print(f"  请求结果: 成功={success_count}  失败={fail_count}")
    print(f"  版本分布: {dict(versions)}")
    print(f"  机位分布: {dict(gate_types)}")

    # 判断灰度效果
    if len(versions) > 1:
        print()
        print("  ★ 检测到多版本响应，灰度路由生效！")
        for ver, cnt in versions.most_common():
            pct = cnt / success_count * 100
            print(f"    {ver}: {cnt} 次 ({pct:.0f}%)")
    elif len(versions) == 1:
        ver = list(versions.keys())[0]
        print()
        print(f"  所有请求返回同一版本 ({ver})")
        print("  可能原因: 未配置灰度路由，或灰度比例为 0%")

    # 打印详细结果用于分析
    print()
    print("  详细响应（JSON）:")
    print(json.dumps(results, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()

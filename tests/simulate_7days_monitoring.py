#!/usr/bin/env python3
"""
机场登机口分配 - 7天监控数据模拟脚本

功能：
  1. 初始化过去7天的 ClickHouse 历史数据（直接写入 execution_var_log）
  2. 模拟当天实时流量（调用规则引擎 API，产生真实的 execution_var_log 记录）

用法：
  # 初始化7天历史 + 模拟今天实时流量
  python tests/simulate_7days_monitoring.py

  # 只初始化历史数据
  python tests/simulate_7days_monitoring.py --mode history

  # 只模拟今天实时流量（调用API）
  python tests/simulate_7days_monitoring.py --mode realtime

  # 自定义参数
  python tests/simulate_7days_monitoring.py --server http://localhost:16009 --client http://localhost:16001 --daily-count 500
"""

import argparse
import concurrent.futures
import json
import random
import time
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone

# ─── 数据分布配置 ─────────────────────────────────────────────

AIRCRAFT_TYPES = ["A320", "B737", "A380", "B777", "A350"]
AIRCRAFT_WEIGHTS = [0.30, 0.30, 0.10, 0.15, 0.15]  # A320/B737 最常见

# 一天内各小时的流量权重（模拟机场高峰：早7-9、晚18-20）
HOURLY_WEIGHTS = [
    0.5, 0.3, 0.2, 0.2, 0.3, 0.5,   # 0-5
    1.5, 3.0, 3.5, 2.5, 2.0, 1.8,   # 6-11
    2.0, 2.2, 2.0, 1.8, 2.0, 2.5,   # 12-17
    3.0, 3.5, 2.5, 1.5, 0.8, 0.5,   # 18-23
]

# ─── Payload 生成 ─────────────────────────────────────────────

def generate_payload(hour: int = None):
    """生成一条航班请求，hour 控制 arrival_time 分布"""
    if hour is None:
        # 按小时权重随机选择
        hour = random.choices(range(24), weights=HOURLY_WEIGHTS, k=1)[0]

    aircraft_type = random.choices(AIRCRAFT_TYPES, weights=AIRCRAFT_WEIGHTS, k=1)[0]

    # 客流量：高峰时段（7-9, 18-20）偏大
    if 7 <= hour <= 9 or 18 <= hour <= 20:
        passenger_count = int(random.gauss(280, 60))
    elif 0 <= hour <= 5 or 22 <= hour <= 23:
        passenger_count = int(random.gauss(120, 40))
    else:
        passenger_count = int(random.gauss(200, 70))
    passenger_count = max(50, min(400, passenger_count))

    # 国际航班比例：高峰时段更多国际航班
    intl_prob = 0.55 if (7 <= hour <= 9 or 18 <= hour <= 20) else 0.35
    is_international = random.random() < intl_prob

    return {
        "FlightInfo": {
            "aircraft_type": aircraft_type,
            "arrival_time": hour,
            "is_international": is_international,
            "passenger_count": passenger_count,
        },
        "GateResult": {}
    }


# ─── ClickHouse 直写（历史数据） ──────────────────────────────

def clickhouse_insert(ch_url: str, rows: list):
    """批量写入 execution_var_log"""
    if not rows:
        return
    # ClickHouse HTTP 接口：INSERT INTO ... FORMAT JSONEachRow
    sql = "INSERT INTO execution_var_log FORMAT JSONEachRow"
    data = "\n".join(json.dumps(r) for r in rows)
    url = f"{ch_url}/?query={urllib.parse.quote(sql)}"
    req = urllib.request.Request(
        url,
        data=data.encode("utf-8"),
        headers={"Content-Type": "application/x-ndjson"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        resp.read()


def build_var_rows(execution_id: str, project: str, package_id: str,
                   payload: dict, ts_ms: int) -> list:
    """把一次规则执行的 payload 展开成 execution_var_log 行"""
    rows = []
    ts_sec = ts_ms / 1000.0
    created_at = datetime.fromtimestamp(ts_sec, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    flight = payload["FlightInfo"]

    def make_row(var_category, var_name, var_type, val_num, val_str, io_type):
        row = {
            "execution_id": execution_id,
            "project": project,
            "package_id": package_id,
            "flow_id": "gate_allocation_flow",
            "var_category": var_category,
            "var_name": var_name,
            "var_type": var_type,
            "io_type": io_type,
            "exec_ms": random.randint(5, 80),
            "created_at": created_at,
        }
        # val_num: None → 不写入（ClickHouse 用默认值），避免 Nullable 问题
        if val_num is not None:
            row["val_num"] = val_num
        if val_str is not None:
            row["val_str"] = val_str
        return row

    # FlightInfo 输入变量
    rows.append(make_row("FlightInfo", "aircraft_type",    "String",  None, flight["aircraft_type"], "input"))
    rows.append(make_row("FlightInfo", "arrival_time",     "Integer", float(flight["arrival_time"]), None, "input"))
    rows.append(make_row("FlightInfo", "is_international", "Boolean", 1.0 if flight["is_international"] else 0.0, None, "input"))

    pax = flight.get("passenger_count")
    if pax is not None:
        rows.append(make_row("FlightInfo", "passenger_count", "Integer", float(pax), None, "input"))
    else:
        # 缺失值：val_num 和 val_str 都不写，模拟 NULL
        rows.append(make_row("FlightInfo", "passenger_count", "Integer", None, None, "input"))

    # GateResult 输出变量（模拟规则引擎结果）
    gate_type = "近机位" if (flight["is_international"] or (pax or 0) > 250) else "远机位"
    priority_score = min(100, int(
        (50 if flight["is_international"] else 0) +
        (40 if (pax or 0) > 200 else 20) +
        (20 if flight["arrival_time"] < 10 else 0)
    ))
    assigned_gate = random.choice(["A区-101", "A区-102", "B区-201", "B区-202", "B区-203", "B区-205"])

    rows.append(make_row("GateResult", "gate_type",      "String",  None, gate_type, "output"))
    rows.append(make_row("GateResult", "assigned_gate",  "String",  None, assigned_gate, "output"))
    rows.append(make_row("GateResult", "priority_score", "Integer", float(priority_score), None, "output"))
    rows.append(make_row("GateResult", "reason",         "String",  None, "模拟数据", "output"))

    return rows


def simulate_history(ch_url: str, project: str, package_id: str,
                     days: int, daily_count: int):
    """生成过去 N 天的历史数据，直接写入 ClickHouse"""
    import urllib.parse

    print(f"\n[历史数据] 生成过去 {days} 天，每天约 {daily_count} 条执行记录...")
    now = datetime.now(tz=timezone.utc)

    for day_offset in range(days, 0, -1):
        day = now - timedelta(days=day_offset)
        day_str = day.strftime("%Y-%m-%d")

        # 按小时权重分配当天的请求数
        total_weight = sum(HOURLY_WEIGHTS)
        batch_rows = []
        exec_count = 0

        for hour in range(24):
            hour_count = int(daily_count * HOURLY_WEIGHTS[hour] / total_weight)
            if hour_count == 0:
                continue

            for _ in range(hour_count):
                # 在该小时内随机一个时间点
                minute = random.randint(0, 59)
                second = random.randint(0, 59)
                ms = random.randint(0, 999)
                ts = day.replace(hour=hour, minute=minute, second=second,
                                 microsecond=ms * 1000)
                ts_ms = int(ts.timestamp() * 1000)

                exec_id = f"sim-{day_str}-{hour:02d}-{exec_count:05d}"
                payload = generate_payload(hour)

                # 偶尔注入缺失值（模拟数据质量问题）
                if random.random() < 0.03:  # 3% 缺失率
                    payload["FlightInfo"]["passenger_count"] = None

                rows = build_var_rows(exec_id, project, package_id, payload, ts_ms)
                batch_rows.extend(rows)
                exec_count += 1

                # 每 500 条执行记录批量写入
                if len(batch_rows) >= 500 * 8:  # 8 vars per execution
                    clickhouse_insert(ch_url, batch_rows)
                    batch_rows = []

        # 写入剩余
        if batch_rows:
            clickhouse_insert(ch_url, batch_rows)

        print(f"  {day_str}: 写入 {exec_count} 条执行记录")

    print("[历史数据] 完成")


# ─── 实时流量模拟（调用 API） ─────────────────────────────────

def send_request(url: str, payload: dict):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        status = e.code
        body = e.read().decode("utf-8")
    except Exception as e:
        status = -1
        body = str(e)
    elapsed = time.time() - start
    try:
        data = json.loads(body)
        if data.get("code") != 200:
            status = data.get("code", 500)
    except Exception:
        pass
    return status, elapsed


def simulate_realtime(api_url: str, total: int, concurrency: int):
    """模拟当天实时流量，按小时权重分批发送"""
    print(f"\n[实时流量] 发送 {total} 条请求 (并发={concurrency})...")
    now = datetime.now()
    current_hour = now.hour

    # 只模拟到当前小时的流量
    weights_so_far = HOURLY_WEIGHTS[:current_hour + 1]
    total_w = sum(weights_so_far)

    success = fail = 0
    total_time = 0.0
    wall_start = time.time()

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = []
        for _ in range(total):
            # 按当天已过时段的权重随机选小时
            hour = random.choices(range(current_hour + 1), weights=weights_so_far, k=1)[0]
            payload = generate_payload(hour)
            futures.append(executor.submit(send_request, api_url, payload))

        for i, future in enumerate(concurrent.futures.as_completed(futures)):
            status, elapsed = future.result()
            total_time += elapsed
            if status == 200:
                success += 1
            else:
                fail += 1
            if (i + 1) % 100 == 0:
                print(f"  进度: {i+1}/{total} (成功={success}, 失败={fail})")

    wall = time.time() - wall_start
    print(f"\n[实时流量] 完成")
    print(f"  总请求: {total}  成功: {success}  失败: {fail}")
    print(f"  总耗时: {wall:.1f}s  平均响应: {total_time/total*1000:.1f}ms  RPS: {total/wall:.1f}")


# ─── 主入口 ───────────────────────────────────────────────────

def main():
    import urllib.parse  # noqa: ensure available

    parser = argparse.ArgumentParser(description="机场登机口分配 - 7天监控数据模拟")
    parser.add_argument("--mode", choices=["all", "history", "realtime"], default="all",
                        help="运行模式: all=历史+实时, history=仅历史, realtime=仅实时")
    parser.add_argument("--server", default="http://localhost:16009",
                        help="ruleuler-server 地址")
    parser.add_argument("--client", default="http://localhost:16001",
                        help="ruleuler-client 地址（规则执行）")
    parser.add_argument("--clickhouse", default="http://localhost:8123",
                        help="ClickHouse HTTP 地址")
    parser.add_argument("--project", default="airport_gate_allocation_db")
    parser.add_argument("--package-id", default="gate_allocation_pkg")
    parser.add_argument("--days", type=int, default=7, help="历史天数")
    parser.add_argument("--daily-count", type=int, default=800,
                        help="每天模拟执行次数")
    parser.add_argument("--realtime-count", type=int, default=300,
                        help="今天实时模拟次数")
    parser.add_argument("--concurrency", type=int, default=10,
                        help="实时模拟并发数")
    args = parser.parse_args()

    # 规则引擎执行 URL
    api_url = (f"{args.client}/urule/rest/ruleset/execute"
               f"?project={args.project}&id={args.package_id}")

    print("=" * 55)
    print("  机场登机口分配 - 监控数据模拟")
    print("=" * 55)
    print(f"  项目: {args.project}")
    print(f"  知识包: {args.package_id}")
    print(f"  模式: {args.mode}")
    if args.mode in ("all", "history"):
        print(f"  历史: 过去 {args.days} 天，每天 ~{args.daily_count} 次")
    if args.mode in ("all", "realtime"):
        print(f"  实时: 今天 {args.realtime_count} 次 (并发={args.concurrency})")
    print()

    if args.mode in ("all", "history"):
        simulate_history(
            ch_url=args.clickhouse,
            project=args.project,
            package_id=args.package_id,
            days=args.days,
            daily_count=args.daily_count,
        )

    if args.mode in ("all", "realtime"):
        simulate_realtime(
            api_url=api_url,
            total=args.realtime_count,
            concurrency=args.concurrency,
        )

    print("\n完成！可在监控页面查看数据：")
    print(f"  {args.server}/admin/monitoring/realtime")


if __name__ == "__main__":
    import urllib.parse
    main()

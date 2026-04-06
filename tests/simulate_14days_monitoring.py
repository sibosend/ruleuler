#!/usr/bin/env python3
"""
14天监控数据模拟脚本（含清库）

功能：
  1. 清空 ClickHouse execution_var_log / execution_var_log_5m
  2. 清空 MySQL ruleuler_variable_daily_stats
  3. 模拟过去14天历史数据（每天10000条，含周内流量波动）
  4. 可选：模拟当天实时流量（调用规则引擎 API）

流量波动模型：
  - 工作日（Mon-Fri）：满流量 × (0.85 ~ 1.10) 波动
  - 周六：× 0.60
  - 周日：× 0.45

用法：
  # 清库 + 14天历史
  python tests/simulate_14days_monitoring.py

  # 清库 + 14天历史 + 今天实时流量
  python tests/simulate_14days_monitoring.py --mode all

  # 只清库
  python tests/simulate_14days_monitoring.py --mode clean

  # 自定义参数
  python tests/simulate_14days_monitoring.py --daily-count 10000 --concurrency 20
"""

import argparse
import json
import random
import time
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timedelta, timezone

# ─── 数据分布配置 ─────────────────────────────────────────────

AIRCRAFT_TYPES = ["A320", "B737", "A380", "B777", "A350"]
AIRCRAFT_WEIGHTS = [0.30, 0.30, 0.10, 0.15, 0.15]

# 一天内各小时的流量权重
HOURLY_WEIGHTS = [
    0.5, 0.3, 0.2, 0.2, 0.3, 0.5,   # 0-5
    1.5, 3.0, 3.5, 2.5, 2.0, 1.8,   # 6-11
    2.0, 2.2, 2.0, 1.8, 2.0, 2.5,   # 12-17
    3.0, 3.5, 2.5, 1.5, 0.8, 0.5,   # 18-23
]

# ─── Payload 生成 ─────────────────────────────────────────────

def generate_payload(hour: int = None):
    if hour is None:
        hour = random.choices(range(24), weights=HOURLY_WEIGHTS, k=1)[0]

    aircraft_type = random.choices(AIRCRAFT_TYPES, weights=AIRCRAFT_WEIGHTS, k=1)[0]

    if 7 <= hour <= 9 or 18 <= hour <= 20:
        passenger_count = int(random.gauss(280, 60))
    elif 0 <= hour <= 5 or 22 <= hour <= 23:
        passenger_count = int(random.gauss(120, 40))
    else:
        passenger_count = int(random.gauss(200, 70))
    passenger_count = max(50, min(400, passenger_count))

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


# ─── ClickHouse 操作 ──────────────────────────────────────────

def ch_query(ch_url: str, sql: str):
    """执行 ClickHouse SQL（无返回）"""
    url = f"{ch_url}/?query={urllib.parse.quote(sql)}"
    req = urllib.request.Request(url, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode()


def ch_insert(ch_url: str, rows: list, max_retries: int = 3):
    """批量写入 execution_var_log，带重试和错误详情"""
    if not rows:
        return
    sql = "INSERT INTO execution_var_log FORMAT JSONEachRow"
    data = "\n".join(json.dumps(r) for r in rows)
    url = f"{ch_url}/?query={urllib.parse.quote(sql)}"
    for attempt in range(max_retries):
        try:
            req = urllib.request.Request(
                url,
                data=data.encode("utf-8"),
                headers={"Content-Type": "application/x-ndjson"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=120) as resp:
                resp.read()
            return
        except urllib.error.HTTPError as e:
            body = e.read().decode()
            if attempt < max_retries - 1:
                print(f"    CH insert 失败 (attempt {attempt+1}): {body[:200]}，重试...")
                time.sleep(2)
            else:
                print(f"    CH insert 最终失败: {body[:300]}")
                raise


# ─── 清库 ─────────────────────────────────────────────────────

def clean_all(ch_url: str, mysql_config: dict, project: str, package_id: str):
    print("\n[清库] 开始清理...")

    # ClickHouse: 清空两张表
    # 先 DROP MV 再 TRUNCATE，避免插入过程中物化视图触发
    print("  ClickHouse: TRUNCATE execution_var_log")
    try:
        ch_query(ch_url, "TRUNCATE TABLE execution_var_log")
    except Exception as e:
        print(f"    失败（表可能为空）: {e}")

    print("  ClickHouse: TRUNCATE execution_var_log_5m")
    try:
        ch_query(ch_url, "TRUNCATE TABLE execution_var_log_5m")
    except Exception as e:
        print(f"    失败（表可能为空）: {e}")

    # MySQL: 删除对应项目的 daily_stats
    print("  MySQL: DELETE ruleuler_variable_daily_stats")
    import pymysql
    conn = pymysql.connect(
        host=mysql_config["host"],
        port=mysql_config["port"],
        user=mysql_config["user"],
        password=mysql_config["password"],
        database=mysql_config["database"],
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "DELETE FROM ruleuler_variable_daily_stats WHERE project = %s AND package_id = %s",
                (project, package_id),
            )
        conn.commit()
        print(f"    删除 {cur.rowcount} 行")
    finally:
        conn.close()

    print("[清库] 完成\n")


# ─── 历史数据生成 ─────────────────────────────────────────────

def build_var_rows(execution_id: str, project: str, package_id: str,
                   payload: dict, created_at: datetime) -> list:
    """把一次规则执行展开成 execution_var_log 行，created_at 是 UTC datetime"""
    rows = []
    # ClickHouse DateTime64(3) 格式
    created_at_str = created_at.strftime("%Y-%m-%d %H:%M:%S.") + f"{created_at.microsecond // 1000:03d}"

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
            "created_at": created_at_str,
        }
        if val_num is not None:
            row["val_num"] = val_num
        if val_str is not None:
            row["val_str"] = val_str
        return row

    # 输入变量
    rows.append(make_row("FlightInfo", "aircraft_type",    "String",  None, flight["aircraft_type"], "input"))
    rows.append(make_row("FlightInfo", "arrival_time",     "Integer", float(flight["arrival_time"]), None, "input"))
    rows.append(make_row("FlightInfo", "is_international", "Boolean", 1.0 if flight["is_international"] else 0.0, None, "input"))

    pax = flight.get("passenger_count")
    if pax is not None:
        rows.append(make_row("FlightInfo", "passenger_count", "Integer", float(pax), None, "input"))
    else:
        rows.append(make_row("FlightInfo", "passenger_count", "Integer", None, None, "input"))

    # 输出变量
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


def daily_volume_factor(day: datetime) -> float:
    """周内流量波动因子"""
    weekday = day.weekday()  # 0=Mon ... 6=Sun
    if weekday < 5:  # 工作日
        return random.uniform(0.85, 1.10)
    elif weekday == 5:  # 周六
        return random.uniform(0.55, 0.65)
    else:  # 周日
        return random.uniform(0.40, 0.50)


def simulate_history(ch_url: str, project: str, package_id: str,
                     days: int, base_daily_count: int):
    print(f"[历史数据] 生成过去 {days} 天，基准每天 {base_daily_count} 条，含周内波动...")

    # 用当前 UTC 时间作为锚点，确保 today() 在 ClickHouse 侧也对得上
    now_utc = datetime.now(tz=timezone.utc)

    total_exec = 0

    for day_offset in range(days, -1, -1):
        # 目标日期：now - day_offset 天，保留 UTC
        day_utc = now_utc - timedelta(days=day_offset)
        day_str = day_utc.strftime("%Y-%m-%d")

        # 计算当天实际执行量
        factor = daily_volume_factor(day_utc)
        daily_count = int(base_daily_count * factor)

        total_weight = sum(HOURLY_WEIGHTS)
        batch_rows = []
        exec_count = 0

        # 今天只模拟到当前小时
        current_hour = now_utc.hour if day_offset == 0 else 24
        max_hour = min(24, current_hour + 1)

        for hour in range(max_hour):
            hour_count = int(daily_count * HOURLY_WEIGHTS[hour] / total_weight)
            if hour_count == 0:
                continue

            for _ in range(hour_count):
                minute = random.randint(0, 59)
                second = random.randint(0, 59)
                ms = random.randint(0, 999)

                # 构造精确的 UTC 时间戳
                ts = day_utc.replace(hour=hour, minute=minute, second=second,
                                     microsecond=ms * 1000)
                # 今天的时间不能超过当前时刻
                if day_offset == 0 and ts > now_utc:
                    continue

                exec_id = f"sim-{day_str}-{hour:02d}-{exec_count:05d}"
                payload = generate_payload(hour)

                # 注入缺失值
                if random.random() < 0.03:
                    payload["FlightInfo"]["passenger_count"] = None

                rows = build_var_rows(exec_id, project, package_id, payload, ts)
                batch_rows.extend(rows)
                exec_count += 1

                if len(batch_rows) >= 2000:
                    ch_insert(ch_url, batch_rows)
                    batch_rows = []

        if batch_rows:
            ch_insert(ch_url, batch_rows)

        weekday_name = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][day_utc.weekday()]
        print(f"  {day_str} ({weekday_name}): factor={factor:.2f} → {exec_count} 条执行记录")

        total_exec += exec_count

    print(f"[历史数据] 完成，共 {total_exec} 条执行记录\n")


# ─── 实时流量模拟 ─────────────────────────────────────────────

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
    import concurrent.futures

    print(f"[实时流量] 发送 {total} 条请求 (并发={concurrency})...")
    current_hour = datetime.now().hour
    weights_so_far = HOURLY_WEIGHTS[:current_hour + 1]

    success = fail = 0
    total_time = 0.0
    wall_start = time.time()

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = []
        for _ in range(total):
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
    print(f"[实时流量] 完成  总请求={total}  成功={success}  失败={fail}  "
          f"耗时={wall:.1f}s  RPS={total/wall:.1f}\n")


# ─── 主入口 ───────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="14天监控数据模拟（含清库）")
    parser.add_argument("--mode", choices=["all", "clean", "history", "realtime"], default="history",
                        help="clean=仅清库, history=清库+历史, realtime=仅实时, all=全部")
    parser.add_argument("--server", default="http://localhost:16009")
    parser.add_argument("--client", default="http://localhost:16001")
    parser.add_argument("--clickhouse", default="http://localhost:8123")
    parser.add_argument("--mysql-host", default="127.0.0.1")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="asdfg123")
    parser.add_argument("--mysql-database", default="ruleuler_data")
    parser.add_argument("--project", default="airport_gate_allocation_db")
    parser.add_argument("--package-id", default="gate_pkg")
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--daily-count", type=int, default=10000,
                        help="基准每日执行次数（工作日），周末自动打折")
    parser.add_argument("--realtime-count", type=int, default=300)
    parser.add_argument("--concurrency", type=int, default=10)
    args = parser.parse_args()

    mysql_config = {
        "host": args.mysql_host,
        "port": args.mysql_port,
        "user": args.mysql_user,
        "password": args.mysql_password,
        "database": args.mysql_database,
    }

    print("=" * 55)
    print("  14天监控数据模拟（含清库 + 周内流量波动）")
    print("=" * 55)
    print(f"  项目: {args.project}")
    print(f"  知识包: {args.package_id}")
    print(f"  模式: {args.mode}")
    if args.mode in ("all", "history", "clean"):
        print(f"  历史: 过去 {args.days} 天，基准 {args.daily_count}/天")
    if args.mode in ("all", "realtime"):
        print(f"  实时: 今天 {args.realtime_count} 次 (并发={args.concurrency})")
    print()

    # 清库（history / all / clean 模式都执行）
    if args.mode in ("clean", "history", "all"):
        clean_all(args.clickhouse, mysql_config, args.project, args.package_id)

    if args.mode in ("all", "history"):
        simulate_history(
            ch_url=args.clickhouse,
            project=args.project,
            package_id=args.package_id,
            days=args.days,
            base_daily_count=args.daily_count,
        )

    if args.mode in ("all", "realtime"):
        api_url = (f"{args.client}/urule/rest/ruleset/execute"
                   f"?project={args.project}&id={args.package_id}")
        simulate_realtime(api_url, args.realtime_count, args.concurrency)

    print("完成！可在监控页面查看数据：")
    print(f"  {args.server}/admin/monitoring/realtime")


if __name__ == "__main__":
    main()

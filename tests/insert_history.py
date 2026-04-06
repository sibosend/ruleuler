#!/usr/bin/env python3
"""快速插入7天历史数据，每天约10000次执行"""
import json, random, urllib.request, urllib.parse
from datetime import datetime, timedelta, timezone

PROJECT = "airport_gate_allocation_db"
PKG_ID  = "gate_allocation_pkg"
CH      = "http://localhost:8123/"
DAILY   = 10000
DAYS    = 7

AIRCRAFT = ["A320","B737","A380","B777","A350"]
AW       = [0.30, 0.30, 0.10, 0.15, 0.15]
HW       = [0.5,0.3,0.2,0.2,0.3,0.5, 1.5,3.0,3.5,2.5,2.0,1.8,
            2.0,2.2,2.0,1.8,2.0,2.5, 3.0,3.5,2.5,1.5,0.8,0.5]

def ch_post(sql, data=""):
    url = CH + ("?query=" + urllib.parse.quote(sql) if data else "")
    body = (sql if not data else data).encode()
    req = urllib.request.Request(CH + "?query=" + urllib.parse.quote(sql) if data else CH,
                                  data=body, method="POST")
    if data:
        req = urllib.request.Request(CH + "?query=" + urllib.parse.quote(sql),
                                      data=data.encode(), method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode()

def insert_batch(rows):
    sql = "INSERT INTO execution_var_log FORMAT JSONEachRow"
    ndjson = "\n".join(json.dumps(r) for r in rows)
    req = urllib.request.Request(
        CH + "?query=" + urllib.parse.quote(sql),
        data=ndjson.encode(), method="POST"
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        r.read()

def make_rows(eid, hour, ts_str, missing_pax=False):
    ac   = random.choices(AIRCRAFT, weights=AW)[0]
    intl = random.random() < (0.55 if 7<=hour<=9 or 18<=hour<=20 else 0.35)
    if 7<=hour<=9 or 18<=hour<=20:
        pax = int(random.gauss(280,60))
    elif hour<=5 or hour>=22:
        pax = int(random.gauss(120,40))
    else:
        pax = int(random.gauss(200,70))
    pax = max(50, min(400, pax))

    base = dict(execution_id=eid, project=PROJECT, package_id=PKG_ID,
                flow_id="gate_allocation_flow", exec_ms=random.randint(5,80),
                created_at=ts_str)

    rows = [
        {**base, "var_category":"FlightInfo","var_name":"aircraft_type",   "var_type":"String", "val_str":ac,                          "io_type":"input"},
        {**base, "var_category":"FlightInfo","var_name":"arrival_time",    "var_type":"Integer","val_num":float(hour),                  "io_type":"input"},
        {**base, "var_category":"FlightInfo","var_name":"is_international","var_type":"Boolean","val_num":1.0 if intl else 0.0,         "io_type":"input"},
    ]
    if not missing_pax:
        rows.append({**base,"var_category":"FlightInfo","var_name":"passenger_count","var_type":"Integer","val_num":float(pax),"io_type":"input"})
    else:
        rows.append({**base,"var_category":"FlightInfo","var_name":"passenger_count","var_type":"Integer","io_type":"input"})

    gate_type = "近机位" if (intl or pax>250) else "远机位"
    score = min(100, (50 if intl else 0)+(40 if pax>200 else 20)+(20 if hour<10 else 0))
    gate  = random.choice(["A区-101","A区-102","B区-201","B区-202","B区-203","B区-205"])
    rows += [
        {**base,"var_category":"GateResult","var_name":"gate_type",     "var_type":"String", "val_str":gate_type,    "io_type":"output"},
        {**base,"var_category":"GateResult","var_name":"assigned_gate", "var_type":"String", "val_str":gate,         "io_type":"output"},
        {**base,"var_category":"GateResult","var_name":"priority_score","var_type":"Integer","val_num":float(score), "io_type":"output"},
        {**base,"var_category":"GateResult","var_name":"reason",        "var_type":"String", "val_str":"模拟数据",   "io_type":"output"},
    ]
    return rows

now = datetime.now(tz=timezone.utc)
total_w = sum(HW)

for d in range(DAYS, 0, -1):
    day = now - timedelta(days=d)
    day_str = day.strftime("%Y-%m-%d")
    batch, n = [], 0
    for h in range(24):
        cnt = int(DAILY * HW[h] / total_w)
        for _ in range(cnt):
            m, s, ms = random.randint(0,59), random.randint(0,59), random.randint(0,999)
            ts = day.replace(hour=h, minute=m, second=s, microsecond=ms*1000)
            ts_str = ts.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
            eid = f"sim-{day_str}-{h:02d}-{n:05d}"
            missing = random.random() < 0.03
            batch.extend(make_rows(eid, h, ts_str, missing))
            n += 1
            if len(batch) >= 8000:
                insert_batch(batch); batch = []
    if batch:
        insert_batch(batch)
    print(f"  {day_str}: {n} 次执行")

print("完成")

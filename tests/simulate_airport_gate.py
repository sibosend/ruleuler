import argparse
import concurrent.futures
import json
import random
import time
import urllib.request
import urllib.error

def generate_payload():
    aircraft_types = ["A320", "B737", "A380", "B777", "A350"]
    return {
        "FlightInfo": {
            "aircraft_type": random.choice(aircraft_types),
            "arrival_time": random.randint(0, 23),
            "is_international": random.choice([True, False]),
            "passenger_count": random.randint(50, 400)
        },
        "GateResult": {}
    }

def send_request(url):
    req = urllib.request.Request(
        url,
        data=json.dumps(generate_payload()).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    start_time = time.time()
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            status = resp.status
            body = resp.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        status = e.code
        body = e.read().decode('utf-8')
    except Exception as e:
        status = -1
        body = str(e)
    
    elapsed = time.time() - start_time
    # Parse RespDTO from json
    try:
        data = json.loads(body)
        if data.get('code') != 200:
            status = data.get('code', 500)
    except Exception:
        pass
    return status, elapsed, body

def main():
    parser = argparse.ArgumentParser(description="simulate airport gate allocation requests")
    parser.add_argument("--url", required=True, help="API URL to rule endpoint")
    parser.add_argument("--count", type=int, required=True, help="Number of total requests")
    parser.add_argument("--concurrency", type=int, required=True, help="Max concurrent connections")
    
    args = parser.parse_args()

    start_time = time.time()
    success_count = 0
    fail_count = 0
    total_time = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(send_request, args.url) for _ in range(args.count)]
        for future in concurrent.futures.as_completed(futures):
            status, elapsed, _ = future.result()
            total_time += elapsed
            if status == 200:
                success_count += 1
            else:
                fail_count += 1
                
    wall_time = time.time() - start_time
    
    print("\n--- 压测结果 ---")
    print(f"总请求数    : {args.count}")
    print(f"成功(200)   : {success_count}")
    print(f"失败        : {fail_count}")
    print(f"总耗时      : {wall_time:.2f} s")
    if args.count > 0:
        print(f"平均响应时间: {(total_time/args.count)*1000:.2f} ms")
    if wall_time > 0:
        print(f"RPS         : {args.count / wall_time:.2f} req/s")

if __name__ == "__main__":
    main()

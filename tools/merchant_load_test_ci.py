#!/usr/bin/env python3
"""Read-only Transiva Merchant load test for CI.
Only sends authenticated GET requests to getMerchantOrders.php?load_test=1.
Never changes order state.
"""
from __future__ import annotations
import argparse
import concurrent.futures
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path


def percentile(values, q):
    if not values:
        return 0.0
    s = sorted(values)
    idx = max(0, min(len(s) - 1, math.ceil(q * len(s)) - 1))
    return float(s[idx])


def classify(total, codes, avg_ms, p95_ms, p99_ms):
    ok = int(codes.get("200", 0))
    success_rate = (ok / total * 100.0) if total else 0.0
    server_errors = sum(int(v) for k, v in codes.items() if k in {"500", "502", "503", "504", "-1"})

    if server_errors > 0 or success_rate < 95.0 or p95_ms > 5000:
        return "GAGAL", success_rate
    if success_rate < 99.0 or avg_ms > 1000 or p95_ms > 2000 or p99_ms > 3000:
        return "PERLU PERBAIKAN", success_rate
    return "AMAN", success_rate


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--base", required=True)
    p.add_argument("--token", required=True)
    p.add_argument("--device", required=True)
    p.add_argument("--users", type=int, required=True)
    p.add_argument("--requests", type=int, required=True)
    p.add_argument("--timeout", type=float, default=15.0)
    p.add_argument("--json-out", default="stress-result.json")
    p.add_argument("--summary-out", default="stress-summary.md")
    a = p.parse_args()

    if not (1 <= a.users <= 200):
        raise SystemExit("users harus 1..200")
    if not (1 <= a.requests <= 5000):
        raise SystemExit("requests harus 1..5000")
    if not a.base.lower().startswith("https://"):
        raise SystemExit("base URL harus HTTPS")
    if not a.token.strip() or not a.device.strip():
        raise SystemExit("token/device tidak boleh kosong")

    url = a.base.rstrip("/") + "/getMerchantOrders.php?load_test=1"

    def once(_):
        req = urllib.request.Request(
            url,
            method="GET",
            headers={
                "Authorization": "Bearer " + a.token,
                "X-Device-UUID": a.device,
                "Cache-Control": "no-cache",
                "User-Agent": "Transiva-GitHub-Stress-Test/1.0",
            },
        )
        t0 = time.perf_counter()
        code = -1
        try:
            with urllib.request.urlopen(req, timeout=a.timeout) as r:
                code = int(r.status)
                r.read()
        except urllib.error.HTTPError as e:
            code = int(e.code)
            try:
                e.read()
            except Exception:
                pass
        except Exception:
            code = -1
        return code, (time.perf_counter() - t0) * 1000.0

    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.users) as ex:
        results = list(ex.map(once, range(a.requests)))
    elapsed = max(time.perf_counter() - start, 0.001)

    codes = Counter(str(c) for c, _ in results)
    times = [ms for _, ms in results]
    avg_ms = statistics.mean(times) if times else 0.0
    p95_ms = percentile(times, 0.95)
    p99_ms = percentile(times, 0.99)
    status, success_rate = classify(a.requests, codes, avg_ms, p95_ms, p99_ms)

    payload = {
        "status": status,
        "endpoint": url,
        "requests": a.requests,
        "concurrency": a.users,
        "seconds": round(elapsed, 2),
        "rps": round(a.requests / elapsed, 2),
        "success_rate_pct": round(success_rate, 2),
        "http_codes": dict(sorted(codes.items())),
        "avg_ms": round(avg_ms, 1),
        "p95_ms": round(p95_ms, 1),
        "p99_ms": round(p99_ms, 1),
    }
    Path(a.json_out).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    icon = {"AMAN": "✅", "PERLU PERBAIKAN": "⚠️", "GAGAL": "❌"}[status]
    md = f"""# {icon} Transiva Merchant Stress Test — {status}

| Parameter | Hasil |
|---|---:|
| Concurrent users | **{a.users}** |
| Total requests | **{a.requests}** |
| HTTP 200 success | **{success_rate:.2f}%** |
| Throughput | **{payload['rps']} req/s** |
| Average | **{payload['avg_ms']} ms** |
| P95 | **{payload['p95_ms']} ms** |
| P99 | **{payload['p99_ms']} ms** |
| HTTP codes | `{json.dumps(payload['http_codes'], sort_keys=True)}` |
| Durasi | **{payload['seconds']} s** |

## Penilaian otomatis
- **AMAN**: HTTP 200 ≥99%, tidak ada 5xx/network error, avg ≤1000 ms, P95 ≤2000 ms, P99 ≤3000 ms.
- **PERLU PERBAIKAN**: masih berjalan tetapi melewati salah satu target di atas.
- **GAGAL**: ada 5xx/network error, success <95%, atau P95 >5000 ms.

> Tool ini read-only dan hanya melakukan GET ke `getMerchantOrders.php?load_test=1`. Tetap jalankan bertahap karena request nyata tetap memakai resource PHP/MySQL server.
"""
    Path(a.summary_out).write_text(md, encoding="utf-8")
    print(json.dumps(payload, indent=2))

    # Fail CI only on truly failed test; warning remains green so result can be inspected easily.
    return 2 if status == "GAGAL" else 0


if __name__ == "__main__":
    sys.exit(main())

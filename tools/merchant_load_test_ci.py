#!/usr/bin/env python3
"""Transiva Merchant read-only stress test V3.

Authenticated GET only. Captures bounded diagnostic samples for non-200/network errors.
It never changes order state and never writes credentials to artifacts.
"""
from __future__ import annotations
import argparse, concurrent.futures, json, math, statistics, sys, time
import urllib.error, urllib.request
from collections import Counter
from pathlib import Path

MAX_ERROR_SAMPLES = 20
MAX_BODY_CHARS = 1200


def percentile(values, q):
    if not values: return 0.0
    s = sorted(values)
    idx = max(0, min(len(s)-1, math.ceil(q*len(s))-1))
    return float(s[idx])


def classify(total, codes, avg_ms, p95_ms, p99_ms):
    ok = int(codes.get("200", 0))
    success_rate = (ok / total * 100.0) if total else 0.0
    server_errors = sum(int(v) for k,v in codes.items() if k in {"500","502","503","504","-1"})
    if server_errors > 0 or success_rate < 95.0 or avg_ms > 3000 or p95_ms > 5000 or p99_ms > 10000:
        return "GAGAL", success_rate
    if success_rate < 99.0 or avg_ms > 1500 or p95_ms > 2500 or p99_ms > 5000:
        return "PERLU PERBAIKAN", success_rate
    return "AMAN", success_rate


def safe_body(raw: bytes) -> str:
    text = raw.decode("utf-8", errors="replace").strip()
    return text[:MAX_BODY_CHARS]


def parse_json_code_message(body: str):
    try:
        obj=json.loads(body)
        if isinstance(obj, dict):
            return str(obj.get("code", ""))[:120], str(obj.get("message", ""))[:300]
    except Exception:
        pass
    return "", ""


def main():
    p=argparse.ArgumentParser()
    p.add_argument("--base", required=True); p.add_argument("--token", required=True)
    p.add_argument("--device", required=True); p.add_argument("--stress-key", required=True)
    p.add_argument("--users", type=int, required=True); p.add_argument("--requests", type=int, required=True)
    p.add_argument("--timeout", type=float, default=15.0)
    p.add_argument("--json-out", default="stress-result.json")
    p.add_argument("--summary-out", default="stress-summary.md")
    a=p.parse_args()
    if not (1 <= a.users <= 200): raise SystemExit("users harus 1..200")
    if not (1 <= a.requests <= 5000): raise SystemExit("requests harus 1..5000")
    if not a.base.lower().startswith("https://"): raise SystemExit("base URL harus HTTPS")
    if not a.token.strip() or not a.device.strip(): raise SystemExit("token/device tidak boleh kosong")
    if len(a.stress_key.strip()) < 32: raise SystemExit("stress-key minimal 32 karakter")

    url=a.base.rstrip("/")+"/getMerchantOrders.php?load_test=1"

    def once(i):
        req=urllib.request.Request(url, method="GET", headers={
            "Authorization":"Bearer "+a.token,
            "X-Device-UUID":a.device,
            "X-Transiva-Stress-Key":a.stress_key,
            "X-App-Scope":"merchant",
            "Cache-Control":"no-cache",
            "User-Agent":"Transiva-GitHub-Stress-Test/3.0",
        })
        t0=time.perf_counter(); code=-1; body=""; err_type=""
        headers={}
        try:
            with urllib.request.urlopen(req, timeout=a.timeout) as r:
                code=int(r.status); body=safe_body(r.read())
                headers={k.lower():v for k,v in r.headers.items() if k.lower().startswith("x-transiva-")}
        except urllib.error.HTTPError as e:
            code=int(e.code); err_type="HTTPError"
            try: body=safe_body(e.read())
            except Exception: body=""
            headers={k.lower():v for k,v in e.headers.items() if k.lower().startswith("x-transiva-")} if e.headers else {}
        except Exception as e:
            code=-1; err_type=type(e).__name__; body=str(e)[:MAX_BODY_CHARS]
        ms=(time.perf_counter()-t0)*1000.0
        diag=None
        if code != 200:
            jcode,msg=parse_json_code_message(body)
            diag={"request":i+1,"http":code,"ms":round(ms,1),"error_type":err_type,"code":jcode,"message":msg,"body":body,"headers":headers}
        return code,ms,diag

    start=time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.users) as ex:
        results=list(ex.map(once, range(a.requests)))
    elapsed=max(time.perf_counter()-start,0.001)
    codes=Counter(str(c) for c,_,_ in results); times=[ms for _,ms,_ in results]
    errors=[d for _,_,d in results if d is not None][:MAX_ERROR_SAMPLES]
    avg_ms=statistics.mean(times) if times else 0.0; p95=percentile(times,.95); p99=percentile(times,.99)
    status,success_rate=classify(a.requests,codes,avg_ms,p95,p99)
    payload={"status":status,"endpoint":url,"requests":a.requests,"concurrency":a.users,"seconds":round(elapsed,2),
             "rps":round(a.requests/elapsed,2),"success_rate_pct":round(success_rate,2),"http_codes":dict(sorted(codes.items())),
             "avg_ms":round(avg_ms,1),"p95_ms":round(p95,1),"p99_ms":round(p99,1),"error_samples":errors}
    Path(a.json_out).write_text(json.dumps(payload,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

    icon={"AMAN":"✅","PERLU PERBAIKAN":"⚠️","GAGAL":"❌"}[status]
    md=f"""# {icon} Transiva Merchant Stress Test V3 — {status}

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
- **AMAN**: HTTP 200 ≥99%, tidak ada 5xx/network error, Average ≤1500 ms, P95 ≤2500 ms, P99 ≤5000 ms.
- **PERLU PERBAIKAN**: HTTP 200 <99% atau Average >1500 ms atau P95 >2500 ms atau P99 >5000 ms, tetapi belum melewati batas GAGAL.
- **GAGAL**: ada 5xx/network error, HTTP 200 <95%, Average >3000 ms, P95 >5000 ms, atau P99 >10000 ms.
"""
    if errors:
        md += "\n## Diagnostik request gagal\n"
        md += "Artifact `stress-result.json` menyimpan maksimal 20 sampel error. Token, UUID, dan stress key **tidak** ditulis ke artifact.\n\n"
        md += "| Req | HTTP | ms | code | message |\n|---:|---:|---:|---|---|\n"
        for d in errors[:10]:
            msg=(d.get("message") or d.get("body") or d.get("error_type") or "").replace("|","\\|").replace("\n"," ")[:160]
            md += f"| {d['request']} | {d['http']} | {d['ms']} | `{d.get('code','')}` | {msg} |\n"
    else:
        md += "\n## Diagnostik\nTidak ada request gagal.\n"
    md += "\n> V3 tetap memvalidasi Bearer token + Device UUID. Pada stress request dengan secret valid, hanya UPDATE `native_api_tokens.last_used_at` yang dilewati untuk menghindari contention satu token.\n"
    Path(a.summary_out).write_text(md,encoding="utf-8")
    print(json.dumps(payload,indent=2,ensure_ascii=False))
    return 2 if status=="GAGAL" else 0

if __name__=="__main__": sys.exit(main())

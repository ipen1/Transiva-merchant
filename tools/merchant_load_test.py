#!/usr/bin/env python3
"""Read-only Transiva Merchant load test. Uses authenticated GET only; never mutates orders.
Usage: python merchant_load_test.py --base https://transiva.my.id/ --token TOKEN --users 50 --requests 500
"""
import argparse, concurrent.futures, json, statistics, time, urllib.request, urllib.error

p=argparse.ArgumentParser(); p.add_argument('--base',required=True); p.add_argument('--token',required=True); p.add_argument('--users',type=int,default=50); p.add_argument('--requests',type=int,default=500); p.add_argument('--device',default='load-test-device'); a=p.parse_args()
url=a.base.rstrip('/')+'/getMerchantOrders.php?load_test=1'
def once(_):
    req=urllib.request.Request(url,headers={'Authorization':'Bearer '+a.token,'X-Device-UUID':a.device,'Cache-Control':'no-cache'})
    t=time.perf_counter(); code=0
    try:
        with urllib.request.urlopen(req,timeout=15) as r: code=r.status; r.read()
    except urllib.error.HTTPError as e: code=e.code
    except Exception: code=-1
    return code,(time.perf_counter()-t)*1000
start=time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=max(1,a.users)) as ex: results=list(ex.map(once,range(a.requests)))
elapsed=time.perf_counter()-start; times=[x[1] for x in results]; codes={}
for c,_ in results: codes[c]=codes.get(c,0)+1
times_sorted=sorted(times)
def pct(q): return times_sorted[min(len(times_sorted)-1,int(len(times_sorted)*q))] if times_sorted else 0
print(json.dumps({'requests':a.requests,'concurrency':a.users,'seconds':round(elapsed,2),'rps':round(a.requests/max(elapsed,.001),2),'http_codes':codes,'avg_ms':round(statistics.mean(times),1) if times else 0,'p95_ms':round(pct(.95),1),'p99_ms':round(pct(.99),1)},indent=2))

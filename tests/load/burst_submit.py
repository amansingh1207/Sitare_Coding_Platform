"""Manual HTTP burst test for the judging queue (Phase 2).

Needs a RUNNING backend (any provider) and a verified user:
    python tests/load/burst_submit.py --base http://localhost:8080 \
        --email burst@test.local --password Burst@123 \
        --problem 3 --count 20 --concurrency 20

Measures: submit latency, verdicts, wait-to-terminal, failures, duplicates.
This is a MANUAL step (not part of `mvn test`): it burns real judging
resources (Docker containers or Judge0 quota).
"""
import argparse
import statistics
import sys
import time
import urllib.request
import urllib.error
import json
from concurrent.futures import ThreadPoolExecutor

SOLUTION = "a, b = map(int, input().split())\nprint(a + b)\n"


def call(method, url, token=None, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--email", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--problem", type=int, required=True)
    ap.add_argument("--count", type=int, default=20)
    ap.add_argument("--concurrency", type=int, default=20)
    args = ap.parse_args()

    status, body = call("POST", args.base + "/api/auth/login",
                        payload={"email": args.email, "password": args.password})
    assert status == 200, "login failed: %r" % (body,)
    token = body["data"]["token"]
    print("logged in, firing %d concurrent submits..." % args.count)

    def submit(i):
        t0 = time.time()
        status, body = call("POST", args.base + "/api/submissions", token,
                            {"problemId": args.problem, "language": "PYTHON",
                             "sourceCode": SOLUTION})
        dt = time.time() - t0
        if status != 202:
            return {"ok": False, "latency": dt, "body": str(body)}
        return {"ok": True, "latency": dt, "id": body["data"]["id"],
                "status": body["data"]["status"]}

    t_all = time.time()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        subs = list(pool.map(submit, range(args.count)))
    ok = [s for s in subs if s["ok"]]
    print("submitted: %d/%d accepted (202), max submit latency %.2fs"
          % (len(ok), args.count, max(s["latency"] for s in subs)))

    def poll(sub):
        t0 = time.time()
        deadline = t0 + 600
        while time.time() < deadline:
            status, body = call("GET", "%s/api/submissions/%d" % (args.base, sub["id"]), token)
            st = body["data"]["status"]
            if st not in ("PENDING", "JUDGING"):
                return {"id": sub["id"], "verdict": st,
                        "wait": time.time() - t0}
            time.sleep(1.5)
        return {"id": sub["id"], "verdict": "POLL_TIMEOUT", "wait": time.time() - t0}

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(poll, ok))

    verdicts = {}
    for r in results:
        verdicts[r["verdict"]] = verdicts.get(r["verdict"], 0) + 1
    waits = [r["wait"] for r in results]
    print("verdicts:", verdicts)
    print("wait-to-terminal: min %.1fs median %.1fs max %.1fs"
          % (min(waits), statistics.median(waits), max(waits)))
    print("total wall: %.1fs" % (time.time() - t_all))
    ids = [r["id"] for r in results]
    print("duplicate ids:", len(ids) - len(set(ids)))
    if verdicts.get("ACCEPTED", 0) != args.count:
        print("NOTE: not every submission accepted - inspect verdicts above")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

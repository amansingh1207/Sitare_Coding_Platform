"""DOMjudge API test + load tool (Phase 3/4/5 evidence, rerunnable).

Talks to a DOMjudge domserver over its CCS REST API: submits source,
polls judgements, prints verdicts. Used for the POC matrix
(AC/WA/CE/RTE/TLE, multi-case, empty/large input) and the staged
concurrency runs (1/5/10/20).

Needs: a reachable domserver, a contest shortname, and a TEAM account
(admin account only for problem import, via import_problem.py flow or UI).

  python tests/load/domjudge_api_test.py matrix
  python tests/load/domjudge_api_test.py load 20

Env (never hardcoded): DOMJUDGE_BASE_URL (default http://localhost:12345),
DOMJUDGE_CONTEST (default demo), DOMJUDGE_USER / DOMJUDGE_PASSWORD
(default demo/demo).
"""
import base64
import io
import json
import os
import sys
import time
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

BASE = os.environ.get("DOMJUDGE_BASE_URL", "http://localhost:12345") + "/api/v4"
CID = os.environ.get("DOMJUDGE_CONTEST", "demo")
AUTH = (os.environ.get("DOMJUDGE_USER", "demo"),
        os.environ.get("DOMJUDGE_PASSWORD", "demo"))


def call(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    creds = ("%s:%s" % AUTH).encode()
    req.add_header("Authorization", "Basic " + base64.b64encode(creds).decode())
    with urllib.request.urlopen(req, timeout=120) as r:
        raw = r.read().decode()
        try:
            return r.status, json.loads(raw)
        except Exception:
            return r.status, raw


def src_zip(filename, content):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(filename, content)
    return base64.b64encode(buf.getvalue()).decode()


def submit(language_id, problem_id, filename, source, entry_point=None):
    body = {"language_id": language_id, "problem_id": problem_id,
            "files": [{"filename": "x.zip", "mime": "application/zip",
                       "data": src_zip(filename, source)}]}
    if entry_point:
        body["entry_point"] = entry_point
    status, body = call("POST", "/contests/%s/submissions" % CID, body)
    assert status in (200, 201), body
    return body["id"] if isinstance(body, dict) and "id" in body else body


def await_verdict(sid, budget=180):
    t0 = time.time()
    while time.time() - t0 < budget:
        time.sleep(3)
        _, js = call("GET", "/contests/%s/judgements?submission_id=%s" % (CID, sid))
        if js and js[0].get("judgement_type_id"):
            return js[0]["judgement_type_id"], round(time.time() - t0, 1)
    return "TIMEOUT-WAITING", round(time.time() - t0, 1)


HELLO_CPP = ('#include <bits/stdc++.h>\nusing namespace std;\nint main() {\n'
             '    cout << "Hello world!\\n";\n    return 0;\n}\n')
HELLO_JAVA = ('public class Main {\n'
              '    public static void main(String[] args) {\n'
              '        System.out.println("Hello world!");\n    }\n}\n')
HELLO_PY = 'print("Hello world!")\n'


def matrix():
    cases = [
        ("cpp-AC", "cpp", "hello", "hello.cpp", HELLO_CPP, None, "AC"),
        ("java-AC", "java", "hello", "Main.java", HELLO_JAVA, "Main", "AC"),
        ("py-AC", "python3", "hello", "hello.py", HELLO_PY, "hello.py", "AC"),
        ("cpp-WA", "cpp", "hello", "w.cpp",
         HELLO_CPP.replace("Hello world!", "Bye world!"), None, "WA"),
        ("cpp-CE", "cpp", "hello", "ce.cpp",
         HELLO_CPP.replace('cout << "Hello world!\\n";', 'cout << "Hello world!\\n"'),
         None, "CE"),
        ("cpp-RTE", "cpp", "hello", "re.cpp",
         '#include <bits/stdc++.h>\nusing namespace std;\nint main(){int x=1,y=0;cout<<x/y<<"\\n";return 0;}\n',
         None, "RTE"),
        ("cpp-TLE", "cpp", "hello", "tle.cpp",
         '#include <bits/stdc++.h>\nusing namespace std;\nint main(){volatile long long x=0;while(true){x+=1;}return 0;}\n',
         None, "TLE"),
    ]
    failed = 0
    for name, lang, prob, fname, src, entry, want in cases:
        sid = submit(lang, prob, fname, src, entry)
        got, waited = await_verdict(sid)
        mark = "OK " if got == want else "FAIL"
        if got != want:
            failed += 1
        print("%s %-8s want=%-4s got=%-4s sid=%s (%.1fs)" % (mark, name, want, got, sid, waited),
              flush=True)
    print("MATRIX:", "ALL_PASS" if failed == 0 else "%d FAILURES" % failed)
    return 1 if failed else 0


def load(n):
    print("firing %d concurrent submissions..." % n, flush=True)
    t_all = time.time()

    def one(_):
        try:
            sid = submit("cpp", "hello", "h.cpp", HELLO_CPP)
        except Exception as e:
            return {"ok": False, "error": "submit-failed: %r" % e}
        verdict, waited = await_verdict(sid, budget=600)
        return {"ok": verdict == "AC", "verdict": verdict, "wait": waited}

    with ThreadPoolExecutor(max_workers=n) as pool:
        results = list(pool.map(one, range(n)))
    wall = round(time.time() - t_all, 1)
    ok = [r for r in results if r.get("ok")]
    verdicts = {}
    for r in results:
        verdicts[r.get("verdict", "ERROR")] = verdicts.get(r.get("verdict", "ERROR"), 0) + 1
    waits = [r["wait"] for r in ok]
    print("submitted-ok: %d/%d" % (len(ok), n))
    print("verdicts:", verdicts)
    if waits:
        print("wait: min %.1f avg %.1f max %.1f wall %.1f" % (
            min(waits), sum(waits) / len(waits), max(waits), wall))
    for r in results:
        if not r.get("ok"):
            print("FAIL:", r)
    return 0 if len(ok) == n else 1


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "matrix"
    if mode == "matrix":
        sys.exit(matrix())
    sys.exit(load(int(sys.argv[2]) if len(sys.argv) > 2 else 5))

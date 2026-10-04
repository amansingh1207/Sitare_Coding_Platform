# Judge0 Phase 1 Proof-of-Concept (isolated, developer-only)

> This POC is deliberately NOT integrated anywhere. It does not touch the
> Docker judge, the submission flow, controllers, the database schema, or the
> frontend. See the Phase 1 report at the end of this file for results.

## What it proves

A thin Java client (`backend/src/test/java/com/codingjudge/judge0/`)
talks to a Judge0 instance over plain HTTP:

```
POST /submissions?base64_encoded=false&wait=false  ->  201 {"token": "..."}
GET  /submissions/{token}?base64_encoded=false     ->  200 {status, stdout, ...}
```

`wait=true` is never used (Judge0 docs: "we do not recommend the use of
`wait=true` because it does not scale well"). The client always polls the
token until the status leaves `1 = In Queue` / `2 = Processing`.

Status ids (Judge0 CE v1.13.1, verified from ce.judge0.com docs):
`1` In Queue, `2` Processing, `3` Accepted, `4` Wrong Answer,
`5` Time Limit Exceeded, `6` Compilation Error, `7-12` Runtime Error
variants, `13` Internal Error, `14` Exec Format Error.

Language ids are resolved at runtime via `GET /languages/all`
(non-archived match on `Python (3`, `Java (OpenJDK`, `C++ (GCC`).
On the reference setup these were Python `71`, Java `62`, C++ `54`
(exact ids depend on the Judge0 version; the test prints the ones used).

## Configure

Environment variables only — nothing is hardcoded, no secrets in code:

| Variable         | Meaning                                  | Default                   |
|------------------|------------------------------------------|---------------------------|
| `JUDGE0_BASE_URL`| Judge0 API root                          | `http://localhost:2358`   |
| `JUDGE0_API_KEY` | Optional `X-Auth-Token` (self-host: off) | empty (no auth header)    |

The API key, when set, is sent only as an HTTP header and is never logged.

## Run a local Judge0 (self-hosted, free, no account)

```powershell
docker compose -f docker-compose.judge0.yml up -d
curl.exe http://localhost:2358/languages | Select-String Python
```

This starts Judge0 CE (server + worker + its own Postgres/Redis) on
port `2358`. It is completely separate from the app's own Postgres.

## Run the POC

```powershell
cd backend
$env:JUDGE0_BASE_URL = "http://localhost:2358"
mvn -Dtest='com.codingjudge.judge0.*Test' test
```

Covered: Python/Java/C++ execution, stdin, token + poll loop,
queue/processing detection, Accepted, Compilation Error, Runtime Error,
Time Limit Exceeded, Wrong Answer (via `expected_output`), stdout/stderr/
compile_output/time/memory fields, 422 invalid language, poll timeout,
connection failure, 401/503 mapping (unit-tested without a server).

If Judge0 is not reachable, the live tests SKIP (suite stays green);
`Judge0ClientUnitTest` always runs.

## Example results (reference run, Judge0 CE on Docker Desktop)

Success (Python, stdin `world`):
```json
{"status": {"id": 3, "description": "Accepted"},
 "stdout": "hello, world\n", "time": 0.008, "memory": 7000}
```

Compilation error (Java, missing `;`):
```json
{"status": {"id": 6, "description": "Compilation Error"},
 "compile_output": "Main.java:3: error: ';' expected\n..."}
```

Runtime error (Python `1 // 0`):
```json
{"status": {"id": 11, "description": "Runtime Error (NZEC)"},
 "stderr": "Traceback (most recent call last):\n...ZeroDivisionError..."}
```

Timeout (Python `while True: pass`, `cpu_time_limit: 1`):
```json
{"status": {"id": 5, "description": "Time Limit Exceeded"}}
```

Poll timeout (client-side, 3 s budget on a 60 s sleeper):
```
Judge0Client.Judge0TimeoutException: Submission <token> still not terminal after 3000 ms
```

## Phase 1 report (reference run 2026-10-03, local Docker Desktop)

- Files inspected: SubmissionController, SubmissionService, JudgeEngine,
  DockerSandbox, DockerConfig, Java/Cpp/Python executors, OutputComparator,
  Submission/SubmissionTestResult/TestCase entities, SubmissionStatus/Language
  enums, response DTOs, frontend `api/submissions.ts` (+polling),
  ProblemDetailPage, history/detail pages, docker-compose.yml, Dockerfiles,
  sandbox image, existing backend + frontend tests.
- Files created: `backend/src/test/java/com/codingjudge/judge0/` (client +
  live POC tests + server-free unit tests), `docker-compose.judge0.yml`,
  `docker/judge0/judge0.conf` (local-only throwaway passwords),
  `.env.example` placeholders, this doc.
- Files changed in `src/main` or frontend: NONE. Existing tests untouched.
- API flow tested: `POST /submissions?base64_encoded=false&wait=false`
  -> `201 {"token"}` -> `GET /submissions/{token}` poll until status
  leaves 1/2. `wait=true` never used. Statuses observed live: 1, 2, 3, 4,
  5, 6, 11. Languages resolved live: Python 71, Java 62, C++ 54.
- Baseline backend: 116/116 green. Baseline frontend: 96/96 green.
- POC: `Judge0ClientUnitTest` 8/8, `Judge0PocTest` 12/12 (all live).
- Regression: full backend + frontend suites re-run after POC (see gate).
- Problems hit (all resolved, none hidden):
  1. C: drive full (3.8 MB free) broke Docker/pulls/builds -> freed ~11 GB.
  2. Judge0 CE 1.13.1 isolate needs cgroup v1; modern kernels are v2-only,
     so every execution died with Internal Error (`/box` never created).
     No newer CE exists. Fix: submit with
     `enable_per_process_and_thread_{time, memory}_limit=true`, which makes
     Judge0 skip `--cg` and enforce plain rlimits (deviations documented
     below). A custom isolate rebuild was abandoned (buster headers too old).
  3. Fresh JVMs need GBs of *address space*; per-process `-m` caps exactly
     that, so compiled languages need a roomy `memory_limit` and the local
     server needs `MAX_MEMORY_LIMIT=8000000` (hosted keeps the default).
  4. Server/worker config drift (worker on stale conf) caused SILENT stuck
     jobs: server accepted what the worker's validation rejected, no error
     anywhere. Lesson for Phase 2: worker-side failures must always land a
     submission in a terminal state + log loudly.
- POC-only deviations from stock Judge0 (local env only, NOT production):
  per-process rlimit flags on every submission; `MAX_MEMORY_LIMIT=8000000`;
  `redis:7-alpine` instead of `redis:7.2.4` (local blob corruption);
  throwaway local passwords in `docker/judge0/judge0.conf`.
- Recommendation for Phase 2: execution abstraction
  (`EXECUTION_PROVIDER=docker|judge0`), DB-backed queue reusing the
  `submissions` table (`PENDING` = queued, add `JUDGING` on pickup),
  bounded worker pool, token-poll loop with max timeout, Judge0->project
  status mapping incl. per-test compare via existing `OutputComparator`,
  hidden outputs never leave the backend. Production Judge0 must be hosted
  (Render has no Docker daemon); keep self-host for local dev only.

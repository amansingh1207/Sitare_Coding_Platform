# Judge0 Integration (Phase 2): Architecture & Operations

Branch: `feature/judge0-integration` (NOT merged — merge only after review).

## 1. Architecture

```
Student → POST /api/submissions → validate → save PENDING → 202 {id}
                                    ↓ (background, bounded pool)
                          SubmissionWorker poll (1 s) → atomic claim
                                    → JUDGING → provider executes
                                    → terminal verdict saved
                                    ↓
Frontend polls GET /api/submissions/{id} (unchanged, 1.5 s / 2 min)
```

`CodeExecutionService` (`backend/.../judge/`) is the only execution
abstraction. `JudgeEngine` (batching, verdicts, persistence) sits on top of
it and was not redesigned.

| Provider | Class | Selected by | Use for |
|---|---|---|---|
| Docker (default) | `DockerSandbox` | `EXECUTION_PROVIDER=docker` (or unset) | Local dev, anywhere with a Docker daemon |
| Judge0 | `Judge0ExecutionService` | `EXECUTION_PROVIDER=judge0` | Production hosts without Docker (Render) |

## 2. Local Docker judge

Unchanged from before Phase 2: one container per batch, compile once,
network-none, read-only rootfs, capped memory/CPU/PIDs, always cleaned up.
Needs the Docker daemon + `codingjudge/sandbox:latest` image. Verified by
the pre-existing suites plus a live 20-burst (see §13).

## 3. Judge0 provider

`Judge0ExecutionService` translates one project test case into one Judge0
submission (`POST /submissions` → token → `GET /submissions/{token}` poll).
`wait=true` is never used. Language ids resolve at runtime from
`GET /languages/all` (`Java (OpenJDK…`, `C++ (GCC…`, `Python (3…`) and are
cached; unknown languages degrade to error results, never exceptions.

Status mapping: `3→ACCEPTED-shaped success` (our `OutputComparator` still
decides Wrong Answer — `expected_output` is never sent, so hidden answers
cannot leak into Judge0 logs), `5→TLE`, `6→Compilation Error`,
`7–12→Runtime Error`, `13/14/other→error result`, transport/API/poll
failures → caught, logged loudly, returned as error results (never thrown).

Known mapping limitation: infrastructure failures currently read as
`RUNTIME_ERROR`, not `INTERNAL_ERROR` (shares `JudgeEngine.executionStatus`
semantics with the Docker path on purpose).

## 4. Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `EXECUTION_PROVIDER` | `docker` | `docker` or `judge0` |
| `JUDGE0_BASE_URL` | `http://localhost:2358` | Judge0 API root |
| `JUDGE0_API_KEY` | empty (no auth header) | `X-Auth-Token`, or RapidAPI key (below) |
| `JUDGE0_API_HOST` | empty | RapidAPI host, e.g. `judge0-ce.p.rapidapi.com` |
| `JUDGE0_POLL_MS` | `1000` | Token poll interval |
| `JUDGE0_MAX_WAIT_MS` | `120000` | Per-test-case poll budget |
| `JUDGE0_PER_PROCESS_LIMITS` | `false` | Rlimit mode for cgroup-v1-less hosts (local POC only) |
| `JUDGE_WORKER_ENABLED` | `true` | `false` = judge synchronously in-request (tests use this) |
| `JUDGE_WORKER_POLL_MS` | `1000` | Queue poll interval |
| `JUDGE_WORKER_THREADS_CORE/MAX` | `2` / `4` | Pool bounds |
| `JUDGE_WORKER_QUEUE_CAPACITY` | `100` | Executor queue bound |
| `JUDGE_WORKER_MAX_CLAIM` | `8` | Rows claimed per poll tick |

All are plain env vars (see `.env.example`); no secrets are committed.

## 5. Queue behavior

- The `submissions` table IS the queue (`PENDING` = queued). No Redis/RMQ.
- Claim = one atomic `UPDATE ... WHERE status='PENDING'` per row
  (portable JPQL, no `SKIP LOCKED`): exactly one worker wins.
- The scheduled poller dispatches to a bounded pool (2–4 threads); a full
  pool triggers `RejectedExecutionException`, which the poller catches —
  rows stay `PENDING` for the next tick. Nothing is ever dropped.
- Every claimed row ends terminal: worker code catch-alls to
  `INTERNAL_ERROR`. Only a JVM death can strand `JUDGING`.
- Startup recovery (`ApplicationReadyEvent`) requeues all `JUDGING` rows.
- Graceful shutdown waits up to 30 s for in-flight verdicts.

## 6. Submission lifecycle

`PENDING → JUDGING → {ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR,
RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, INTERNAL_ERROR}`.
`POST /` already returned 202; it now returns `PENDING` instead of the final
verdict (the intended new contract — the frontend polls and never noticed).
`run`/`run-custom` stay synchronous through the active provider.

## 7. Failure handling

| Failure | Behavior |
|---|---|
| Judge0 4xx/5xx, timeouts, resets, malformed JSON | Typed `Judge0*Exception` → error result → terminal verdict + server log |
| Queue full (executor saturated) | Poll defers; rows stay `PENDING` |
| Poll timeout (`judge0.max-wait-ms`) | Error result for that test case |
| Invalid language / no match | Error result mentioning the language |
| Compile / runtime / TLE / MLE | Normal verdict path (unchanged) |
| Judge0 outage | Every test errors loudly server-side; backend stays up; verdicts terminal |
| Backend crash mid-judging | Startup recovery requeues `JUDGING` |

No stack traces, tokens, keys, or infra details ever reach the student API.

## 8. Testing commands

```powershell
# Unit + Docker-stub suites (no Docker, no Judge0 needed):
cd backend; mvn test
# Judge0 POC/live (needs Judge0 up; skips cleanly otherwise):
mvn -Dtest='com.codingjudge.judge0.*Test,com.codingjudge.judge.Judge0LiveEngineTest' test
# Local Judge0 (dev only):
docker compose -f docker-compose.judge0.yml up -d
# Frontend (unchanged in Phase 2):
cd frontend; npx vitest run
# Manual HTTP burst (needs running backend + verified user):
python tests/load/burst_submit.py --base http://localhost:8080 \
  --email burst@test.local --password Burst@123 --problem 3 \
  --count 20 --concurrency 20
```

## 9. Switching providers

- Local: `EXECUTION_PROVIDER=docker` (default). Nothing else needed besides
  the Docker daemon.
- Production: `EXECUTION_PROVIDER=judge0` + `JUDGE0_BASE_URL` (+ key/host).
  No code change, no rebuild beyond deploying the branch. To fall back:
  flip the single variable back to `docker` (or shut the worker off with
  `JUDGE_WORKER_ENABLED=false` for fully synchronous judging).

## 10. Render deployment configuration

Backend service env (in addition to existing DB/JWT/SendGrid vars):

```
EXECUTION_PROVIDER=judge0
JUDGE0_BASE_URL=https://<your-judge0-host>
JUDGE0_API_KEY=<server-side only>
JUDGE0_API_HOST=<only for RapidAPI fronting>
JUDGE_WORKER_ENABLED=true
```

Render never sees Docker; `JUDGE0_*` stay server-side (the frontend bundle
contains no keys — verified: no `JUDGE0` references under `frontend/src`).
Judge0 itself must be hosted (RapidAPI/cloud/VPS): Render cannot run it.

## 11. Security considerations

- Hidden `expected_output` is compared inside our backend only — never sent
  to Judge0, never serialized to student-facing DTOs (unchanged, tested).
- Judge0 credentials live in backend env only; client sends user code + stdin
  over HTTPS; API key travels as a header and is never logged.
- Per-submission resource caps still come from the problem
  (`cpu_time_limit`, `memory_limit` per Judge0 call).
- Local-POC deviations (`per-process-limits`, raised `MAX_MEMORY_LIMIT`,
  alpine redis, throwaway compose passwords) are documented in
  `docs/JUDGE0_POC.md` and must NOT be copied to production.

## 12. Known limitations

1. One Judge0 call per test case (compile repeats): fine at our scale, wasteful
   at large N. No batch endpoint used yet (deliberate: no premature optimization).
2. Infra failures surface as `RUNTIME_ERROR`, not `INTERNAL_ERROR`.
3. Very long queues can outlast the frontend's 2-minute poll timeout; the job
   still completes server-side and History shows it — the editor just stops
   waiting. (Tune client timeout or poll from History if this bites.)
4. `run`/`run-custom` execute synchronously in the HTTP thread (unchanged);
   only `submit` is queued.
5. Memory/CPU were bounded by construction (4 threads, capped containers),
   not profiled under sustained load — see §13 for what WAS measured.

## 13. Load / burst testing (measured, not claimed)

Scale target: ~120–150 registered students, bursty usage. What was TESTED:

- **50-burst, in-JVM** (`SubmissionWorkerTest`, stub judge): drained in
  **648 ms**, 50/50 terminal ACCEPTED, exactly 2 result rows each (no dupes),
  queue counters back to zero.
- **20-burst, real HTTP + real Docker judge** (`tests/load/burst_submit.py`,
  local backend, problem `sum-two`, correct Python solution, 20 concurrent):
  **20/20 HTTP 202** (max submit latency **0.38 s** — request thread never
  blocked), verdicts **20/20 ACCEPTED**, wait-to-terminal min **3.2 s** /
  median **13.1 s** / max **23.3 s**, wall **23.7 s**, duplicate ids **0**.
- **Race test**: 4 threads × `poll()` on one submission → exactly one set of
  results (atomic claim holds).

What is EXPECTED (not load-tested): 150 truly-simultaneous submits against
hosted Judge0. Extrapolating the local numbers, the queue absorbs the burst
(HTTP stays fast) and verdicts drain at provider speed; hosted Judge0
throughput/rate limits will dominate. Do a staged 5→10→20→50 run against the
production Judge0 host before exam-week reliance, and watch worker logs +
`countByStatus(PENDING)` depth.

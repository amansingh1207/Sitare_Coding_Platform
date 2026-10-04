# DOMjudge Integration Design (Phase 9 proposal — NOT implemented)

Status: design only. Branch `feature/domjudge-integration`, unmerged.
Local + AWS POCs green (see `docs/DOMJUDGE_POC.md`, `docs/DOMJUDGE_AWS.md`).
Nothing below changes existing behavior until explicitly approved.

## 1. Goal (recap)

Production judging without Docker on the backend host, reusing everything
built for Judge0: `CodeExecutionService` abstraction, DB-backed bounded
queue, polling frontend, verdict banner. DOMjudge becomes a third provider
next to `docker` and `judge0`, selected by `EXECUTION_PROVIDER=domjudge`.

## 2. Exact classes (inspected current code first — no invented hierarchy)

| New class | Package | Role |
|---|---|---|
| `DomjudgeExecutionService` | `com.codingjudge.judge` | `implements CodeExecutionService`, active on `execution.provider=domjudge` |
| `DomjudgeClient` | `com.codingjudge.judge` | Thin CCS REST client (JDK HTTP + Jackson, like `Judge0Client`; no new deps) |
| `DomjudgeProblemMirror` | `com.codingjudge.service` | Resolve-or-import CodingJudge problem → DOMjudge problem, cached |

Untouched: `JudgeEngine`, `DockerSandbox`, `Judge0ExecutionService`,
`SubmissionService`, `SubmissionWorker`, controllers, DTOs, entities,
frontend, schema. `DockerSandbox` keeps `@ConditionalOnProperty(docker,
matchIfMissing)`; Judge0 keeps its own; only one provider bean is ever
active (proven pattern, wiring-tested).

## 3. Core design decision: submit ONCE, map runs back

DOMjudge judges a whole submission against a mirrored problem — never one
call per testcase (that would also spam its DB and break its verdict).
Per `executeBatch(source, inputs, executor, timeoutMs, memoryLimitMb)` call:

1. `mirror.ensure(problem)` → DOMjudge problem id (externalid
   `codingjudge-<problemId>`; lookup-first so restarts never duplicate;
   package built in-memory: `problem.yaml` + `domjudge-problem.ini`
   (timelimit/memory from OUR problem) + `data/sample|secret` from OUR
   testcases in sortOrder; secret stays server-side, mirroring our model).
2. `POST /api/v4/contests/{cid}/submissions` once
   (`language_id` mapped from OUR `Language`, `entry_point` where required,
   source as base64 ZIP) with the dedicated **team worker account**.
3. Poll `GET .../judgements?submission_id=` (existing poll/timeout config
   shape: `domjudge.poll-ms`, `domjudge.max-wait-ms`) to a terminal
   judgement; fetch `GET .../runs?judgement_id=` ordered by `ordinal`.
4. Translate each run → `ExecutionResult` (same list order as `inputs`):
   - run `correct`/`wrong-answer` → `success(stdout, timeMs, memKb)` and let
     **OUR `OutputComparator`** decide AC/WA (one verdict semantics across
     all providers; hidden answers never leave our backend in student
     responses — DOMjudge must hold them to compare, same trust as our DB).
   - run `compiler-error` (whole submission) → `compilationError` for every
     input (engine short-circuits on the first, as today).
   - run `timelimit` → `timeout()`; run `run-error`/other → `error(stderr,
     exit)`; infra failure at any step → caught, logged, `error()` per
     input (terminal verdict, backend never crashes — same containment
     contract as the Judge0 provider; reads as RUNTIME_ERROR, documented).
5. `JudgeEngine` loop, persistence, history, banner: byte-for-byte unchanged.

## 4. Open risk, closed by test (not by assumption)

Run↔input alignment is by `ordinal`. The mirror builder names files to
force rank order, but DOMjudge does not contractually guarantee it. Guard:
an integration test with 3 distinct-output cases (one deliberately WA)
asserting per-case assignment end-to-end. If DOMjudge ever reorders, that
test — not students — finds out first. Fallback if flaky: align by
matching run stdout against our expected outputs via our own comparator.

## 5. Configuration (all env, server-side only)

| Variable | Default | Meaning |
|---|---|---|
| `EXECUTION_PROVIDER` | `docker` | + `domjudge` as third value |
| `DOMJUDGE_BASE_URL` | — (required) | e.g. `https://judge.example.com` |
| `DOMJUDGE_CONTEST` | `demo`-like cid | shortname, single practice contest |
| `DOMJUDGE_USER/PASSWORD` | — | dedicated TEAM worker account |
| `DOMJUDGE_ADMIN_USER/PASSWORD` | — | problem import only |
| `DOMJUDGE_POLL_MS` / `MAX_WAIT_MS` | `1000` / `120000` | same shape as Judge0 props |
| `JUDGE_WORKER_*` | unchanged | queue reused as-is |

No frontend env, no schema migration, no new infra (no Redis/RMQ).

## 6. Queue, flood, failure, security (reuse, not rebuild)

- Bursts: existing bounded pool (2–4) + DB queue; DOMjudge ALSO queues
  internally (proven linear drain) — two-stage backpressure, neither unbounded.
- Per-student flood: no new limiter; queue depth is the throttle (documented;
  add one only if measured abuse appears).
- Stuck/duplicate/restart: existing atomic claim + startup recovery cover
  provider-independent paths; DOMjudge-side orphans are harmless (its own
  queue drains; our row always reaches terminal via catch-all).
- Secrets: team+admin creds in backend env only, never logged/committed;
  DOMjudge DB/judgehost never public (judgehost needs zero inbound);
  HTTPS to DOMserver API in production.
- OOM semantic: DOMjudge has NO memory-limit verdict (DB-verified) —
  memory kills arrive as run-error → our `RUNTIME_ERROR` (engine keeps its
  own MLE heuristics for exit-137-style signals where present).

## 7. Test plan (mirrors the Judge0 gate)

Unit (no server): client mapping incl. 401/422/timeout/malformed, verdict
table incl. OOM→RE, package builder bytes, ordinal-alignment unit pieces.
Wiring: `provider=domjudge` activates service, deactivates Docker (minimal
Spring context, existing pattern). Live (gated, needs DOMjudge up):
AC/WA/CE/RTE/TLE × Java/C++/Python, multi-case incl. empty+large input,
3-case alignment guard, 5→10→20 staged burst vs local DOMjudge, then same
against the AWS box. Regression: full backend 167 + frontend 96 must stay
green. Load claims only from measured runs (tested-vs-expected rule).

## 8. Rollout / rollback

1. Provision DOMjudge (AWS pattern proven: 1 box to start, docs/DOMJUDGE_AWS.md).
2. Create worker-team + admin importer creds; set backend env; deploy backend
   with `EXECUTION_PROVIDER=domjudge` (staging first: 5-submission smoke).
3. Rollback = flip one variable back to `docker` (or `judge0`); zero code
   changes, zero data migration. Branch stays unmerged until green.

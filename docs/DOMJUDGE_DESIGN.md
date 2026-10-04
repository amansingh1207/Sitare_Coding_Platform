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
| `DomjudgeProblemMirror` | `com.codingjudge.judge` | Resolve-or-import CodingJudge problem → DOMjudge problem, cached |

Untouched: `DockerSandbox`, `Judge0ExecutionService`,
`SubmissionService`, `SubmissionWorker`, controllers, DTOs, entities,
frontend, schema. Touched minimally: `JudgeEngine` (3-line provider-verdict
branch), `ExecutionResult` (+nullable provider verdict, all factories
default null), `CodeExecutionService` (+2 default methods, existing impls
inherit). `DockerSandbox` keeps `@ConditionalOnProperty(docker,
matchIfMissing)`; Judge0/DOMjudge keep theirs; only one provider bean is
ever active (proven pattern, wiring-tested).

## 3. Core design decision: submit ONCE, map runs back (verified live)

DOMjudge judges a whole submission against a mirrored problem — never one
call per testcase (verified live: per-test stdout is `Serializer\Exclude`d,
so it is UNAVAILABLE through the API; compiler diagnostics likewise).
Verdict authority therefore belongs to DOMjudge on this path (our
comparator is bypassed via provider verdicts in `JudgeEngine`).
Per `judgeTestCases(problem, requested, source, executor)` call:

1. `mirror.ensure(problem)` → DOMjudge problem id (externalid
   `cj-<problemId>-<hash8>`; hash covers sorted test data + limits so edits
   create a new mirror instead of judging stale data; lookup-first so
   restarts never duplicate; package built in-memory: `problem.yaml` +
   `domjudge-problem.ini` (timelimit/memory from OUR problem) +
   `data/sample|secret` from OUR testcases; secret stays server-side).
   NOTE: the API `id` may differ from the short-name (observed: dashes in
   short-name yield id `"problem"`); the mirror uses the returned id, never
   assumes it. NOTE 2: DOMjudge derives `problem.externalid` from the
   uploaded ZIP *filename*, so the import MUST send `<short-name>.zip` —
   a fixed `problem.zip` collides on the second import with
   `problem.externalid: This value is already used` (hit live, fixed).
2. `POST /api/v4/contests/{cid}/submissions` once
   (`language_id` resolved live, `entry_point` where required — `Main` for
   Java, main filename for Python — source as base64 ZIP) with a dedicated
   service account holding **team+admin** roles (team submits; jury/admin
   reads runs — the runs endpoint needs jury/judgehost/api-reader).
3. Poll `GET .../judgements?submission_id=` to a terminal judgement; fetch
   `GET .../runs?judging_id=` ordered by `ordinal`. NOTE: the query
   parameter is `judging_id` — the API docs annotation says
   `judgement_id`, which is silently ignored (returns ALL runs). Verified
   in `RunController` source and live. NOTE 2: the judgement verdict can
   go terminal while slow runs are still flushing (observed live: TLE
   verdict with only 1/35 runs visible), so runs are awaited up to 120s
   until the count matches AND every run has a verdict; only a persistent
   mismatch fails loud.
4. Translate each run → `ExecutionResult` (aligned to requested inputs):
   - run `correct` → provider-verdict ACCEPTED; run `wrong-answer` →
     provider-verdict WRONG_ANSWER (actualOutput stays empty: unavailable).
   - whole-submission `compiler-error` (no runs) → `compilationError` for
     every input (engine short-circuits on the first, as today; message
     stays generic — diagnostics are jury-UI-only).
   - run `timelimit` → `timeout()`; run `run-error`/other → `error()`;
     infra failure at any step → caught, logged, `error()` per input
     (terminal verdict, backend never crashes — same containment contract
     as the Judge0 provider; reads as RUNTIME_ERROR, documented).
   - run-count mismatch vs mirrored cases → fail loud (all-error), never
     misassign.
5. `JudgeEngine` loop, persistence, history, banner: unchanged except a
   3-line provider-verdict branch. Run-custom (arbitrary stdin) has no
   DOMjudge equivalent → clear error result (documented limitation).

## 4. Open risks, closed by test (not by assumption)

(a) Run↔input alignment is by `ordinal`. Guard: integration test with 3
distinct-output cases (one deliberately WA) asserting exact per-testcase
id+status mapping. If DOMjudge ever reorders, that test — not students —
finds out first.
(b) **Short-circuit judging (RESOLVED):** DOMjudge has a documented
`lazy_eval_results` config (default `1` = Lazy: stop at the first
highest-priority failure). Failing submissions then expose fewer runs
than mirrored cases. Since our engine needs per-test rows, DOMjudge
hosts used by CodingJudge MUST set `lazy_eval_results = 2` (Full
judging: all testcases always run) — verified live (TLE + alignment
guard both green after the change). The count-mismatch tripwire stays
as defense-in-depth.
(c) **Contest expiry hides judgements (HIT LIVE):** the practice contest
must stay temporally open — after `contest.endtime` passes, the judgehost
still judges but the API stops returning judgements (workers then spin to
`max-wait` timeout). Keep a far-future `endtime` on every DOMjudge host
(local extended +7d, AWS +30d on 2026-10-04) and re-check before any
student-facing window. The service account needs team+jury-level roles:
team alone can submit but `runs` answers 403 (hit live).

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

# DOMjudge POC Report — Phases 1–6 (local, dev-only)

Branch: `feature/domjudge-integration` (NOT merged). No app code changed
except `docker-compose.domjudge.yml` + this doc + `tests/load/domjudge_api_test.py`.
Local stack: `domserver:9.0.0` + `judgehost:9.0.0` + `mariadb:10.11`
(Docker Desktop, project `codingjudge-*` containers). Client: throwaway
scripts only; the CodingJudge backend was never pointed at DOMjudge.

## Phase 1 — CodingJudge architecture (verified, read-only)

```
Student → React → POST /api/submissions → SubmissionService
→ save PENDING → JudgeEngine → DockerSandbox (docker-java, 1 container/batch)
→ Java/Cpp/Python executors → OutputComparator (token-based)
→ Submission + SubmissionTestResults (hidden = sample-only) → verdict
→ frontend polls (1.5s, 2 min budget) → VerdictBanner
```

Statuses: PENDING JUDGING ACCEPTED WRONG_ANSWER COMPILATION_ERROR
RUNTIME_ERROR TIME_LIMIT_EXCEEDED MEMORY_LIMIT_EXCEEDED INTERNAL_ERROR.
Endpoints: `POST /` (202), `POST /run`, `POST /run-custom`,
`GET /solved-ids|/{id}|/`, auth/problems/admin-import. Test suites:
backend 167, frontend 96. Compose: postgres + backend (docker.sock mount)
+ nginx frontend. Judge0 Phase-2 branch adds (unmerged):
`CodeExecutionService` abstraction + DB-backed bounded queue + Judge0
provider + 50-burst/race proofs.

**DOMjudge fit-point:** a third `CodeExecutionService` implementation plus
a judgement→verdict mapper. Design note: DOMjudge judges a WHOLE
submission (test data mirrored into it); our engine sends code+stdin per
test. Integration must mirror each CodingJudge problem into a DOMjudge
problem (import path proven below) and map per-run results back.

## Phase 2 — Official requirements (DOMjudge 9.0.1 stable, images 9.0.0)

Source of truth: `github.com/DOMjudge/domjudge` manual + `hub.docker.com/r/domjudge/{domserver,judgehost}`.

- DOMserver: Linux + nginx/Apache + PHP ≥ 8.2 (+curl, ds, gd, intl, json,
  mbstring, mysqli, xml, zip) + MySQL/MariaDB + NTP. Judgehosts poll it
  over HTTP(S); it never calls out.
- Judgehost: Linux + root, sudo/debootstrap/PHP-CLI/gcc-g++, run users +
  sudoers, prebuilt chroot (`dj_make_chroot`), **cgroup v2 REQUIRED**
  (kernel ≥ 5.19/6.0; v1 unsupported), judgedaemon (several per host OK),
  judgehost-role REST credentials. Same-box server+host possible
  (testing OK, production discouraged).
- Capacity guidance in docs: ~1 judgehost / 20 teams (live-contest metric).
  **Docs give NO hard CPU/RAM minimum** — stated explicitly per requirements.
- Languages C++/Java/Python: standard via chroot toolchains (verified live).
- Test cases: problem ZIP packages (`problem.yaml` + `domjudge-problem.ini`
  + `data/sample|secret/*.in/*.ans`); API import: multipart field **`zip`**
  to `POST /contests/{cid}/problems` (found in source; `data` is for YAML).
- Ports: 80/443 inbound to domserver only. Judgehost needs only OUTBOUND
  HTTP(S) (+DNS/NTP). MySQL/Redis-equivalents stay private (there is no Redis).
- Verdict vocabulary (DB-verified): `correct, compiler-error, run-error,
  timelimit, wrong-answer` (+API codes AC/CE/RTE/TLE/WA). **No separate
  memory-limit verdict**: OOM surfaces as run-error.

## Phase 3 — Local execution matrix (all via CCS REST API)

Demo contest `demo` (5 h window), team `demo`, problem `hello`
(1 sample case: input `1\n` → `Hello world!\n`; learned from DB — the API
never exposes it, good for our hidden-test requirement).

| # | Case | Verdict | Note |
|---|---|---|---|
| 2 | C++ correct | **AC** | ~3 s incl. compile |
| 3 | Java correct (`entry_point=Main`) | **AC** | |
| 4 | Python correct (`entry_point` = file) | **AC** | |
| 5 | C++ wrong output | **WA** | |
| 6 | C++ syntax error | **CE** | diagnostics visible to jury/admin |
| 7 | C++ div-by-zero | **RTE** | |
| 8 | C++ infinite loop (limit 5 s) | **TLE** | killed at limit |
| 10 | Custom `sumtwo` (sample+2 secret) python | **AC** | multi-case |
| 11 | `sumbig` (4 cases incl. empty + 519 KB input) | **AC** | empty stdin delivered; big input fine |

MLE: not applicable (no such verdict; OOM → run-error by design).
Wrong-format submit → `400 No files specified` (needs `files[].data` =
base64 ZIP). Filenames must match `^[A-Za-z0-9+._-]+$` (space rejected —
learned the hard way).

Custom problem import (`sumtwo`, `sumbig`) works via API incl. an EMPTY
`.in` file. Re-import does NOT append (shortname collision) — one package
per problem version.

## Phase 4 — API flow (proven, scripted in `tests/load/domjudge_api_test.py`)

`POST /api/v4/contests/{cid}/submissions` (team basic-auth,
`{language_id, problem_id, entry_point?, files:[{data: base64zip}]}`)
→ `201 {id}` → poll `GET .../judgements?submission_id=` until
`judgement_type_id` set → optional `GET .../runs?judgement_id=`.
Team role sees verdicts; test data never leaves the server side.

## Phase 5/6 — Concurrency (single judgehost, 1 daemon)

| Concurrent | Result | Wall | Notes |
|---|---|---|---|
| 1 | 1/1 AC | 3.2 s | baseline |
| 5 | 5/5 AC | 15.5 s | |
| 10 | 10/10 AC | 30.9 s | |
| 20 | 20/20 AC | 61.7 s | 0 failed, 0 duplicates |

Linear drain ≈ 3 s/submission: the queue works, nothing crashes, verdicts
stay correct. **Limiter = single judgedaemon throughput, not CPU/RAM**
(idle: server ~258 MB, mariadb ~190 MB, judgehost ~20 MB; load adds one
compile at a time). 30 was skipped: the trend is conclusive and the laptop
had already shown memory fragility — documented, not hidden.

**Empirical capacity:** one judgehost ≈ 20 judged submissions/minute for
C++-sized work. For PRACTICE (students rarely simultaneous), one judgehost
comfortably serves 120–150 registered students with queueing; this is an
estimate from measured throughput, NOT a guarantee, and NOT "150
simultaneous". Scale later with more daemons/hosts (both supported).

## Phase 7 — AWS Free Tier feasibility (analysis only, nothing launched)

No AWS access was available, so this is evaluated from public Free Tier
terms + the measurements above — no instance was started, nothing spent.

- Free Tier compute for new accounts: `t3.micro` (2 vCPU, 1 GiB RAM),
  750 h/mo for 12 months; 30 GB EBS; modest transfer allowance.
- Measured idle for domserver+mariadb+judgehost: ~0.5 GB, plus OS
  (~0.2 GB), plus compile spikes (g++ hundreds of MB; javac commonly
  0.5–1 GB RSS). Peak on a shared box plausibly exceeds 1 GiB.
- **Verdict: `t3.micro` is NOT recommended for all-in-one**
  (domserver + judgehost + DB on 1 GiB). It would likely boot, then risk
  OOM kills exactly when Java compiles during a burst. This is a
  reasoned assessment from measurements, not a test on the instance.
- Cheapest SAFE options: (a) single `t3.small` 2 GiB (~$15/mo, NOT free
  tier) for all-in-one; (b) two free `t3.micro`s — domserver+DB on one,
  judgehost on the other (judge side still tight for Java, needs a live
  trial); (c) keep CodingJudge backend on Render + Neon (unchanged!) and
  put ONLY domserver+judgehost+MariaDB on EC2, talking to it over HTTPS.
- If EC2 is used: Security Groups inbound = 443 (/80) to the world (or
  tighter), SSH (22) to admin IP only, MySQL closed; judgehost needs NO
  inbound. Outbound HTTPS/DNS/NTP only. No RDS needed (local MariaDB in
  container/volume or EC2-local install is enough at this scale).
- Cost if Free Tier exceeded: one small instance + EBS + transfer ≈
  $15–25/mo range — verify in the AWS console before committing.

## Integration sketch (Phase 9+, not implemented)

New `DomjudgeExecutionService implements CodeExecutionService`
(`EXECUTION_PROVIDER=domjudge`): mirror problem→DOMjudge problem once (cache
mapping), submit source per CodingJudge submission… — replaced by simpler
correct design: submit ONCE per CodingJudge submission to the mirrored
DOMjudge problem, map runs→testcases for history, verdict→status. Queue,
polling, failure handling and UI reuse the Judge0 Phase-2 work unchanged
(statuses PENDING/JUDGING already exist; INTERNAL_ERROR covers
DOMjudge-down). No schema change foreseen; no frontend change foreseen.

## Exact commands used

```powershell
docker compose -f docker-compose.domjudge.yml pull
docker compose -f docker-compose.domjudge.yml up -d domjudge-mariadb
docker compose -f docker-compose.domjudge.yml up -d domjudge-server
docker exec codingjudge-domserver cat /opt/domjudge/domserver/etc/initial_admin_password.secret
# judgehost password printed in server logs on first boot
$env:DOMJUDGE_JUDGEHOST_PASSWORD='<from logs>'
docker compose -f docker-compose.domjudge.yml up -d domjudge-judgehost
python tests/load/domjudge_api_test.py matrix
python tests/load/domjudge_api_test.py load 20
```

Throwaway local creds (admin/judgehost/demo passwords) exist ONLY in
container volumes and chat history — never in the repo. DB/API verification
queries used throwaway SELECTs.

## Rollback

Nothing merged (`feature/domjudge-integration` branch only).
`docker compose -f docker-compose.domjudge.yml down -v` removes the entire
POC including its volumes. `git branch -D feature/domjudge-integration`
abandons it. CodingJudge `master` was never touched.

# DOMjudge AWS POC Report — t3.micro (ap-southeast-2)

Date: 2026-10-04. Instance: `i-0b18619f7ccf52ae7` (`t3.micro`, 2 vCPU,
1 GiB RAM, 20 GiB gp3, Ubuntu 24.04.5, kernel 7.0.0-aws, Docker 29.8.2).
All work in `~/domjudge-poc` on the instance; compose:
`docker-compose.domjudge-aws.yml` (this repo, root). Nothing in the
CodingJudge app was modified. No AWS resources created or destroyed
(instance, SG and key pre-existed).

## Environment (verified, not assumed)

- `stat -fc %T /sys/fs/cgroup` → `cgroup2fs` (cgroup v2 ✅, DOMjudge 9
  requires it). No grub changes needed on this kernel.
- Images (pre-downloaded): `mariadb:11` ~467 MB,
  `domjudge/domserver:9.0.0` ~943 MB, `domjudge/judgehost:9.0.0` ~3.33 GB.
- Before start: ~909 MiB total, ~535 MiB available, 2 GiB swap (used ~0),
  ~9.3 GiB disk free.

## Tunings applied (smallest safe changes, all documented here)

1. `FPM_MAX_CHILDREN=5` (default 40: forty PHP children do not fit 1 GiB).
2. `FPM_MEMORY_LIMIT=256M` (default 2G: caps per-child worst case).
3. MariaDB `--innodb-buffer-pool-size=128M` (+ `--max-connections=100` kept
   from the official example).
4. Judgehost `cgroup: host` + `/sys/fs/cgroup` mount (same as local POC;
   without it the daemon refuses to start).
5. Domserver bound to `127.0.0.1:12345` only (SG has no HTTP anyway).

## Startup (MariaDB → domserver → judgehost)

- MariaDB healthy; domserver ran DB migrations, seeded demo contest
  (`demo`, 5 h window, `allow_submit=true`) and example problems.
- Judgehost registered via API and polled (`No submissions in queue`).
- Throwaway POC credentials (server-generated) live only in container
  volumes and operator chat history — never in this repo.

## API + language matrix (live, `tests/load/domjudge_api_test.py matrix`)

`POST /api/v4/contests/demo/submissions` (team basic-auth,
`{language_id, problem_id, entry_point?, files:[{base64 ZIP}]}`) → `201`
→ poll `GET .../judgements?submission_id=` → `judgement_type_id`.
Filenames must match `^[A-Za-z0-9+._-]+$`; problem ZIP import uses
multipart field **`zip`** to `POST .../contests/demo/problems`.

| Case | Verdict |
|---|---|
| C++ / Java (`entry_point=Main`) / Python (`entry_point`=file) correct | AC, AC, AC |
| Wrong output | WA |
| C++ syntax error | CE (diagnostics jury-visible) |
| Division by zero | RTE |
| Infinite loop (5 s limit) | TLE (~9 s incl. kill) |

Custom problems `sumtwo`/`sumbig` imported via API (incl. an EMPTY `.in`
file — accepted and judged). Re-import does NOT append (shortname
collision); one package per problem version.

MLE: not applicable — DOMjudge 9 has no memory-limit verdict
(DB-verified vocabulary: correct/compiler-error/run-error/timelimit/
wrong-answer; team manual lists the same). OOM would surface as
run-error: map it to `RUNTIME_ERROR` in the future mapper.

## Concurrency (single judgehost, 1 daemon; C++ unless noted)

| Concurrent | Result | Wall | Notes |
|---|---|---|---|
| 1 | 1/1 AC | 3.2 s | baseline |
| 5 | 5/5 AC | 19.0 s | |
| 10 | 10/10 AC | 31.8 s | |
| 5 × Java | 5/5 AC | 12.7 s | no OOM, javac fine |
| 20 | 20/20 AC | 60.4 s | 0 failed, 0 duplicates |

30 skipped deliberately: scaling is perfectly linear (~3 s/submission),
and pushing a 1 GiB box further buys no new information while risking
stability. Throughput ≈ 20 judged submissions/minute per daemon.

Observed resources: idle domserver ~190–260 MB / mariadb ~27–190 MB /
judgehost daemon ~10–26 MB; no container restarts across all runs;
disk stayed ~9 GB free; **swap engaged (~344 MiB of 2 GiB) during the
20-run** — the box survives via swap, verdicts stay correct, but
headroom is thin.

## Verdict: t3.micro viable ONLY with the configuration above

- ✅ Survives the full matrix + 20-burst with zero failures/crashes.
- ⚠️ Swap gets used under burst; available RAM dipped toward ~300 MiB.
- ❌ Do NOT run stock config (40 PHP children) or add load beyond ~20
  concurrent without re-measuring.
- Cheapest scaling (still $0): a second judgedaemon on the SAME box
  (`DAEMON_ID=1` + run user) roughly doubles throughput — untested, listed
  as the next experiment, not a claim.
- If bursts routinely exceed ~20 concurrent or Java dominates: move to
  `t3.small` 2 GiB (paid) or split domserver+DB / judgehost across two
  free `t3.micro`s (judge side still needs its own live trial).

## Production direction (unchanged plan)

CodingJudge backend stays on Render + Neon. This EC2 pattern
(domserver+judgehost+MariaDB, HTTPS to the backend) is the execution
sidecar; judgehost needs NO inbound ports (polls out), MySQL stays
private, only 443 to the world (+22 to admin IP) when the API goes public.

## Resume / teardown

```bash
cd ~/domjudge-poc
docker compose -f docker-compose.domjudge-aws.yml up -d   # resume
docker compose -f docker-compose.domjudge-aws.yml down    # stop (volumes kept)
docker compose -f docker-compose.domjudge-aws.yml down -v # full wipe incl. DB
```
Current state after testing: all three containers STOPPED, volumes kept.

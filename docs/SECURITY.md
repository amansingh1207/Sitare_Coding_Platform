# SECURITY.md — CodingJudge Security Requirements

**Version:** 1.0
**Status:** Draft
**Last Updated:** 2026-10-02

---

## 1. Security Philosophy

All student-submitted code is treated as **hostile/untrusted input**. The security of the judge sandbox is the highest priority. A sandbox escape could compromise the entire platform.

---

## 2. Threat Model

| Threat | Impact | Likelihood |
|--------|--------|------------|
| Student code escapes sandbox | Critical — Full host compromise | Low |
| Student reads hidden tests | High — Unfair advantage | Medium |
| Student reads other users' code | High — Privacy violation | Medium |
| Student exhausts resources | Medium — Service degradation | High |
| Student accesses Docker socket | Critical — Full host compromise | Low |
| Student brute-forces passwords | High — Account takeover | Medium |
| Student floods API | Medium — Service degradation | Medium |

---

## 3. Sandbox Security

### 3.1 Container Isolation

Student code runs inside Docker containers with the following restrictions:

| Restriction | Implementation | Purpose |
|-------------|---------------|---------|
| No network | `--network none` | Prevent data exfiltration, downloading malware |
| Limited memory | `--memory 256m` | Prevent OOM attacks |
| Limited CPU | `--cpus 1.0` | Prevent CPU exhaustion |
| Limited processes | `--pids-limit 64` | Prevent fork bombs |
| Read-only filesystem | `--read-only` | Prevent system modification |
| No new privileges | `--security-opt no-new-privileges` | Prevent privilege escalation |
| Drop all capabilities | `--cap-drop ALL` | Remove Linux capabilities |
| No Docker socket | Do not mount `/var/run/docker.sock` | Prevent Docker control |

### 3.2 What Student Code CANNOT Do

- Access the internet or internal network
- Modify files outside `/tmp`
- Spawn more than 64 processes
- Use more than 256 MB of memory
- Use more than 1 CPU core
- Access the Docker socket
- Read environment variables from the host
- Access the database
- Access other containers

### 3.3 Verified Sandbox Boundaries

These were verified empirically against `codingjudge/sandbox:latest`
(image built from `docker/sandbox/Dockerfile`), not merely asserted:

| Boundary | Verification | Result |
|----------|--------------|--------|
| No network | `getent hosts google.com` inside container | Blocked, no DNS resolution |
| Read-only root filesystem | `touch /escape.txt` | `Read-only file system` |
| Writable scratch space | `touch /tmp/ok` | Succeeds |
| No application secrets | `env` inside container | Only base-image defaults, no app config |
| Process limit | 500 concurrent `fork()` calls | Blocked at 63 processes (`pids-limit 64`) |
| Memory limit | Allocate and touch 400 MB | Killed (exit 137) when swap disabled |
| Toolchain presence | `java -version`, `g++ --version`, `python3 --version` | Java 17, g++ 11.4, Python 3.10 |
| Unprivileged execution | `id -u` | 1000 (non-root) |

**Swap must be disabled.** Docker defaults `--memory-swap` to twice the memory
limit. With the default, a 400 MB allocation survives a 256 MB limit because
pages spill to swap. The judge therefore sets memory and memory-swap to the same
value. Verified: default swap survives, disabled swap yields exit 137.

**Exit code 137 means MEMORY_LIMIT_EXCEEDED**, not a runtime error. The judge
maps 128 + SIGKILL(9) to `MEMORY_LIMIT_EXCEEDED`.

### 3.4 Known Limits of This Boundary

- The sandbox image ships a full JDK, g++ and Python; a determined student may
  spend CPU on compilation, which the compile timeout bounds but does not cap.
- `--memory-swap` equality disables swap for the container. On a host under
  memory pressure this makes submissions OOM-kill sooner, which is intentional.
- Isolation is Docker-on-Linux semantics. Docker Desktop (Windows/macOS) runs the
  daemon inside a Linux VM, so these boundaries hold at the VM layer rather than
  the bare-metal kernel.

### 3.3 What Student Code CAN Do

- Read its own source code
- Write to `/tmp` (limited to 64 MB)
- Execute within the time limit
- Use standard library functions

---

## 4. API Security

### 4.1 Authentication

- All endpoints except `/api/auth/**` require JWT authentication
- JWT tokens expire after 24 hours (configurable)
- Tokens are signed with a secure secret (min 256 bits)
- Passwords are hashed with BCrypt (cost factor 12)

### 4.2 Authorization

- Users can only access their own submissions
- Users cannot access other users' data
- Problem data is accessible to all authenticated users
- Hidden test cases are never sent to the client

### 4.3 Input Validation

| Input | Validation |
|-------|------------|
| Email | Valid email format, max 255 chars |
| Username | 3-50 chars, alphanumeric + underscore |
| Password | Min 8 chars, must contain letter and number |
| Source code | Max 256 KB |
| Problem ID | Must exist in database |
| Language | Must be JAVA, CPP, or PYTHON |

### 4.4 Rate Limiting

Future enhancement: Implement rate limiting to prevent abuse.

Recommended limits:
- Login: 5 attempts per minute per IP
- Submissions: 10 per minute per user
- Run Code: 20 per minute per user

---

## 5. Data Security

### 5.1 Secrets Management

- All secrets come from environment variables
- `.env` files are never committed to version control
- Database passwords are stored in environment variables
- JWT secret is stored in environment variables

### 5.2 Data Protection

| Data | Protection |
|------|------------|
| Passwords | BCrypt hashed (never stored in plaintext) |
| JWT tokens | Signed with secure secret |
| Hidden test cases | Never sent to client |
| Source code | Only accessible to owning user |
| Database | Accessible only from backend |

### 5.3 Data Exposure Rules

| Data | Exposed to Student? | Notes |
|------|---------------------|-------|
| Problem statement | Yes | Public to all authenticated users |
| Sample test cases | Yes | Marked as `is_sample = true` |
| Hidden test cases | **NEVER** | Only used server-side |
| Own source code | Yes | Only the owning user |
| Others' source code | **NEVER** | Strictly isolated |
| Own submission results | Yes | Only the owning user |
| Others' submission results | **NEVER** | Strictly isolated |

---

## 6. Infrastructure Security

### 6.1 Docker Host

- Docker daemon runs on a dedicated host or VM
- Docker socket is only accessible to the backend container
- Regular security updates are applied
- Docker is configured with user namespacing (if available)

### 6.2 Network

- Backend is not directly exposed to the internet (behind reverse proxy)
- Database is only accessible from the backend container
- Frontend communicates with backend via HTTPS

### 6.3 Monitoring

- Log all sandbox creation and destruction
- Monitor for unusual resource usage
- Alert on repeated `INTERNAL_ERROR` results

---

## 7. Security Checklist

### 7.1 Must Always Do

- [x] Execute student code only inside Docker containers
- [x] Restrict memory, CPU, and processes
- [x] Enforce execution timeout
- [x] Disable network access
- [x] Use read-only filesystem
- [x] Drop all capabilities
- [x] Disable privilege escalation
- [x] Never mount Docker socket
- [x] Clean up containers after execution
- [x] Validate all API input
- [x] Authenticate protected endpoints
- [x] Authorize user-specific resource access
- [x] Hash passwords with BCrypt
- [x] Use environment variables for secrets
- [x] Never expose hidden test cases

### 7.2 Must Never Do

- [ ] Execute student code directly on the host
- [ ] Expose hidden test cases to the client
- [ ] Store plaintext passwords
- [ ] Hardcode secrets in source code
- [ ] Commit `.env` files
- [ ] Mount Docker socket into student containers
- [ ] Allow network access from student containers
- [ ] Skip input validation
- [ ] Allow unauthorized access to submissions

---

## 8. Incident Response

### 8.1 Sandbox Escape

1. Immediately shut down the affected container
2. Isolate the host from the network
3. Preserve logs for forensic analysis
4. Patch the vulnerability
5. Review all recent submissions for suspicious activity

### 8.2 Data Breach

1. Immediately revoke all JWT tokens
2. Force password reset for all users
3. Investigate the breach vector
4. Notify affected users
5. Patch the vulnerability

---

## 9. Security Testing

Automated: `ApiSecurityTest` (13 tests) covers authentication, authorization,
hidden-test isolation, payload limits and password handling.

Verified manually against a live judge (2026-10-02):

### 9.1 Sandbox Escape Attempts

Adversarial submissions were run through the real judge. All were contained:

| Attempt | Result |
|---------|--------|
| Read `/etc/passwd` | Container's own file only |
| Connect to Docker socket | Blocked |
| TCP egress to 1.1.1.1:53 | Blocked |
| DNS lookup of external host | Blocked |
| Write to `/etc` | Blocked (read-only rootfs) |
| Read `JWT`/`PASS`/`SECRET` env vars | Empty list |
| Read `/proc/1/environ` | Container env only, no app secrets |
| Fork bomb (500 forks) | Blocked at 63 processes |
| Read `/root` | Permission denied |
| Check for host paths (`/c/Users`) | Does not exist |
| Symlink to host file | Resolves inside container |

### 9.2 API Security

| Check | Result |
|-------|--------|
| Protected endpoints without token | 401 |
| Malformed / garbage tokens | 401 |
| `alg:none` JWT downgrade | 401 |
| Reading another user's submission | 403 |
| Submission list scoped to owner | Confirmed |
| Source over 256 KB | 413 |
| Blank source / invalid language | 400 |
| Password in any response body | Absent |
| Passwords stored as BCrypt | Confirmed (`$2...`) |
| Secrets committed to git | None |

---

## 10. Issues Found and Fixed During the Audit

1. **Large submissions failed silently (availability).** Source above roughly
   90 KB could not be written into the container: base64 inflates the payload by
   a third and the resulting exec command exceeded the shell argument limit,
   failing with exit 255 and surfacing as a misleading `RUNTIME_ERROR`. Since the
   platform accepts up to 256 KB, the write is now chunked across multiple small
   execs. Verified: submissions of 14 KB to 215 KB now succeed.
2. **Stale per-submission results.** The judge saved `SubmissionTestResult` rows
   directly, leaving the `Submission.testResults` collection stale inside the
   same persistence context, so the detail endpoint could return an empty test
   list. The endpoint now reads through the repository.

## 11. Known Residual Risks

- **No rate limiting.** Login and submission endpoints are unthrottled, so an
  attacker could brute-force credentials or flood the judge. This is the most
  significant remaining gap.
- **Peak memory includes compilation.** Reported memory is the container's cgroup
  peak, which includes toolchain startup, so it overstates a submission's usage.
- **OOM detection is a stderr heuristic.** A program printing `OutOfMemoryError`
  while exiting non-zero would be misclassified as `MEMORY_LIMIT_EXCEEDED`.
- **Judging is synchronous**, so a submission holds a request thread for the
  full compile and run duration.
- **Sandbox isolation is Docker-on-Linux semantics.** Under Docker Desktop the
  boundary sits at the Linux VM layer rather than the bare-metal kernel.

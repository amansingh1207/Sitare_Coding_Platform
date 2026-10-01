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

The following tests must pass before deployment:

- [ ] Student code cannot access the network
- [ ] Student code cannot read files outside `/tmp`
- [ ] Student code cannot spawn more than 64 processes
- [ ] Student code cannot use more than 256 MB of memory
- [ ] Student code cannot access the Docker socket
- [ ] Student code cannot read environment variables
- [ ] Containers are destroyed after execution
- [ ] Hidden test cases are never returned by the API
- [ ] Users cannot access other users' submissions
- [ ] All endpoints require authentication (except auth)

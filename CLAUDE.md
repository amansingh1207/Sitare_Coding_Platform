# CLAUDE.md — Claude Agent Operational Instructions

This file provides operational instructions for Claude-compatible agent workflows on the CodingJudge project.

---

## Build Commands

There is no Maven wrapper in this repository; use a local Maven 3.8+.

### Backend (Spring Boot + Maven)

```bash
# Navigate to backend
cd backend

# Compile
mvn compile

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=FullUserFlowIntegrationTest

# Package (skip tests)
mvn package -DskipTests

# Run application
mvn spring-boot:run
```

### Frontend (React + TypeScript + Vite)

```bash
# Navigate to frontend
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev

# Run tests
npm test

# Run tests with coverage
npm run test:coverage

# Type check
npm run type-check

# Build for production
npm run build
```

### Judge

The judge is Java code inside the backend, not a separate service:

```bash
# Judge source
backend/src/main/java/com/codingjudge/judge/

# Judge tests run as part of the backend suite
cd backend && mvn test -Dtest=JudgeEngineTest

# The live container path needs the sandbox image
docker build -t codingjudge/sandbox:latest docker/sandbox
```

### Docker (Full Stack)

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up -d --build
```

---

## Directory Map

| Path | Purpose |
|------|---------|
| `backend/src/main/java/com/codingjudge/` | Spring Boot application source |
| `backend/src/main/java/com/codingjudge/judge/` | Judge engine: sandbox, executor, verdict |
| `backend/src/main/resources/db/migration/` | Flyway migrations |
| `backend/src/test/` | Backend unit, integration, security and flow tests |
| `frontend/src/components/` | Reusable UI components |
| `frontend/src/pages/` | Page-level components |
| `frontend/src/api/` | API client functions |
| `frontend/src/hooks/` | Custom React hooks |
| `frontend/src/utils/` | Formatting helpers (duration, status, templates) |
| `docs/` | All specification documents |
| `docker/sandbox/` | Judge sandbox image definition |
| `tests/` | Reserved for cross-cutting suites; currently empty |

---

## Important Conventions

### API Response Format

All API responses follow this structure:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Error responses:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable message",
    "details": { ... }
  }
}
```

### Authentication

- JWT tokens are passed in the `Authorization: Bearer <token>` header.
- Tokens expire after 24 hours (configurable via `JWT_EXPIRATION_MS`).
- All endpoints except `/api/auth/**` require authentication.

### Judge Result Codes

| Code | Meaning |
|------|---------|
| `ACCEPTED` | All test cases passed |
| `WRONG_ANSWER` | Output did not match expected |
| `COMPILATION_ERROR` | Source code failed to compile |
| `RUNTIME_ERROR` | Program crashed during execution |
| `TIME_LIMIT_EXCEEDED` | Execution exceeded time limit |
| `MEMORY_LIMIT_EXCEEDED` | Execution exceeded memory limit |
| `INTERNAL_ERROR` | Judge infrastructure failure |

---

## Validation Commands

```bash
# Backend compilation check
cd backend && mvn compile

# Backend test suite
cd backend && mvn test

# Frontend type check
cd frontend && npm run type-check

# Frontend tests
cd frontend && npm test

# Full Docker stack health check
docker-compose ps
```

---

## Testing Notes

The backend suite must not require a Docker daemon. The `test` profile
(`application-test.properties` plus `TestJudgeConfig`) replaces `DockerSandbox`
with a stub that selects outcomes from marker comments in the submitted source:
`CE`, `RE`, `TLE`, `OOM`, `WRONG`, and unmarked source treated as a correct
solution. This keeps verdict mapping testable, but it cannot prove the container
behaves as designed.

**Verify judge changes against real Docker as well.** Several defects in this
codebase passed every unit test and were only found by live submission; they are
recorded in `docs/JUDGE_DESIGN.md` sections 11.6 and 11.7.

Frontend tests run in Vitest. The default environment is `node`; files that need
a DOM opt in per file with a `// @vitest-environment jsdom` docblock. Shared
helpers live in `src/test/render.tsx`:

- `renderWithRouter` wraps a component in `MemoryRouter`; pass `path` when the
  component reads route params.
- `renderAuthenticated` adds `AuthProvider` for pages that read auth state.
- `ok` / `fail` build Response stand-ins carrying the backend's `ApiResponse`
  envelope, so `parseEnvelope` runs its real success and error paths.

Mock `fetch` rather than the API modules, so URL, method and request body are all
asserted. `ProblemDetailPage.test.tsx` is the reference: it asserts the run call
targets `/submissions/run` and not `/submissions`, which is the guard against the
exact regression that once left the Run button returning 404.

---

## Forbidden Operations

- Do NOT run `mvn spring-boot:run` without PostgreSQL running
- Do NOT execute student code outside Docker
- Do NOT modify database schema without a migration
- Do NOT commit `.env` files
- Do NOT weaken tests to make them pass
- Do NOT add features outside the current specification
- Do NOT introduce Redis or message queues unless explicitly approved

---

## Security Constraints

- All student code MUST execute inside Docker containers
- Docker containers MUST have: no network, limited memory, limited CPU, read-only filesystem (except tmp)
- Hidden test cases MUST NEVER be returned to the client
- Passwords MUST be hashed with BCrypt
- Secrets MUST come from environment variables
- API input MUST be validated and sanitized

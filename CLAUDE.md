# CLAUDE.md — Claude Agent Operational Instructions

This file provides operational instructions for Claude-compatible agent workflows on the CodingJudge project.

---

## Build Commands

### Backend (Spring Boot + Maven)

```bash
# Navigate to backend
cd backend

# Compile
./mvnw compile

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=SubmissionServiceTest

# Package (skip tests)
./mvnw package -DskipTests

# Run application
./mvnw spring-boot:run
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

```bash
# Navigate to judge
cd judge

# Run tests (Python-based judge tests)
python -m pytest tests/

# Run specific test
python -m pytest tests/test_judge.py -v
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
| `backend/src/main/java/` | Spring Boot application source |
| `backend/src/main/resources/` | Configuration, migrations |
| `backend/src/test/` | Backend tests |
| `frontend/src/` | React application source |
| `frontend/src/components/` | Reusable UI components |
| `frontend/src/pages/` | Page-level components |
| `frontend/src/api/` | API client functions |
| `frontend/src/hooks/` | Custom React hooks |
| `judge/` | Judge execution engine |
| `judge/src/` | Judge source code |
| `judge/tests/` | Judge tests |
| `docs/` | All specification documents |
| `docker/` | Dockerfiles and sandbox configs |
| `tests/` | Cross-cutting test suites |

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
cd backend && ./mvnw compile

# Backend test suite
cd backend && ./mvnw test

# Frontend type check
cd frontend && npm run type-check

# Frontend tests
cd frontend && npm test

# Full Docker stack health check
docker-compose ps
```

---

## Forbidden Operations

- Do NOT run `./mvnw spring-boot:run` without PostgreSQL running
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

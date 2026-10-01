# AGENTS.md — CodingJudge Project Instructions

This file provides instructions for AI coding agents (OpenCode, Claude Code, Cursor, Copilot, etc.) working on the CodingJudge project.

---

## 1. Project Purpose

CodingJudge is a **post-contest coding practice and online judge platform** for students. After weekly graded contests (conducted via Secure Exam Browser), students use this platform to:

- Browse previous programming problems
- Read complete problem statements
- Write solutions in an online editor
- Run code against sample test cases
- Submit solutions against hidden test cases
- Receive judging results (Accepted, Wrong Answer, Compilation Error, etc.)
- View submission history
- Optionally use a practice timer

This is **NOT** a live contest platform. There are no live contests, rankings, or scoring.

---

## 2. Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3 + Java 17 |
| Database | PostgreSQL 16 |
| Judge | Docker-based sandboxed execution |
| Auth | JWT (JSON Web Tokens) |
| Build (Backend) | Maven |
| Build (Frontend) | npm / Vite |

---

## 3. Directory Structure

```
coding-judge/
├── AGENTS.md                  # This file — general agent instructions
├── CLAUDE.md                  # Claude-specific operational instructions
├── README.md                  # Human-facing project overview
├── .gitignore                 # Git ignore rules
├── .env.example               # Environment variable template
├── docker-compose.yml         # Docker Compose for local development
│
├── docs/                      # All specification documents
│   ├── PRODUCT_SPEC.md        # Product requirements (source of truth)
│   ├── ARCHITECTURE.md        # System architecture
│   ├── DATABASE.md            # Database schema design
│   ├── API_SPEC.md            # REST API specification
│   ├── JUDGE_DESIGN.md        # Judge engine design
│   ├── SECURITY.md            # Security requirements
│   └── DEVELOPMENT_PLAN.md    # Phased implementation plan
│
├── frontend/                  # React + TypeScript frontend
├── backend/                   # Spring Boot backend
├── judge/                     # Judge execution engine
├── tests/                     # Test suites
│   ├── backend/               # Backend unit/integration tests
│   ├── frontend/              # Frontend tests
│   ├── judge/                 # Judge engine tests
│   └── integration/           # End-to-end integration tests
└── docker/                    # Dockerfiles and sandbox configs
```

---

## 4. Architecture Summary

CodingJudge uses a **modular monolith** architecture:

```
┌─────────────┐     HTTP/REST      ┌──────────────────┐
│   Frontend  │ ◄────────────────► │  Spring Boot API │
│  (React)    │                    │   (Backend)      │
└─────────────┘                    └────────┬─────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │   PostgreSQL DB  │
                                   └──────────────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │   Judge Worker   │
                                   │  (Docker exec)   │
                                   └──────────────────┘
```

- The **frontend** is a React SPA that communicates with the backend via REST API.
- The **backend** is a Spring Boot application handling auth, problems, submissions, and orchestrating the judge.
- The **judge** executes student code inside Docker containers with strict resource limits.
- The **database** stores users, problems, test cases, and submissions.

---

## 5. Development Conventions

### 5.1 Code Style

- **Java**: Follow Google Java Style Guide. Use meaningful names. Keep methods small.
- **TypeScript/React**: Use functional components with hooks. Prefer `const` over `let`. Use strict TypeScript.
- **SQL**: Use snake_case for table and column names.

### 5.2 Naming

- Java classes: `PascalCase` (e.g., `SubmissionService`)
- Java methods/variables: `camelCase` (e.g., `findById`)
- React components: `PascalCase` (e.g., `ProblemList`)
- Database tables: `snake_case` plural (e.g., `submission_test_results`)
- API endpoints: `kebab-case` (e.g., `/api/problems/:slug`)

### 5.3 Commits

Use conventional commit format:

```
feat: add problem listing endpoint
feat: implement Docker judge for Java
test: add judge timeout tests
fix: handle runtime error in C++ execution
docs: update API specification
```

### 5.4 Testing

- Every feature MUST have tests.
- Judge tests MUST cover all result types (AC, WA, CE, RE, TLE, MLE).
- Run tests before committing.

---

## 6. Security Rules

These are **non-negotiable**:

1. **NEVER** execute student code directly on the host. Always use Docker sandbox.
2. **NEVER** expose hidden test case input/output through any API.
3. **NEVER** store plaintext passwords. Use BCrypt.
4. **NEVER** hardcode secrets. Use environment variables.
5. **NEVER** commit `.env` files or credentials.
6. **ALWAYS** validate and sanitize API input.
7. **ALWAYS** authenticate protected endpoints.
8. **ALWAYS** authorize user-specific resource access.
9. **NEVER** give student code access to the Docker socket.
10. **ALWAYS** clean up Docker containers after execution.

---

## 7. Things That Must NEVER Be Done

- Do NOT implement live contest functionality (no countdowns, rankings, scoring).
- Do NOT build an admin dashboard in the MVP.
- Do NOT add social features, chat, or gamification.
- Do NOT use mock/fake judging logic in production flow.
- Do NOT skip tests or weaken tests to make them pass.
- Do NOT introduce unnecessary infrastructure (Redis, message queues) unless genuinely needed.
- Do NOT couple the frontend directly to Docker execution.
- Do NOT hardcode language-specific logic throughout the application — use the language abstraction.

---

## 8. Workflow for Making Changes

### Step 1 — Understand

Before modifying code:
- Read `AGENTS.md`, `CLAUDE.md`, and relevant docs
- Inspect the relevant source files
- Identify dependencies and tests

### Step 2 — Plan

Before editing:
- Identify affected files
- Explain implementation plan
- Identify risks

### Step 3 — Implement

- Make the smallest clean implementation
- Follow existing patterns and conventions

### Step 4 — Test

- Run relevant automated tests
- Do NOT claim success without running tests

### Step 5 — Validate

- Check functional correctness
- Check security implications
- Check architecture consistency
- Check API consistency

### Step 6 — Report

- Summarize what changed
- List files changed
- Report test results
- Note known limitations
- Suggest next steps

---

## 9. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Modular monolith | Simpler deployment, no unnecessary distributed complexity |
| JWT auth | Stateless, simple, sufficient for single-server deployment |
| Docker sandbox | Industry standard for code isolation |
| Language abstraction | Easy to add new languages without rewriting judge |
| Standard output comparison | Simple, predictable, well-understood by students |
| PostgreSQL | Robust, open-source, supports complex queries |

---

## 10. Documentation as Source of Truth

When requirements change:
1. Update the relevant specification document first
2. Then update implementation
3. Then update tests
4. Update documentation if necessary

Do NOT rely on conversation history as the only record of a requirement.

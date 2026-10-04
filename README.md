# CodingJudge

A **post-contest coding practice and online judge platform** for students.

After weekly graded coding contests, students use CodingJudge to practice problems, verify their solutions, and learn from their mistakes — all in a safe, sandboxed environment.

---

## Problem Being Solved

Students participate in weekly graded coding contests using a Secure Exam Browser (SEB). After the contest, they receive problem statements and test cases, but have **no platform to verify whether their solutions would be accepted**.

CodingJudge solves this by providing:

- A browsable library of past contest problems
- An online code editor with multi-language support
- Safe code execution against sample and hidden test cases
- Detailed judging results (Accepted, Wrong Answer, Compilation Error, etc.)
- Submission history for tracking progress

---

## Major Features

- **Problem Browsing** — Browse, search, and view past contest problems organized by week
- **Online Code Editor** — Write code in Java, C++, or Python with syntax highlighting
- **Run Code** — Execute code against visible sample test cases
- **Submit Code** — Submit solutions against the full test suite (including hidden tests)
- **Judging Results** — See Accepted, Wrong Answer, Compilation Error, Runtime Error, TLE, or MLE
- **Submission History** — View all past submissions with status, runtime, and memory usage
- **Practice Timer** — Optional timer to track time spent on each problem
- **Multi-Language Support** — Java, C++, and Python with an extensible language system

---

## Architecture Overview

```
┌─────────────┐     HTTP/REST      ┌──────────────────┐
│   Frontend  │ ◄────────────────► │  Spring Boot API │
│  (React)    │                    │   (Backend)      │
└─────────────┘                    └────────┬─────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │   PostgreSQL DB  │
                                   │ (Neon, managed)  │
                                   └────────┬─────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │ DB-backed worker │
                                   │ queue (PENDING → │
                                   │ JUDGING → verdict)│
                                   └────────┬─────────┘
                                            │
                        ┌───────────────────┼───────────────────┐
                        │                   │                   │
               ┌────────▼─────────┐ ┌───────▼────────┐ ┌───────▼────────┐
               │ Docker sandbox   │ │ Judge0 (aband.)│ │ DOMjudge (AWS) │
               │ (local judges)   │ │ (not used)     │ │ (staging judge)│
               └──────────────────┘ └────────────────┘ └────────────────┘
```

**Modular Monolith**: A single Spring Boot backend serves the REST API and
orchestrates judging. Submissions are saved as `PENDING`, claimed atomically
by a bounded worker pool (`PENDING → JUDGING → terminal verdict`), and judged
through a `CodeExecutionService` provider abstraction — Docker sandbox locally,
DOMjudge on AWS for staging. The frontend polls `GET /api/submissions/{id}`
until the verdict is terminal.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, TypeScript, Vite |
| Backend | Spring Boot 3, Java 17 |
| Database | PostgreSQL (Neon managed, Flyway migrations) |
| Local judge | Docker-based sandboxed execution |
| Staging judge | DOMjudge 9 on AWS EC2 (Sydney) behind Caddy + Let's Encrypt IP certificate |
| Auth | JWT (JSON Web Tokens) + email OTP |
| Email | SendGrid |
| Backend hosting | Render |

---

## Local Setup

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.8+
- Node.js 18+

### Quick Start

```bash
# 1. Clone the repository
git clone <repo-url>
cd coding-judge

# 2. Copy environment configuration
cp .env.example .env
# Edit .env with your values

# 3. Start all services
docker-compose up -d

# 4. Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080/api
```

### Manual Setup (Development)

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Start backend (from backend/ directory)
cd backend
mvn spring-boot:run

# Start frontend (from frontend/ directory)
cd frontend
npm install
npm run dev
```

The backend also needs a built sandbox image, which the judge runs student code
inside:

```bash
docker build -t codingjudge/sandbox:latest docker/sandbox
```

---

## Development Commands

### Backend

```bash
cd backend
mvn compile             # Compile
mvn test                # Run tests
mvn package             # Package and run tests
mvn package -DskipTests # Package only
mvn spring-boot:run     # Run application
```

### Frontend

```bash
cd frontend
npm install             # Install dependencies
npm run dev             # Dev server
npm test                # Run tests
npm run type-check      # Type check
npm run build           # Production build
```

### Docker

```bash
docker-compose up -d           # Start all services
docker-compose logs -f backend # View backend logs
docker-compose down            # Stop all services
docker-compose up -d --build   # Rebuild and start
```

---

## How to Run Tests

```bash
# Backend: unit, integration, security and end-to-end flow tests
cd backend && mvn test

# Frontend
cd frontend && npm test
cd frontend && npm run type-check
```

The backend suite needs no Docker daemon. The `test` profile swaps the real
`DockerSandbox` for a stub (`TestJudgeConfig`) that returns outcomes selected by
marker comments in the submitted source, so every verdict branch can be driven
deterministically. The live container path is verified separately against real
Docker; see [JUDGE_DESIGN.md](docs/JUDGE_DESIGN.md) sections 11.5 and 11.7.

---

## High-Level Judge Explanation

The judge is the core of CodingJudge. Student-submitted code is **untrusted** and must never run directly on the host.

### Submission Flow

```
Student submits code
       │
       ▼
Spring Boot API creates Submission record (status PENDING)
       │
       ▼
DB-backed worker queue claims it (PENDING → JUDGING, bounded pool)
       │
       ▼
JudgeEngine routes to the configured provider
(Docker sandbox locally, DOMjudge submission on staging)
       │
       ▼
Code is compiled (if needed) and executed against each test case
       │
       ▼
Output is compared with expected output
(DOMjudge path: DOMjudge itself is the verdict authority)
       │
       ▼
Verdict is determined and persisted
       │
       ▼
Frontend (polling) displays the result
```

Judging happens **asynchronously** through the worker queue: the submit
response carries the new submission id, and the frontend polls
`GET /api/submissions/{id}` until the status leaves `PENDING`/`JUDGING`.
Bursts queue up instead of crashing the server; the pool is bounded
(2–4 workers, no Redis/RabbitMQ by design).

"Run Code" takes a separate path: it executes only the visible sample test cases
and creates no submission record, so iterating in the editor neither reveals
hidden tests nor pollutes history.

### Sandbox Security

Each Docker container (local judge path):
- Has **no network access**
- Has **limited memory** (default 256 MB)
- Has **limited CPU** (default 1 core)
- Has a **read-only filesystem** (except `/workspace` and `/tmp`)
- Is **destroyed** after execution
- Has **no access** to the Docker socket or host resources

On the DOMjudge path, student code runs inside DOMjudge judgehosts
(Docker-isolated chroots on the AWS box) under the same never-on-host
guarantee, and hidden test data never leaves the server: submission detail
responses carry sample cases only.

---

## Documentation

Detailed documentation is in the `docs/` directory:

| Document | Purpose |
|----------|---------|
| [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Product requirements (source of truth) |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture |
| [DATABASE.md](docs/DATABASE.md) | Database schema design |
| [API_SPEC.md](docs/API_SPEC.md) | REST API specification |
| [JUDGE_DESIGN.md](docs/JUDGE_DESIGN.md) | Judge engine design (Docker path) |
| [DOMJUDGE_DESIGN.md](docs/DOMJUDGE_DESIGN.md) | DOMjudge integration design |
| [DOMJUDGE_AWS.md](docs/DOMJUDGE_AWS.md) | AWS judge host evidence |
| [DOMJUDGE_POC.md](docs/DOMJUDGE_POC.md) | DOMjudge proof-of-concept record |
| [JUDGE0.md](docs/JUDGE0.md) | Judge0 evaluation (abandoned, kept for record) |
| [JUDGE0_POC.md](docs/JUDGE0_POC.md) | Judge0 proof-of-concept record |
| [SECURITY.md](docs/SECURITY.md) | Security requirements |
| [DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | Phased implementation plan |

---

## Project Status

All sixteen planned phases are complete. The platform supports registration and
JWT login, problem browsing with search and filters, an editor for Java, C++ and
Python, sample-test runs, judged submissions, submission history and a practice
timer. On top of that: a DB-backed worker queue, a DOMjudge execution provider
(staged end-to-end on AWS), and production hosting on Render + Neon.

| Area | Coverage |
|------|----------|
| Backend tests | 177 passing (JUnit, incl. judge provider + queue + security suites) |
| Frontend tests | 96 passing across 12 files (API client, utilities, DOM-level component and page tests) |
| Live judge verification | All 7 verdicts, all 3 languages (local Docker + AWS DOMjudge), burst-tested |

See [DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) for the per-phase record,
including what each phase actually changed and the issues found along the way.
Known limitations are listed in [SECURITY.md](docs/SECURITY.md) section 11.

---

## Production Deployment

| Piece | Where | Notes |
|-------|-------|-------|
| Backend | Render (`sitare-coding-platform.onrender.com`) | `server.address=0.0.0.0`, honors `PORT`; health at `/api/health` |
| Frontend | Render static site | `VITE_API_BASE_URL` is baked at build time — redeploy after changing it |
| Database | Neon PostgreSQL (managed) | Flyway migrations run on boot |
| Staging judge | AWS EC2 `ap-southeast-2` (t3.micro) | DOMjudge 9 (domserver + judgehost + MariaDB), tuned for 1 GiB RAM |
| Judge HTTPS | Caddy on the EC2 box | Let's Encrypt IP certificate, auto-renew via systemd + deploy hook |
| Email | SendGrid | OTP + password reset |

Rules that stay in force: production default is `EXECUTION_PROVIDER=docker`
(switch only with explicit approval); the judge port is never exposed
publicly — traffic goes through Caddy; no secrets are committed (`.env`
files are gitignored, CI/CD uses secret stores).

---

## License

Private project. All rights reserved.

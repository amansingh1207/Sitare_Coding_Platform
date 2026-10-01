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
                                   └──────────────────┘
                                            │
                                   ┌────────▼─────────┐
                                   │   Judge Worker   │
                                   │  (Docker exec)   │
                                   └──────────────────┘
```

**Modular Monolith**: A single Spring Boot backend serves the REST API and orchestrates the judge. The judge executes student code inside isolated Docker containers.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, TypeScript, Vite |
| Backend | Spring Boot 3, Java 17 |
| Database | PostgreSQL 16 |
| Judge | Docker-based sandboxed execution |
| Auth | JWT (JSON Web Tokens) |

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
./mvnw spring-boot:run

# Start frontend (from frontend/ directory)
cd frontend
npm install
npm run dev
```

---

## Development Commands

### Backend

```bash
cd backend
./mvnw compile          # Compile
./mvnw test             # Run tests
./mvnw package -DskipTests  # Package
./mvnw spring-boot:run  # Run application
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
docker-compose logs -f backend  # View backend logs
docker-compose down             # Stop all services
docker-compose up -d --build    # Rebuild and start
```

---

## How to Run Tests

```bash
# Backend tests
cd backend && ./mvnw test

# Frontend tests
cd frontend && npm test

# Judge tests
cd judge && python -m pytest tests/

# Integration tests
cd tests/integration && ./run-tests.sh
```

---

## High-Level Judge Explanation

The judge is the core of CodingJudge. Student-submitted code is **untrusted** and must never run directly on the host.

### Submission Flow

```
Student submits code
       │
       ▼
Spring Boot API creates Submission record
       │
       ▼
Judge Worker picks up the submission
       │
       ▼
Docker container is created with resource limits
       │
       ▼
Code is compiled (if needed) inside container
       │
       ▼
Compiled program is executed against each test case
       │
       ▼
Output is compared with expected output
       │
       ▼
Result is determined and persisted
       │
       ▼
Frontend displays the result
```

### Sandbox Security

Each Docker container:
- Has **no network access**
- Has **limited memory** (default 256 MB)
- Has **limited CPU** (default 1 core)
- Has a **read-only filesystem** (except `/tmp`)
- Is **destroyed** after execution
- Has **no access** to the Docker socket or host resources

---

## Documentation

Detailed documentation is in the `docs/` directory:

| Document | Purpose |
|----------|---------|
| [PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | Product requirements (source of truth) |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture |
| [DATABASE.md](docs/DATABASE.md) | Database schema design |
| [API_SPEC.md](docs/API_SPEC.md) | REST API specification |
| [JUDGE_DESIGN.md](docs/JUDGE_DESIGN.md) | Judge engine design |
| [SECURITY.md](docs/SECURITY.md) | Security requirements |
| [DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | Phased implementation plan |

---

## Project Status

This project is currently in **Phase 0** (specification and planning). See [DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) for the full implementation roadmap.

---

## License

Private project. All rights reserved.

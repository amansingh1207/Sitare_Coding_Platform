# PRODUCT_SPEC.md — CodingJudge Product Specification

**Version:** 1.0
**Status:** Draft
**Last Updated:** 2026-10-02

---

## 1. Target Users

### Primary Users: Students

- University students who participate in weekly graded coding contests
- Need a platform to practice past contest problems after the contest ends
- Want to verify whether their solutions would be accepted
- Need to see compilation errors, runtime errors, and wrong answers to learn

### Secondary Users: Professors

- Conduct weekly coding contests using Secure Exam Browser (SEB)
- Add new problems to the platform after each contest
- Do NOT need a full admin dashboard in the MVP (problems can be inserted via database mechanisms)

---

## 2. Problem Statement

Students participate in weekly graded coding contests using SEB. After the contest, they receive problem statements and test cases, but **have no platform to submit solutions and verify correctness**.

This creates a gap: students cannot practice effectively, cannot learn from their mistakes, and cannot build confidence before the next contest.

---

## 3. Product Goals

1. Provide a browsable library of past contest problems
2. Allow students to write and test code in an online editor
3. Execute student code safely against sample and hidden test cases
4. Provide clear judging results (Accepted, Wrong Answer, etc.)
5. Track submission history for progress monitoring
6. Support multiple programming languages (Java, C++, Python)
7. Organize problems by weekly contest source

---

## 4. Functional Requirements

### 4.1 Problem Browsing

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01 | Students can view a list of all available problems | Must |
| FR-02 | Students can search problems by title | Must |
| FR-03 | Students can filter problems by week/source label | Must |
| FR-04 | Students can open a problem and read its full statement | Must |
| FR-05 | Students can see input format, output format, and constraints | Must |
| FR-06 | Students can see sample test cases (input + expected output) | Must |
| FR-07 | Students can see problem difficulty | Must |
| FR-08 | Students can see execution time limit and memory limit | Must |
| FR-09 | Problems are organized by weekly/source label | Must |

### 4.2 Code Editor

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-10 | Students can write code in an online editor | Must |
| FR-11 | Editor supports Java, C++, and Python | Must |
| FR-12 | Students can switch between languages | Must |
| FR-13 | Editor provides syntax highlighting | Should |
| FR-14 | Students can reset to default starter code | Should |
| FR-15 | Language system is extensible for future languages | Must |

### 4.3 Run Code

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-16 | Students can run code against visible sample test cases only | Must |
| FR-17 | Students see per-test-case status (pass/fail) | Must |
| FR-18 | Students see actual output for each test case | Must |
| FR-19 | Students see expected output for each test case | Must |
| FR-20 | Students see compilation errors | Must |
| FR-21 | Students see runtime errors | Must |
| FR-22 | Students see timeout errors | Must |
| FR-23 | Students see execution time | Must |
| FR-24 | Hidden test cases are NEVER exposed during Run Code | Must |

### 4.4 Submit Code

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-25 | Students can submit code against the full test suite | Must |
| FR-26 | Full test suite includes visible + hidden tests | Must |
| FR-27 | Hidden test input/output is NEVER returned to client | Must |
| FR-28 | Submission results include: ACCEPTED, WRONG_ANSWER, COMPILATION_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, INTERNAL_ERROR | Must |
| FR-29 | Submissions are persisted in the database | Must |
| FR-30 | Each submission records: problem, language, status, runtime, memory, timestamp | Must |

### 4.5 Submission History

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-31 | Students can view their own submission history | Must |
| FR-32 | Each submission shows: problem, language, status, runtime, memory, time | Must |
| FR-33 | Students can view submitted source code | Must |
| FR-34 | Students cannot see hidden test case contents | Must |

### 4.6 Practice Timer

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-35 | Students can start/pause/reset a practice timer per problem | Should |
| FR-36 | Timer displays elapsed time (HH:MM:SS) | Should |
| FR-37 | Timer does NOT affect judging | Must |
| FR-38 | Timer is optional | Must |

### 4.7 Authentication

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-39 | Students can register an account | Must |
| FR-40 | Students can log in | Must |
| FR-41 | Students can view their current profile | Must |
| FR-42 | Passwords are securely hashed (BCrypt) | Must |
| FR-43 | Protected endpoints require authentication | Must |
| FR-44 | Students can only access their own submissions | Must |

---

## 5. Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-01 | Page load time | < 2 seconds |
| NFR-02 | API response time (non-judge) | < 500ms |
| NFR-03 | Judge execution time (per test case) | < 10 seconds |
| NFR-04 | Concurrent submissions | At least 10 |
| NFR-05 | Availability | 99.5% during contest periods |
| NFR-06 | Data retention | All submissions retained indefinitely |
| NFR-07 | Browser support | Chrome, Firefox, Safari, Edge (latest 2 versions) |

---

## 6. Supported Languages

| Language | Version | Compilation | Execution |
|----------|---------|-------------|-----------|
| Java | 17 | `javac` | `java` |
| C++ | C++17 | `g++ -std=c++17` | Native binary |
| Python | 3.11 | None (interpreted) | `python3` |

The language system is designed as an abstraction so additional languages can be added without rewriting the judge.

---

## 7. Judging Statuses

| Status | Description | When |
|--------|-------------|------|
| `ACCEPTED` | All test cases passed | Output matches expected for all tests |
| `WRONG_ANSWER` | Output did not match | At least one test case output differs |
| `COMPILATION_ERROR` | Source failed to compile | Compiler returned non-zero exit code |
| `RUNTIME_ERROR` | Program crashed | Non-zero exit code during execution |
| `TIME_LIMIT_EXCEEDED` | Execution too slow | Execution time exceeded limit |
| `MEMORY_LIMIT_EXCEEDED` | Too much memory used | Memory usage exceeded limit |
| `INTERNAL_ERROR` | Judge infrastructure failure | Docker failure, unexpected error |

---

## 8. Future Extensibility

The following are designed for but NOT implemented in the MVP:

- **Custom checkers** — The output comparison strategy is abstracted so custom checkers can be added later
- **Additional languages** — The language abstraction allows adding languages (e.g., C#, Go, Rust)
- **Admin dashboard** — The data model supports problem management; UI can be added later
- **Live contests** — The architecture can be extended to support live contests
- **Plagiarism detection** — Can be added as a post-submission analysis step

---

## 9. Explicit Out of Scope

The following are **NOT** part of this product:

- Live contests or contest registration
- Contest countdown timers
- Contest leaderboards or rankings
- Contest scoring
- SEB integration
- Live proctoring or webcam monitoring
- Plagiarism detection
- AI code generation or solution explanation
- Social features (chat, forums, comments)
- Unnecessary gamification (badges, points, levels)
- Mobile applications

---

## 10. Success Metrics

| Metric | Target |
|--------|--------|
| Students can complete full flow (register → solve → submit → see result) | < 5 minutes |
| Judge correctly identifies all 7 result types | 100% |
| Zero security incidents (code escaping sandbox) | 0 |
| Student satisfaction | > 4.0 / 5.0 |

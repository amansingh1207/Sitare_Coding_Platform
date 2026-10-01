# API_SPEC.md — CodingJudge REST API Specification

**Version:** 1.0
**Status:** Draft
**Last Updated:** 2026-10-02

---

## 1. Conventions

### 1.1 Base URL

```
http://localhost:8080/api
```

### 1.2 Authentication

All endpoints except `/api/auth/**` require a JWT token in the `Authorization` header:

```
Authorization: Bearer <token>
```

### 1.3 Response Format

All responses follow this structure:

**Success:**
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

**Error:**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "details": { ... }
  }
}
```

### 1.4 HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 | Successful GET, PUT, DELETE |
| 201 | Successful creation |
| 202 | Accepted (async processing started) |
| 400 | Bad request / validation error |
| 401 | Unauthorized (missing/invalid token) |
| 403 | Forbidden (insufficient permissions) |
| 404 | Resource not found |
| 409 | Conflict (e.g., duplicate email) |
| 413 | Payload too large |
| 422 | Unprocessable entity |
| 500 | Internal server error |

---

## 2. Authentication Endpoints

### 2.1 Register

Create a new student account.

**Endpoint:** `POST /api/auth/register`
**Auth:** No

**Request:**
```json
{
  "email": "student@university.edu",
  "username": "student123",
  "password": "securePassword123",
  "fullName": "John Doe"
}
```

**Validation:**
- `email`: required, valid email format, max 255 chars
- `username`: required, 3-50 chars, alphanumeric + underscore
- `password`: required, min 8 chars, must contain letter and number
- `fullName`: required, max 255 chars

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "student@university.edu",
    "username": "student123",
    "fullName": "John Doe",
    "role": "STUDENT"
  },
  "error": null
}
```

**Errors:**
- `409 CONFLICT` — Email or username already exists
- `400 BAD_REQUEST` — Validation failed

---

### 2.2 Login

Authenticate and receive JWT token.

**Endpoint:** `POST /api/auth/login`
**Auth:** No

**Request:**
```json
{
  "email": "student@university.edu",
  "password": "securePassword123"
}
```

**Validation:**
- `email`: required, valid email format
- `password`: required

**Response (200):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "email": "student@university.edu",
      "username": "student123",
      "fullName": "John Doe",
      "role": "STUDENT"
    }
  },
  "error": null
}
```

**Errors:**
- `401 UNAUTHORIZED` — Invalid credentials

---

### 2.3 Current User

Get the currently authenticated user's profile.

**Endpoint:** `GET /api/auth/me`
**Auth:** Yes

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "student@university.edu",
    "username": "student123",
    "fullName": "John Doe",
    "role": "STUDENT"
  },
  "error": null
}
```

**Errors:**
- `401 UNAUTHORIZED` — Missing or invalid token

---

## 3. Problem Endpoints

### 3.1 List Problems

Get a list of all available problems.

**Endpoint:** `GET /api/problems`
**Auth:** Yes

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `search` | string | No | Search by title |
| `week` | string | No | Filter by week label (e.g., "Week 1") |
| `difficulty` | string | No | Filter by difficulty (EASY, MEDIUM, HARD) |
| `page` | integer | No | Page number (default: 0) |
| `size` | integer | No | Page size (default: 20, max: 100) |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "slug": "power-cut",
        "title": "Power Cut",
        "difficulty": "EASY",
        "weekLabel": "Week 1",
        "timeLimitMs": 2000,
        "memoryLimitMb": 256
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 8,
    "totalPages": 1
  },
  "error": null
}
```

---

### 3.2 Get Problem Details

Get full details of a specific problem, including sample test cases.

**Endpoint:** `GET /api/problems/{slug}`
**Auth:** Yes

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | string | Problem URL slug |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "slug": "power-cut",
    "title": "Power Cut",
    "statement": "## Problem Description\n\nA power cut has occurred...",
    "inputFormat": "The first line contains an integer T (1 ≤ T ≤ 100)...",
    "outputFormat": "For each test case, output a single integer...",
    "constraints": "1 ≤ T ≤ 100\n1 ≤ N ≤ 10^5",
    "difficulty": "EASY",
    "weekLabel": "Week 1",
    "timeLimitMs": 2000,
    "memoryLimitMb": 256,
    "sampleTestCases": [
      {
        "id": 1,
        "inputData": "2\n3\n1 2 3\n4\n1 2 3 4",
        "expectedOutput": "6\n10"
      }
    ]
  },
  "error": null
}
```

**Note:** Only sample test cases (`is_sample = true`) are returned. Hidden test cases are never exposed.

**Errors:**
- `404 NOT_FOUND` — Problem not found

---

## 4. Execution Endpoints

### 4.1 Run Code

Run student code against **visible sample test cases only**.

**Endpoint:** `POST /api/submissions/run`
**Auth:** Yes

**Request:**
```json
{
  "problemId": 1,
  "language": "JAVA",
  "sourceCode": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int t = sc.nextInt();\n        while (t-- > 0) {\n            int n = sc.nextInt();\n            int sum = 0;\n            for (int i = 0; i < n; i++) {\n                sum += sc.nextInt();\n            }\n            System.out.println(sum);\n        }\n    }\n}"
}
```

**Validation:**
- `problemId`: required, must exist
- `language`: required, must be JAVA, CPP, or PYTHON
- `sourceCode`: required, max 256 KB

**Response (200):**
```json
{
  "success": true,
  "data": {
    "status": "ACCEPTED",
    "testResults": [
      {
        "testCaseId": 1,
        "status": "ACCEPTED",
        "actualOutput": "6\n10",
        "expectedOutput": "6\n10",
        "runtimeMs": 150,
        "memoryUsedKb": 12800
      }
    ],
    "totalRuntimeMs": 150,
    "totalMemoryUsedKb": 12800
  },
  "error": null
}
```

**Note:** This endpoint does NOT create a submission record. It only runs code against sample tests and returns results.

**Errors:**
- `400 BAD_REQUEST` — Validation failed
- `404 NOT_FOUND` — Problem not found

---

### 4.2 Submit Code

Submit student code for judging against the **full test suite** (including hidden tests).

**Endpoint:** `POST /api/submissions`
**Auth:** Yes

**Request:**
```json
{
  "problemId": 1,
  "language": "JAVA",
  "sourceCode": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int t = sc.nextInt();\n        while (t-- > 0) {\n            int n = sc.nextInt();\n            int sum = 0;\n            for (int i = 0; i < n; i++) {\n                sum += sc.nextInt();\n            }\n            System.out.println(sum);\n        }\n    }\n}"
}
```

**Validation:**
- `problemId`: required, must exist
- `language`: required, must be JAVA, CPP, or PYTHON
- `sourceCode`: required, max 256 KB

**Response (202):**
```json
{
  "success": true,
  "data": {
    "id": 42,
    "status": "PENDING",
    "submittedAt": "2026-10-02T10:30:00Z"
  },
  "error": null
}
```

**Note:** The submission is processed asynchronously. The client should poll for results using `GET /api/submissions/{id}`.

**Errors:**
- `400 BAD_REQUEST` — Validation failed
- `404 NOT_FOUND` — Problem not found
- `413 PAYLOAD_TOO_LARGE` — Source code exceeds size limit

---

## 5. Submission Endpoints

### 5.1 Get Submission

Get the result of a specific submission.

**Endpoint:** `GET /api/submissions/{id}`
**Auth:** Yes

**Path Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | long | Submission ID |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": 42,
    "problem": {
      "id": 1,
      "slug": "power-cut",
      "title": "Power Cut"
    },
    "language": "JAVA",
    "status": "ACCEPTED",
    "runtimeMs": 150,
    "memoryUsedKb": 12800,
    "submittedAt": "2026-10-02T10:30:00Z",
    "judgedAt": "2026-10-02T10:30:02Z",
    "sourceCode": "import java.util.Scanner;\n\npublic class Main {\n    ...",
    "testResults": [
      {
        "testCaseId": 1,
        "status": "ACCEPTED",
        "actualOutput": "6\n10",
        "runtimeMs": 150,
        "memoryUsedKb": 12800
      }
    ]
  },
  "error": null
}
```

**Note:** `testResults` only includes sample test cases. Hidden test case results are not exposed.

**Errors:**
- `404 NOT_FOUND` — Submission not found
- `403 FORBIDDEN` — Submission belongs to another user

---

### 5.2 List User Submissions

Get the current user's submission history.

**Endpoint:** `GET /api/submissions`
**Auth:** Yes

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `problemId` | long | No | Filter by problem |
| `status` | string | No | Filter by status |
| `language` | string | No | Filter by language |
| `page` | integer | No | Page number (default: 0) |
| `size` | integer | No | Page size (default: 20, max: 100) |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 42,
        "problem": {
          "id": 1,
          "slug": "power-cut",
          "title": "Power Cut"
        },
        "language": "JAVA",
        "status": "ACCEPTED",
        "runtimeMs": 150,
        "memoryUsedKb": 12800,
        "submittedAt": "2026-10-02T10:30:00Z"
      },
      {
        "id": 41,
        "problem": {
          "id": 1,
          "slug": "power-cut",
          "title": "Power Cut"
        },
        "language": "JAVA",
        "status": "WRONG_ANSWER",
        "runtimeMs": 145,
        "memoryUsedKb": 12500,
        "submittedAt": "2026-10-02T10:15:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "error": null
}
```

---

## 6. Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `UNAUTHORIZED` | 401 | Missing or invalid authentication |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource conflict (duplicate) |
| `PAYLOAD_TOO_LARGE` | 413 | Request body too large |
| `INTERNAL_ERROR` | 500 | Internal server error |

---

## 7. Security Considerations

1. **Hidden test cases** are NEVER returned by any API endpoint
2. **Source code** is only returned for the authenticated user's own submissions
3. **All endpoints** except `/api/auth/**` require JWT authentication
4. **Input validation** is performed on all request bodies
5. **Rate limiting** should be added to prevent abuse (future enhancement)

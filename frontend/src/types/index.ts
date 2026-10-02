export type Language = 'JAVA' | 'CPP' | 'PYTHON';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type SubmissionStatus =
  | 'PENDING'
  | 'JUDGING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILATION_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'INTERNAL_ERROR';

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown> | null;
  } | null;
}

export interface User {
  id: number;
  email: string;
  username: string;
  fullName: string;
  role: string;
}

export interface AuthData {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  weekLabel: string;
  timeLimitMs: number;
  memoryLimitMb: number;
}

export interface ProblemListData {
  content: ProblemSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SampleTestCase {
  id: number;
  inputData: string;
  expectedOutput: string;
}

export interface ProblemDetail extends ProblemSummary {
  statement: string;
  inputFormat: string;
  outputFormat: string;
  constraints: string | null;
  sampleTestCases: SampleTestCase[];
}

export interface TestCaseResult {
  testCaseId: number;
  status: SubmissionStatus;
  inputData?: string | null;
  actualOutput: string | null;
  expectedOutput?: string | null;
  runtimeMs: number | null;
  memoryUsedKb: number | null;
}

export interface CustomRunPayload {
  problemId: number;
  language: Language;
  sourceCode: string;
  stdin: string;
}

export interface CustomRunResult {
  status: SubmissionStatus;
  output: string | null;
  error: string | null;
  exitCode: number | null;
  runtimeMs: number | null;
  memoryUsedKb: number | null;
}

export interface RunResult {
  status: SubmissionStatus;
  testResults: TestCaseResult[];
  totalRuntimeMs: number | null;
  totalMemoryUsedKb: number | null;
  compilationError?: string | null;
}

export interface SubmissionRef {
  id: number;
  status: SubmissionStatus;
  submittedAt: string;
}

export interface SubmissionDetail extends SubmissionRef {
  problem: ProblemSummary;
  language: Language;
  runtimeMs: number | null;
  memoryUsedKb: number | null;
  judgedAt: string | null;
  sourceCode: string;
  testResults: TestCaseResult[];
}

export interface SubmissionSummary {
  id: number;
  problem: Pick<ProblemSummary, 'id' | 'slug' | 'title'>;
  language: Language;
  status: SubmissionStatus;
  runtimeMs: number | null;
  memoryUsedKb: number | null;
  submittedAt: string;
}

export interface AdminProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  weekLabel: string;
  testCaseCount: number;
  sampleCount: number;
}

export interface ImportedProblemSummary {
  slug: string;
  title: string;
  status: 'CREATED' | 'SKIPPED';
  sampleCount: number;
  hiddenCount: number;
  message: string;
}

export interface ImportPackResponse {
  problems: ImportedProblemSummary[];
  totalProblems: number;
  totalSamples: number;
  totalHidden: number;
}

export interface ImportPackPayload {
  weekLabel: string;
  defaultTimeLimitMs: number;
  defaultMemoryLimitMb: number;
  defaultDifficulty: Difficulty;
}

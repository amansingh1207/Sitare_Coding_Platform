import { apiFetch, buildQuery } from './client';
import type {
  CustomRunPayload,
  CustomRunResult,
  Language,
  RunResult,
  SubmissionDetail,
  SubmissionRef,
  SubmissionSummary,
} from '../types';

export interface CodePayload {
  problemId: number;
  language: Language;
  sourceCode: string;
}

export interface SubmissionFilters {
  problemId?: number;
  status?: string;
  language?: string;
  page?: number;
  size?: number;
}

export interface SubmissionListData {
  content: SubmissionSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const POLL_TIMEOUT_MS = 120000;

/**
 * Poll spacing grows while a verdict is pending: fast feedback in the
 * first seconds, gentle on the server during long judge queues.
 * Exported for tests; the poller caps at the last entry.
 */
export const POLL_DELAYS_MS = [1500, 1500, 3000, 3000, 5000];

export function pollDelayForAttempt(attempt: number): number {
  return POLL_DELAYS_MS[Math.min(Math.max(attempt, 0), POLL_DELAYS_MS.length - 1)];
}

const TERMINAL_STATUSES = new Set([
  'ACCEPTED',
  'WRONG_ANSWER',
  'COMPILATION_ERROR',
  'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'INTERNAL_ERROR',
]);

export const submissionsApi = {
  async runCode(payload: CodePayload): Promise<RunResult> {
    return apiFetch<RunResult>('/submissions/run', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async runCustomInput(payload: CustomRunPayload): Promise<CustomRunResult> {
    return apiFetch<CustomRunResult>('/submissions/run-custom', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async submit(payload: CodePayload): Promise<SubmissionRef> {
    return apiFetch<SubmissionRef>('/submissions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async get(id: number): Promise<SubmissionDetail> {
    return apiFetch<SubmissionDetail>(`/submissions/${id}`);
  },

  async solvedIds(): Promise<number[]> {
    return apiFetch<number[]>('/submissions/solved-ids');
  },

  async list(filters: SubmissionFilters = {}): Promise<SubmissionListData> {
    return apiFetch<SubmissionListData>(
      `/submissions${buildQuery(filters as Record<string, string | number | undefined>)}`,
    );
  },

  async pollUntilJudged(
    id: number,
    onProgress?: (submission: SubmissionDetail) => void,
  ): Promise<SubmissionDetail> {
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    let attempt = 0;
    for (;;) {
      const submission = await submissionsApi.get(id);
      if (TERMINAL_STATUSES.has(submission.status)) {
        return submission;
      }
      if (Date.now() > deadline) {
        throw new Error('Timed out waiting for judging result');
      }
      onProgress?.(submission);
      await new Promise((resolve) => setTimeout(resolve, pollDelayForAttempt(attempt)));
      attempt += 1;
    }
  },
};

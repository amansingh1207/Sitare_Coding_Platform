import type { SubmissionStatus, TestCaseResult } from '../types';

interface VerdictBannerProps {
  status: SubmissionStatus;
  results: TestCaseResult[];
  runtimeMs?: number | null;
  /** Optional heading shown above the verdict, e.g. "Submission #42". */
  title?: string;
  /** Shown under the verdict when the run never executed (e.g. CE message). */
  detail?: string | null;
}

const VERDICT_LABELS: Record<SubmissionStatus, string> = {
  PENDING: 'Pending',
  JUDGING: 'Judging',
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  COMPILATION_ERROR: 'Compilation Error',
  RUNTIME_ERROR: 'Runtime Error',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  INTERNAL_ERROR: 'Internal Error',
};

export function VerdictBanner({ status, results, runtimeMs, title, detail }: VerdictBannerProps) {
  const passed = results.filter((r) => r.status === 'ACCEPTED').length;
  const total = results.length;

  return (
    <div className={`verdict verdict--${status.toLowerCase()}`}>
      {title && <p className="verdict__title">{title}</p>}
      <p className="verdict__status">{VERDICT_LABELS[status] ?? status}</p>
      <p className="verdict__meta">
        {total > 0 ? (
          <>
            {passed} / {total} testcases passed
          </>
        ) : (
          <>No testcases evaluated</>
        )}
        {runtimeMs !== null && runtimeMs !== undefined && <> · {runtimeMs} ms</>}
      </p>
      {detail && <pre className="verdict__detail">{detail}</pre>}
    </div>
  );
}

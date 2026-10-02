import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { submissionsApi } from '../api/submissions';
import { ApiError } from '../api/client';
import { TestCaseResults } from '../components/TestCaseResults';
import { CodeViewer } from '../components/CodeViewer';
import { formatMemory, formatRuntime, formatTimestamp, languageLabel, statusLabel } from '../utils/format';
import type { SubmissionDetail } from '../types';

export function SubmissionDetailPage() {
  const { id = '' } = useParams();
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSubmission(await submissionsApi.get(Number(id)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load submission');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <p>Loading submission...</p>;
  }
  if (error || !submission) {
    return (
      <div>
        <p className="error">{error ?? 'Submission not found.'}</p>
        <Link to="/submissions">Back to submissions</Link>
      </div>
    );
  }

  return (
    <div className="submission-detail">
      <h2>
        Submission #{submission.id} — {submission.problem.title}
      </h2>

      <dl className="submission-meta">
        <dt>Status</dt>
        <dd>
          <span className={`status-badge status-badge--${submission.status.toLowerCase()}`}>
            {statusLabel(submission.status)}
          </span>
        </dd>
        <dt>Language</dt>
        <dd>{languageLabel(submission.language)}</dd>
        <dt>Runtime</dt>
        <dd>{formatRuntime(submission.runtimeMs)}</dd>
        <dt>Memory</dt>
        <dd>{formatMemory(submission.memoryUsedKb)}</dd>
        <dt>Submitted</dt>
        <dd>{formatTimestamp(submission.submittedAt)}</dd>
        {submission.judgedAt && (
          <>
            <dt>Judged</dt>
            <dd>{formatTimestamp(submission.judgedAt)}</dd>
          </>
        )}
      </dl>

      <p>
        <Link to={`/problems/${submission.problem.slug}`}>Open problem</Link>
      </p>

      <h3>Source code</h3>
      <CodeViewer language={submission.language} code={submission.sourceCode} />

      {/* Only sample test results are returned by the API; hidden cases are never sent. */}
      <TestCaseResults results={submission.testResults} title="Sample test results" />
    </div>
  );
}

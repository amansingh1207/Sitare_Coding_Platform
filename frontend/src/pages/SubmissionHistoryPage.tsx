import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { submissionsApi } from '../api/submissions';
import { ApiError } from '../api/client';
import { formatMemory, formatRuntime, formatTimestamp, languageLabel, statusLabel } from '../utils/format';
import type { SubmissionSummary } from '../types';

const PAGE_SIZE = 20;

export function SubmissionHistoryPage() {
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (targetPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await submissionsApi.list({ page: targetPage, size: PAGE_SIZE });
      setSubmissions(data.content);
      setTotalPages(data.totalPages);
      setPage(data.page);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load submissions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(0);
  }, [load]);

  return (
    <div className="submission-history">
      <h2>My Submissions</h2>

      {loading && <p>Loading submissions...</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && submissions.length === 0 && (
        <p>No submissions yet. Solve a problem to get started.</p>
      )}

      {submissions.length > 0 && (
        <table className="submission-table">
          <thead>
            <tr>
              <th>Problem</th>
              <th>Language</th>
              <th>Status</th>
              <th>Runtime</th>
              <th>Memory</th>
              <th>Submitted</th>
            </tr>
          </thead>
          <tbody>
            {submissions.map((submission) => (
              <tr key={submission.id}>
                <td>
                  <Link to={`/submissions/${submission.id}`}>
                    {submission.problem.title}
                  </Link>
                </td>
                <td>{languageLabel(submission.language)}</td>
                <td>
                  <span
                    className={`status-badge status-badge--${submission.status.toLowerCase()}`}
                  >
                    {statusLabel(submission.status)}
                  </span>
                </td>
                <td>{formatRuntime(submission.runtimeMs)}</td>
                <td>{formatMemory(submission.memoryUsedKb)}</td>
                <td>{formatTimestamp(submission.submittedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {totalPages > 1 && (
        <div className="pagination">
          <button
            type="button"
            onClick={() => void load(page - 1)}
            disabled={page <= 0 || loading}
          >
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => void load(page + 1)}
            disabled={page >= totalPages - 1 || loading}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

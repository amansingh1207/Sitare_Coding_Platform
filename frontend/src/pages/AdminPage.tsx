import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { adminApi } from '../api/admin';
import { ApiError } from '../api/client';
import type {
  AdminProblemSummary,
  Difficulty,
  ImportedProblemSummary,
  ImportPackPayload,
  PresenceData,
} from '../types';

export function AdminPage() {
  const [problems, setProblems] = useState<AdminProblemSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [presence, setPresence] = useState<PresenceData | null>(null);

  // Problem pack import state
  const [packWeekLabel, setPackWeekLabel] = useState('');
  const [packTimeLimitMs, setPackTimeLimitMs] = useState(1000);
  const [packMemoryLimitMb, setPackMemoryLimitMb] = useState(256);
  const [packDifficulty, setPackDifficulty] = useState<Difficulty>('MEDIUM');
  const [packFile, setPackFile] = useState<File | null>(null);
  const [packImporting, setPackImporting] = useState(false);
  const [packResults, setPackResults] = useState<ImportedProblemSummary[]>([]);

  useEffect(() => {
    loadProblems();
  }, []);

  useEffect(() => {
    let stopped = false;
    const loadPresence = async () => {
      try {
        const data = await adminApi.presence();
        if (!stopped) {
          setPresence(data);
        }
      } catch {
        // Traffic snapshot is best-effort; the rest of the panel keeps working.
      }
    };
    void loadPresence();
    const timer = window.setInterval(loadPresence, 30_000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, []);

  async function loadProblems() {
    try {
      const data = await adminApi.listProblems();
      setProblems(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load problems');
    }
  }

  async function handleImportPack(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setPackResults([]);
    if (!packFile) {
      setError('Please select a problem pack ZIP file');
      return;
    }
    setPackImporting(true);
    try {
      const payload: ImportPackPayload = {
        weekLabel: packWeekLabel,
        defaultTimeLimitMs: packTimeLimitMs,
        defaultMemoryLimitMb: packMemoryLimitMb,
        defaultDifficulty: packDifficulty,
      };
      const result = await adminApi.importPack(payload, packFile);
      setPackResults(result.problems);
      setSuccess(
        `Pack imported: ${result.totalProblems} problem(s), ${result.totalSamples} sample and ${result.totalHidden} hidden test case(s)`,
      );
      setPackFile(null);
      loadProblems();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to import problem pack');
    } finally {
      setPackImporting(false);
    }
  }

  return (
    <div className="admin-page">
      <h2>Admin Panel</h2>

      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}

      <section className="admin-section">
        <h3>Live traffic</h3>
        {presence ? (
          <dl className="submission-meta">
            <dt>Online now</dt>
            <dd data-testid="presence-active">{presence.activeUsers}</dd>
            <dt>Registered users</dt>
            <dd>{presence.registeredUsers}</dd>
            <dt>Judging in flight</dt>
            <dd>{presence.judgingInFlight}</dd>
            <dt>Queue waiting</dt>
            <dd>{presence.pendingQueue}</dd>
            <dt>Signups today</dt>
            <dd>{presence.signupsToday}</dd>
          </dl>
        ) : (
          <p className="help-text">Loading traffic…</p>
        )}
      </section>

      <section className="admin-section">
        <h3>Problems</h3>
        <table className="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Slug</th>
              <th>Title</th>
              <th>Difficulty</th>
              <th>Week</th>
              <th>Test Cases</th>
              <th>Samples</th>
            </tr>
          </thead>
          <tbody>
            {problems.map((p) => (
              <tr key={p.id}>
                <td>{p.id}</td>
                <td>{p.slug}</td>
                <td>{p.title}</td>
                <td>{p.difficulty}</td>
                <td>{p.weekLabel}</td>
                <td>{p.testCaseCount}</td>
                <td>{p.sampleCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="admin-section">
        <h3>Import Problem Pack</h3>
        <p className="help-text">
          Upload a professor&apos;s contest ZIP. Each problem folder must contain a{' '}
          <code>statement.html</code> and a <code>tests/sample</code> + <code>tests/secret</code>{' '}
          folder with <code>.in</code>/<code>.ans</code> pairs. Problems, sample tests and hidden
          tests are all created automatically. Title, difficulty and time limit are read from each
          statement; the fields below are used as fallbacks and for the week label.
        </p>
        <form onSubmit={handleImportPack}>
          <label>
            Week Label
            <input
              value={packWeekLabel}
              onChange={(e) => setPackWeekLabel(e.target.value)}
              required
              maxLength={50}
              placeholder="e.g. Week 5"
            />
          </label>
          <label>
            Default Difficulty (fallback)
            <select
              value={packDifficulty}
              onChange={(e) => setPackDifficulty(e.target.value as Difficulty)}
            >
              <option value="EASY">EASY</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HARD">HARD</option>
            </select>
          </label>
          <label>
            Default Time Limit (ms)
            <input
              type="number"
              value={packTimeLimitMs}
              onChange={(e) => setPackTimeLimitMs(Number(e.target.value))}
              required
              min={100}
            />
          </label>
          <label>
            Default Memory Limit (MB)
            <input
              type="number"
              value={packMemoryLimitMb}
              onChange={(e) => setPackMemoryLimitMb(Number(e.target.value))}
              required
              min={16}
            />
          </label>
          <label>
            Pack ZIP File
            <input
              type="file"
              accept=".zip"
              onChange={(e) => setPackFile(e.target.files?.[0] ?? null)}
              required
            />
          </label>
          <button type="submit" disabled={packImporting}>
            {packImporting ? 'Importing pack...' : 'Import Problem Pack'}
          </button>
        </form>
        {packResults.length > 0 && (
          <table className="admin-table">
            <thead>
              <tr>
                <th>Slug</th>
                <th>Title</th>
                <th>Status</th>
                <th>Samples</th>
                <th>Hidden</th>
                <th>Message</th>
              </tr>
            </thead>
            <tbody>
              {packResults.map((r) => (
                <tr key={r.slug}>
                  <td>{r.slug}</td>
                  <td>{r.title}</td>
                  <td>{r.status}</td>
                  <td>{r.sampleCount}</td>
                  <td>{r.hiddenCount}</td>
                  <td>{r.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

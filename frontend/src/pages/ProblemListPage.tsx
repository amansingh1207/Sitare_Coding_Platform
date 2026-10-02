import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { problemsApi } from '../api/problems';
import { submissionsApi } from '../api/submissions';
import { ApiError } from '../api/client';
import type { Difficulty, ProblemSummary } from '../types';

interface ProgressStats {
  total: number;
  solved: number;
  easy: { total: number; solved: number };
  medium: { total: number; solved: number };
  hard: { total: number; solved: number };
}

function emptyStats(): ProgressStats {
  const group = { total: 0, solved: 0 };
  return { total: 0, solved: 0, easy: { ...group }, medium: { ...group }, hard: { ...group } };
}

function buildStats(problems: ProblemSummary[], solvedIds: Set<number>): ProgressStats {
  const stats = emptyStats();
  stats.total = problems.length;
  for (const problem of problems) {
    const solved = solvedIds.has(problem.id);
    if (solved) {
      stats.solved += 1;
    }
    const group =
      problem.difficulty === 'EASY'
        ? stats.easy
        : problem.difficulty === 'MEDIUM'
          ? stats.medium
          : stats.hard;
    group.total += 1;
    if (solved) {
      group.solved += 1;
    }
  }
  return stats;
}

function difficultyLabel(difficulty: Difficulty): string {
  return difficulty.charAt(0) + difficulty.slice(1).toLowerCase();
}

function ProgressRing({ solved, total }: { solved: number; total: number }) {
  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  const fraction = total === 0 ? 0 : solved / total;
  return (
    <div className="progress-ring">
      <svg width="90" height="90" viewBox="0 0 90 90" role="img" aria-label={`${solved} of ${total} solved`}>
        <circle cx="45" cy="45" r={radius} fill="none" stroke="#e5e5e5" strokeWidth="8" />
        <circle
          cx="45"
          cy="45"
          r={radius}
          fill="none"
          stroke="#2cbb5d"
          strokeWidth="8"
          strokeLinecap="round"
          strokeDasharray={`${circumference * fraction} ${circumference}`}
          transform="rotate(-90 45 45)"
        />
      </svg>
      <div className="progress-ring__text">
        <strong>
          {solved}/{total}
        </strong>
        <span>Solved</span>
      </div>
    </div>
  );
}

function DifficultyBar({ label, solved, total, level }: { label: string; solved: number; total: number; level: Difficulty }) {
  const fraction = total === 0 ? 0 : (solved / total) * 100;
  return (
    <div className="progress-row">
      <span className={`diff diff--${level}`}>{label}</span>
      <span className="progress-row__count">
        {solved}/{total}
      </span>
      <div className="progress-bar">
        <div className={`progress-bar__fill progress-bar__fill--${level}`} style={{ width: `${fraction}%` }} />
      </div>
    </div>
  );
}

export function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [solvedIds, setSolvedIds] = useState<Set<number>>(new Set());
  const [stats, setStats] = useState<ProgressStats>(emptyStats());
  const [search, setSearch] = useState('');
  const [week, setWeek] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(filters: { search?: string; week?: string; difficulty?: string }) {
    setLoading(true);
    setError(null);
    try {
      const [data, solved] = await Promise.all([
        problemsApi.list(filters),
        submissionsApi.solvedIds(),
      ]);
      setProblems(data.content);
      const solvedSet = new Set(solved);
      setSolvedIds(solvedSet);
      // Dashboard stats always cover the whole catalogue, not the filter.
      const all = await problemsApi.list({ size: 100 });
      setStats(buildStats(all.content, solvedSet));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load problems');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load({});
  }, []);

  function handleSearch(event: FormEvent) {
    event.preventDefault();
    void load({
      search: search || undefined,
      week: week || undefined,
      difficulty: difficulty || undefined,
    });
  }

  return (
    <div className="problem-list-page">
      <section className="dashboard" aria-label="Progress dashboard">
        <ProgressRing solved={stats.solved} total={stats.total} />
        <div className="dashboard__breakdown">
          <DifficultyBar label="Easy" solved={stats.easy.solved} total={stats.easy.total} level="EASY" />
          <DifficultyBar label="Med." solved={stats.medium.solved} total={stats.medium.total} level="MEDIUM" />
          <DifficultyBar label="Hard" solved={stats.hard.solved} total={stats.hard.total} level="HARD" />
        </div>
      </section>

      <h2>Problems</h2>
      <form className="filters" onSubmit={handleSearch}>
        <input
          type="text"
          placeholder="Search by title"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <input
          type="text"
          placeholder="Week (e.g. Week 1)"
          value={week}
          onChange={(e) => setWeek(e.target.value)}
        />
        <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
          <option value="">All difficulties</option>
          <option value="EASY">Easy</option>
          <option value="MEDIUM">Medium</option>
          <option value="HARD">Hard</option>
        </select>
        <button type="submit">Search</button>
      </form>
      {loading && <p>Loading problems...</p>}
      {error && <p className="error">{error}</p>}
      {!loading && !error && problems.length === 0 && <p>No problems found.</p>}
      {!loading && !error && problems.length > 0 && (
        <table className="problem-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>#</th>
              <th>Title</th>
              <th>Difficulty</th>
            </tr>
          </thead>
          <tbody>
            {problems.map((problem, index) => (
              <tr key={problem.id}>
                <td className="problem-table__status">
                  {solvedIds.has(problem.id) && (
                    <span className="solved-check" title="Solved" aria-label={`Solved: ${problem.title}`}>
                      ✓
                    </span>
                  )}
                </td>
                <td className="problem-table__num">{index + 1}</td>
                <td className="problem-table__title">
                  <Link to={`/problems/${problem.slug}`}>{problem.title}</Link>
                </td>
                <td>
                  <span className={`diff diff--${problem.difficulty}`}>
                    {difficultyLabel(problem.difficulty)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

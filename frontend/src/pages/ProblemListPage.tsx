import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { problemsApi } from '../api/problems';
import { ApiError } from '../api/client';
import type { ProblemSummary } from '../types';

export function ProblemListPage() {
  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [search, setSearch] = useState('');
  const [week, setWeek] = useState('');
  const [difficulty, setDifficulty] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(filters: { search?: string; week?: string; difficulty?: string }) {
    setLoading(true);
    setError(null);
    try {
      const data = await problemsApi.list(filters);
      setProblems(data.content);
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
              <th>#</th>
              <th>Title</th>
              <th>Difficulty</th>
            </tr>
          </thead>
          <tbody>
            {problems.map((problem, index) => (
              <tr key={problem.id}>
                <td className="problem-table__num">{index + 1}</td>
                <td className="problem-table__title">
                  <Link to={`/problems/${problem.slug}`}>{problem.title}</Link>
                </td>
                <td>
                  <span className={`diff diff--${problem.difficulty}`}>
                    {problem.difficulty.charAt(0) + problem.difficulty.slice(1).toLowerCase()}
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

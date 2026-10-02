// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { ProblemDetailPage } from './ProblemDetailPage';
import { fail, ok, renderWithRouter } from '../test/render';
import type { ProblemDetail, RunResult, SubmissionDetail } from '../types';

const mockFetch = vi.fn();

vi.mock('../components/CodeEditor', () => ({
  // CodeMirror needs layout APIs jsdom does not implement, and the editor's own
  // behaviour is not what these tests are about.
  CodeEditor: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="code" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}));

const PROBLEM: ProblemDetail = {
  id: 3,
  slug: 'sum-two',
  title: 'Sum of Two Numbers',
  statement: 'Read two integers and print their sum.',
  inputFormat: 'Two space-separated integers.',
  outputFormat: 'One integer.',
  constraints: null,
  difficulty: 'EASY',
  weekLabel: 'Week 1',
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  sampleTestCases: [{ id: 1, inputData: '3 4', expectedOutput: '7' }],
};

const RUN_ACCEPTED: RunResult = {
  status: 'ACCEPTED',
  testResults: [
    {
      testCaseId: 1,
      status: 'ACCEPTED',
      actualOutput: '7',
      expectedOutput: '7',
      runtimeMs: 106,
      memoryUsedKb: 140612,
    },
  ],
  totalRuntimeMs: 106,
  totalMemoryUsedKb: 140612,
};

const SUBMISSION_ACCEPTED: SubmissionDetail = {
  id: 42,
  problem: {
    id: 3,
    slug: 'sum-two',
    title: 'Sum of Two Numbers',
    difficulty: 'EASY',
    weekLabel: 'Week 1',
    timeLimitMs: 2000,
    memoryLimitMb: 256,
  },
  language: 'JAVA',
  status: 'ACCEPTED',
  runtimeMs: 84,
  memoryUsedKb: 13092,
  submittedAt: '2026-10-02T10:30:00Z',
  judgedAt: '2026-10-02T10:30:01Z',
  sourceCode: 'public class Main {}',
  testResults: [
    {
      testCaseId: 1,
      status: 'ACCEPTED',
      actualOutput: '7',
      expectedOutput: '7',
      runtimeMs: 84,
      memoryUsedKb: 13092,
    },
  ],
};

/** Returns the URL and parsed body of each fetch call, in order. */
function calls(): Array<{ url: string; body: Record<string, unknown> | null }> {
  return mockFetch.mock.calls.map(([url, init]) => ({
    url: String(url),
    body: init?.body ? JSON.parse(String(init.body)) : null,
  }));
}

async function renderPage() {
  renderWithRouter(<ProblemDetailPage />, { route: '/problems/sum-two', path: '/problems/:slug' });
  await screen.findByRole('heading', { name: 'Sum of Two Numbers' });
}

describe('ProblemDetailPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('loads the problem from its slug', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    expect(calls()[0].url).toContain('/problems/sum-two');
    expect(screen.getByText('Read two integers and print their sum.')).toBeTruthy();
    expect(screen.getByText('3 4')).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
  });

  it('surfaces a load failure', async () => {
    mockFetch.mockResolvedValueOnce(fail('NOT_FOUND', 'Problem not found', 404));
    renderWithRouter(<ProblemDetailPage />, {
      route: '/problems/missing',
      path: '/problems/:slug',
    });

    expect(await screen.findByText('Problem not found')).toBeTruthy();
  });

  // Regression guard: this endpoint was specified and wired up but never
  // implemented, so every click of this button 404'd for an entire phase.
  it('Run Code posts to /submissions/run, not /submissions', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(ok(RUN_ACCEPTED));
    fireEvent.click(screen.getByRole('button', { name: 'Run Code' }));

    await screen.findByText('Run result:');
    const runCall = calls().find((c) => c.url.includes('/submissions/run'));
    expect(runCall).toBeDefined();
    expect(calls().some((c) => c.url.endsWith('/submissions') && c.body !== null)).toBe(false);

    expect(runCall?.body).toEqual({
      problemId: 3,
      language: 'JAVA',
      sourceCode: expect.any(String),
    });
    // Shown once as the run verdict and once as the per-test status.
    expect(screen.getAllByText('Passed').length).toBeGreaterThan(0);
    expect(screen.getByText('106 ms')).toBeTruthy();
  });

  it('sends only the code, never the practice timer state', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(ok(RUN_ACCEPTED));
    fireEvent.click(screen.getByRole('button', { name: 'Run Code' }));
    await screen.findByText('Run result:');

    const runCall = calls().find((c) => c.url.includes('/submissions/run'));
    expect(Object.keys(runCall?.body ?? {}).sort()).toEqual([
      'language',
      'problemId',
      'sourceCode',
    ]);
  });

  it('Submit Code posts, then reads the judged submission back', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(ok({ id: 42, status: 'ACCEPTED', submittedAt: '2026-10-02T10:30:00Z' }));
    mockFetch.mockResolvedValueOnce(ok(SUBMISSION_ACCEPTED));

    fireEvent.click(screen.getByRole('button', { name: 'Submit Code' }));

    await waitFor(() => expect(screen.getByText(/Submission #42/)).toBeTruthy());
    const urls = calls().map((c) => c.url);
    expect(urls.some((u) => u.endsWith('/submissions') && !u.includes('/run'))).toBe(true);
    expect(urls.some((u) => u.includes('/submissions/42'))).toBe(true);
    expect(screen.getByText('Visible test results')).toBeTruthy();
    // Rendered as the submission summary and again as the per-test metric.
    expect(screen.getAllByText(/84 ms/).length).toBeGreaterThan(0);
  });

  it('reports a failed run instead of silently doing nothing', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(fail('NOT_FOUND', 'Run endpoint is missing', 404));
    fireEvent.click(screen.getByRole('button', { name: 'Run Code' }));

    expect(await screen.findByText('Run endpoint is missing')).toBeTruthy();
    expect(screen.queryByText('Run result:')).toBeNull();
  });

  it('shows compiler diagnostics returned by the judge', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(
      ok({
        status: 'COMPILATION_ERROR',
        testResults: [
          {
            testCaseId: 1,
            status: 'COMPILATION_ERROR',
            actualOutput: "Main.java:1: error: ';' expected",
            expectedOutput: '7',
            runtimeMs: 0,
            memoryUsedKb: 0,
          },
        ],
        totalRuntimeMs: 0,
        totalMemoryUsedKb: 0,
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Run Code' }));

    expect(await screen.findByText('Run result:')).toBeTruthy();
    expect(screen.getAllByText('Compilation Error').length).toBeGreaterThan(0);
    expect(screen.getByText("Main.java:1: error: ';' expected")).toBeTruthy();
  });

  it('switching language clears stale results without re-running', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    mockFetch.mockResolvedValueOnce(ok(RUN_ACCEPTED));
    fireEvent.click(screen.getByRole('button', { name: 'Run Code' }));
    await screen.findByText('Run result:');

    const callCountBefore = mockFetch.mock.calls.length;
    fireEvent.change(screen.getByLabelText('Language'), { target: { value: 'PYTHON' } });

    expect(screen.queryByText('Run result:')).toBeNull();
    // Changing language only swaps the starter code; it must not hit the judge.
    expect(mockFetch.mock.calls.length).toBe(callCountBefore);
  });

  it('offers all three supported languages', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    const select = screen.getByLabelText('Language') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.value)).toEqual(['JAVA', 'CPP', 'PYTHON']);
  });

  it('renders the practice timer alongside the editor', async () => {
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    expect(screen.getByText('Practice time')).toBeTruthy();
    expect(screen.getByTestId('practice-timer-value').textContent).toBe('00:00:00');
  });

  it('loads an uploaded code file into the editor', async () => {
    localStorage.clear();
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    const file = new File(['public class Main { uploaded }'], 'Main.java', {
      type: 'text/plain',
    });
    fireEvent.change(screen.getByLabelText('Upload code file'), {
      target: { files: [file] },
    });

    await waitFor(() =>
      expect(
        (screen.getByLabelText('code') as HTMLTextAreaElement).value,
      ).toContain('uploaded'),
    );
    localStorage.clear();
  });

  it('restores starter code on Reset', async () => {
    localStorage.clear();
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    fireEvent.change(screen.getByLabelText('code'), {
      target: { value: 'my custom attempt' },
    });
    fireEvent.click(screen.getByTitle('Restore starter code'));

    expect((screen.getByLabelText('code') as HTMLTextAreaElement).value).toContain('class Main');
    localStorage.clear();
  });

  it('rejects code files larger than the judge limit', async () => {
    localStorage.clear();
    mockFetch.mockResolvedValueOnce(ok(PROBLEM));
    await renderPage();

    const big = new File(['x'.repeat(300 * 1024)], 'big.java', { type: 'text/plain' });
    fireEvent.change(screen.getByLabelText('Upload code file'), {
      target: { files: [big] },
    });

    expect(await screen.findByText(/too large/)).toBeTruthy();
    localStorage.clear();
  });
});
import { afterEach, describe, expect, it, vi } from 'vitest';
import { submissionsApi } from './submissions';

function mockFetchSequence(bodies: unknown[]) {
  const mock = vi.fn();
  for (const body of bodies) {
    mock.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ success: true, data: body, error: null }),
    });
  }
  vi.stubGlobal('fetch', mock);
  return mock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('submissionsApi', () => {
  it('posts run requests to /submissions/run', async () => {
    const mock = mockFetchSequence([
      { status: 'ACCEPTED', testResults: [], totalRuntimeMs: 10, totalMemoryUsedKb: 100 },
    ]);
    const payload = { problemId: 1, language: 'JAVA' as const, sourceCode: 'code' };
    await submissionsApi.runCode(payload);
    expect(mock).toHaveBeenCalledWith(
      expect.stringContaining('/submissions/run'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify(payload) }),
    );
  });

  it('posts submit requests to /submissions', async () => {
    const mock = mockFetchSequence([{ id: 7, status: 'PENDING', submittedAt: 'now' }]);
    await submissionsApi.submit({ problemId: 2, language: 'CPP', sourceCode: 'code' });
    expect(mock).toHaveBeenCalledWith(
      expect.stringContaining('/submissions'),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('polls until the submission reaches a terminal status', async () => {
    vi.useFakeTimers();
    const mock = mockFetchSequence([
      { id: 7, status: 'JUDGING' },
      { id: 7, status: 'JUDGING' },
      { id: 7, status: 'ACCEPTED', testResults: [] },
    ]);
    const promise = submissionsApi.pollUntilJudged(7);
    await vi.runAllTimersAsync();
    const result = await promise;
    expect(result.status).toBe('ACCEPTED');
    expect(mock).toHaveBeenCalledTimes(3);
  });
});

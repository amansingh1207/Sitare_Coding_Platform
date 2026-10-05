// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { SubmissionDetailPage } from './SubmissionDetailPage';
import { ok, renderWithRouter } from '../test/render';

const mockFetch = vi.fn();

const PENDING_QUEUED = {
  id: 9,
  status: 'PENDING',
  submittedAt: '2026-10-06T10:00:00Z',
  judgedAt: null,
  problem: { id: 3, slug: 'sum-two', title: 'Sum of Two Numbers' },
  language: 'JAVA',
  runtimeMs: null,
  memoryUsedKb: null,
  sourceCode: 'public class Main {}',
  testResults: [],
  queuePosition: 5,
};

describe('SubmissionDetailPage queue position', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function renderDetail() {
    renderWithRouter(<SubmissionDetailPage />, {
      route: '/submissions/9',
      path: '/submissions/:id',
    });
  }

  it('shows the queue position while pending', async () => {
    mockFetch.mockResolvedValueOnce(ok(PENDING_QUEUED));
    renderDetail();

    expect(await screen.findByTestId('queue-position')).toBeTruthy();
    expect(screen.getByText('#5')).toBeTruthy();
  });

  it('hides the queue position once judging starts', async () => {
    mockFetch.mockResolvedValueOnce(ok({ ...PENDING_QUEUED, status: 'JUDGING' }));
    renderDetail();

    await screen.findByText('Submission #9 — Sum of Two Numbers');
    expect(screen.queryByTestId('queue-position')).toBeNull();
  });
});

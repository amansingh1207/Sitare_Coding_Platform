// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { SubmissionHistoryPage } from './SubmissionHistoryPage';
import { fail, ok, renderWithRouter } from '../test/render';
import type { SubmissionSummary } from '../types';

const mockFetch = vi.fn();

function summary(overrides: Partial<SubmissionSummary> = {}): SubmissionSummary {
  return {
    id: 42,
    problem: { id: 3, slug: 'sum-two', title: 'Sum of Two Numbers' },
    language: 'JAVA',
    status: 'ACCEPTED',
    runtimeMs: 84,
    memoryUsedKb: 13092,
    submittedAt: '2026-10-02T10:30:00Z',
    ...overrides,
  };
}

function page(content: SubmissionSummary[], totalPages = 1, pageNumber = 0) {
  return ok({
    content,
    page: pageNumber,
    size: 20,
    totalElements: content.length,
    totalPages,
  });
}

describe('SubmissionHistoryPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('asks for the first page on mount', async () => {
    mockFetch.mockResolvedValueOnce(page([summary()]));
    renderWithRouter(<SubmissionHistoryPage />);

    await screen.findByRole('heading', { name: 'My Submissions' });
    expect(String(mockFetch.mock.calls[0][0])).toContain('page=0');
    expect(String(mockFetch.mock.calls[0][0])).toContain('size=20');
  });

  it('renders a row per submission with its metrics', async () => {
    mockFetch.mockResolvedValueOnce(page([summary()]));
    renderWithRouter(<SubmissionHistoryPage />);

    expect(await screen.findByText('Sum of Two Numbers')).toBeTruthy();
    expect(screen.getByText('Java')).toBeTruthy();
    expect(screen.getByText('Accepted')).toBeTruthy();
    expect(screen.getByText('84 ms')).toBeTruthy();
    expect(screen.getByText('12.8 MB')).toBeTruthy();
  });

  it('labels each status in student-facing wording', async () => {
    mockFetch.mockResolvedValueOnce(
      page([
        summary({ id: 1, status: 'WRONG_ANSWER' }),
        summary({ id: 2, status: 'COMPILATION_ERROR' }),
        summary({ id: 3, status: 'TIME_LIMIT_EXCEEDED' }),
        summary({ id: 4, status: 'MEMORY_LIMIT_EXCEEDED' }),
        summary({ id: 5, status: 'RUNTIME_ERROR' }),
      ]),
    );
    renderWithRouter(<SubmissionHistoryPage />);

    expect(await screen.findByText('Wrong Answer')).toBeTruthy();
    expect(screen.getByText('Compilation Error')).toBeTruthy();
    expect(screen.getByText('Time Limit Exceeded')).toBeTruthy();
    expect(screen.getByText('Memory Limit Exceeded')).toBeTruthy();
    expect(screen.getByText('Runtime Error')).toBeTruthy();
  });

  it('invites a first submission when history is empty', async () => {
    mockFetch.mockResolvedValueOnce(page([]));
    renderWithRouter(<SubmissionHistoryPage />);

    expect(await screen.findByText(/No submissions yet/)).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('surfaces a load failure', async () => {
    mockFetch.mockResolvedValueOnce(fail('UNAUTHORIZED', 'Session expired', 401));
    renderWithRouter(<SubmissionHistoryPage />);

    expect(await screen.findByText('Session expired')).toBeTruthy();
  });

  it('links each row to its submission detail', async () => {
    mockFetch.mockResolvedValueOnce(page([summary({ id: 77 })]));
    renderWithRouter(<SubmissionHistoryPage />);

    const link = (await screen.findByText('Sum of Two Numbers')) as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/submissions/77');
  });

  it('hides pagination on a single page', async () => {
    mockFetch.mockResolvedValueOnce(page([summary()], 1, 0));
    renderWithRouter(<SubmissionHistoryPage />);

    await screen.findByText('Sum of Two Numbers');
    expect(screen.queryByRole('button', { name: 'Next' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Previous' })).toBeNull();
  });

  it('paginates and disables the edges', async () => {
    mockFetch.mockResolvedValueOnce(page([summary()], 3, 0));
    renderWithRouter(<SubmissionHistoryPage />);

    const previous = (await screen.findByRole('button', { name: 'Previous' })) as HTMLButtonElement;
    const next = screen.getByRole('button', { name: 'Next' }) as HTMLButtonElement;
    expect(screen.getByText('Page 1 of 3')).toBeTruthy();
    expect(previous.disabled).toBe(true);
    expect(next.disabled).toBe(false);

    mockFetch.mockResolvedValueOnce(page([summary()], 3, 1));
    fireEvent.click(next);

    await waitFor(() => expect(screen.getByText('Page 2 of 3')).toBeTruthy());
    expect(String(mockFetch.mock.calls[1][0])).toContain('page=1');
  });
});
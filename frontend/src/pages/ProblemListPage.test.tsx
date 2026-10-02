// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { ProblemListPage } from './ProblemListPage';
import { ok, renderWithRouter } from '../test/render';
import type { ProblemListData, ProblemSummary } from '../types';

const mockFetch = vi.fn();

function problem(id: number, slug: string, title: string, difficulty: 'EASY' | 'MEDIUM' | 'HARD') {
  return {
    id,
    slug,
    title,
    difficulty,
    weekLabel: 'Week 1',
    timeLimitMs: 2000,
    memoryLimitMb: 256,
  };
}

function page(content: ProblemSummary[]): ProblemListData {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('ProblemListPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  async function renderSolved() {
    const all = [
      problem(1, 'sum-two', 'Sum of Two Numbers', 'EASY'),
      problem(2, 'maze', 'Maze Runner', 'MEDIUM'),
      problem(3, 'tough', 'Tough One', 'HARD'),
    ];
    const full = page(all);
    mockFetch.mockResolvedValueOnce(ok(full));
    mockFetch.mockResolvedValueOnce(ok([1, 3]));
    mockFetch.mockResolvedValueOnce(ok(full));
    renderWithRouter(<ProblemListPage />);
    await screen.findByText('Sum of Two Numbers');
  }

  it('marks solved problems with a check', async () => {
    await renderSolved();

    expect(screen.getByLabelText('Solved: Sum of Two Numbers')).toBeTruthy();
    expect(screen.getByLabelText('Solved: Tough One')).toBeTruthy();
    expect(screen.queryByLabelText('Solved: Maze Runner')).toBeNull();
  });

  it('shows overall and per-difficulty progress on the dashboard', async () => {
    await renderSolved();

    expect(screen.getByLabelText('2 of 3 solved')).toBeTruthy();
    expect(screen.getByText('2/3')).toBeTruthy();
  });
});

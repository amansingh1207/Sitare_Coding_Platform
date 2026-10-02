import { apiFetch, buildQuery } from './client';
import type { ProblemDetail, ProblemListData } from '../types';

export interface ProblemFilters {
  search?: string;
  week?: string;
  difficulty?: string;
  page?: number;
  size?: number;
}

export const problemsApi = {
  async list(filters: ProblemFilters = {}): Promise<ProblemListData> {
    return apiFetch<ProblemListData>(`/problems${buildQuery(filters as Record<string, string | number | undefined>)}`);
  },

  async get(slug: string): Promise<ProblemDetail> {
    return apiFetch<ProblemDetail>(`/problems/${encodeURIComponent(slug)}`);
  },
};

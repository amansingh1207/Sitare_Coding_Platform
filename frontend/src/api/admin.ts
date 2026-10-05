import { apiFetch } from './client';
import type { AdminProblemSummary, ImportPackPayload, ImportPackResponse, PresenceData } from '../types';

export const adminApi = {
  async listProblems(): Promise<AdminProblemSummary[]> {
    return apiFetch<AdminProblemSummary[]>('/admin/problems');
  },

  async presence(): Promise<PresenceData> {
    return apiFetch<PresenceData>('/admin/presence');
  },

  async importPack(payload: ImportPackPayload, file: File): Promise<ImportPackResponse> {
    const formData = new FormData();
    formData.append('weekLabel', payload.weekLabel);
    formData.append('defaultTimeLimitMs', String(payload.defaultTimeLimitMs));
    formData.append('defaultMemoryLimitMb', String(payload.defaultMemoryLimitMb));
    formData.append('defaultDifficulty', payload.defaultDifficulty);
    formData.append('file', file);
    return apiFetch<ImportPackResponse>('/admin/problems/import-pack', {
      method: 'POST',
      body: formData,
    });
  },
};

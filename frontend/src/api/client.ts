import type { ApiResponse } from '../types';

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  return localStorage.getItem('codingjudge_token');
}

export function setToken(token: string): void {
  localStorage.setItem('codingjudge_token', token);
}

export function clearToken(): void {
  localStorage.removeItem('codingjudge_token');
}

async function parseEnvelope<T>(response: Response): Promise<T> {
  let envelope: ApiResponse<T>;
  try {
    envelope = (await response.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError(response.status, 'NETWORK_ERROR', `Request failed with status ${response.status}`);
  }
  if (!response.ok || !envelope.success) {
    throw new ApiError(
      response.status,
      envelope.error?.code ?? 'UNKNOWN_ERROR',
      envelope.error?.message ?? `Request failed with status ${response.status}`,
    );
  }
  return envelope.data as T;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  return parseEnvelope<T>(response);
}

export function buildQuery(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : '';
}

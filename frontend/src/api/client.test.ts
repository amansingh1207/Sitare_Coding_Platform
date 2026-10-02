import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch, buildQuery, clearToken, getToken, setToken } from './client';

function mockFetchOnce(json: unknown, status = 200, ok = true) {
  const response = {
    ok,
    status,
    json: () => Promise.resolve(json),
  } as Response;
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
  return fetch as unknown as ReturnType<typeof vi.fn>;
}

afterEach(() => {
  vi.unstubAllGlobals();
  clearToken();
});

describe('token storage', () => {
  it('stores and clears the token', () => {
    expect(getToken()).toBeNull();
    setToken('abc');
    expect(getToken()).toBe('abc');
    clearToken();
    expect(getToken()).toBeNull();
  });
});

describe('apiFetch', () => {
  it('unwraps the success envelope', async () => {
    mockFetchOnce({ success: true, data: { id: 1 }, error: null });
    await expect(apiFetch('/problems')).resolves.toEqual({ id: 1 });
  });

  it('attaches the bearer token when present', async () => {
    setToken('secret-token');
    const fetchMock = mockFetchOnce({ success: true, data: {}, error: null });
    await apiFetch('/auth/me');
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/auth/me'),
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer secret-token' }),
      }),
    );
  });

  it('throws ApiError with the server code on failure envelope', async () => {
    mockFetchOnce(
      { success: false, data: null, error: { code: 'NOT_FOUND', message: 'Missing' } },
      404,
      false,
    );
    const err = await apiFetch('/problems/nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(404);
    expect((err as ApiError).code).toBe('NOT_FOUND');
  });

  it('throws ApiError when the body is not JSON', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 500, json: () => Promise.reject(new Error('bad')) }),
    );
    await expect(apiFetch('/x')).rejects.toBeInstanceOf(ApiError);
  });
});

describe('buildQuery', () => {
  it('omits empty and undefined values', () => {
    expect(buildQuery({ search: 'a', week: '', difficulty: undefined, page: 0 })).toBe(
      '?search=a&page=0',
    );
  });

  it('returns an empty string when nothing is set', () => {
    expect(buildQuery({})).toBe('');
  });
});

import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from './auth';

describe('authApi.heartbeat', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts to /auth/heartbeat with no body', async () => {
    const mock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ success: true, data: 'heartbeat recorded', error: null }),
    });
    vi.stubGlobal('fetch', mock);

    await authApi.heartbeat();

    expect(mock).toHaveBeenCalledOnce();
    const [url, init] = mock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/auth/heartbeat');
    expect(init.method).toBe('POST');
  });
});

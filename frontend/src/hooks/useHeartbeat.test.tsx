// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthProvider } from '../context/AuthContext';
import { useHeartbeat } from './useHeartbeat';
import { ok } from '../test/render';

const mockFetch = vi.fn();

const USER = {
  id: 1,
  email: 's@uni.edu',
  username: 's',
  fullName: 'S',
  role: 'STUDENT',
};

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

function heartbeatCalls() {
  return mockFetch.mock.calls.filter(([url]) => String(url).includes('/auth/heartbeat'));
}

describe('useHeartbeat', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    mockFetch.mockImplementation(async (url: unknown) =>
      String(url).includes('/auth/me') ? ok(USER) : ok('heartbeat recorded'),
    );
    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      configurable: true,
    });
    localStorage.setItem('codingjudge_token', 'token');
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
    localStorage.clear();
    Object.defineProperty(document, 'visibilityState', {
      value: 'prerender',
      configurable: true,
    });
  });

  it('pings immediately and once a minute while logged in', async () => {
    const { unmount } = renderHook(() => useHeartbeat(), { wrapper });
    await act(async () => undefined);
    expect(heartbeatCalls().length).toBeGreaterThanOrEqual(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(heartbeatCalls().length).toBeGreaterThanOrEqual(3);
    unmount();
  });

  it('stays silent in background tabs', async () => {
    Object.defineProperty(document, 'visibilityState', {
      value: 'hidden',
      configurable: true,
    });
    const { unmount } = renderHook(() => useHeartbeat(), { wrapper });
    await act(async () => undefined);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(120_000);
    });
    expect(heartbeatCalls()).toHaveLength(0);
    unmount();
  });

  it('stays silent when logged out', async () => {
    localStorage.clear();
    const { unmount } = renderHook(() => useHeartbeat(), { wrapper });
    await act(async () => undefined);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(120_000);
    });
    expect(heartbeatCalls()).toHaveLength(0);
    unmount();
  });
});

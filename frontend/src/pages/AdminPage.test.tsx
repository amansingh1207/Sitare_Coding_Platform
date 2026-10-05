// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { AdminPage } from './AdminPage';
import { ok, renderWithRouter } from '../test/render';

const mockFetch = vi.fn();

const PRESENCE = {
  activeUsers: 7,
  registeredUsers: 100,
  judgingInFlight: 3,
  pendingQueue: 5,
  signupsToday: 12,
};

describe('AdminPage live traffic', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    vi.stubGlobal('fetch', mockFetch);
    mockFetch.mockImplementation(async (url: unknown) => {
      if (String(url).includes('/admin/presence')) {
        return ok(PRESENCE);
      }
      return ok([]);
    });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows traffic counts without personal data', async () => {
    renderWithRouter(<AdminPage />, { route: '/admin' });

    expect(await screen.findByText('Live traffic')).toBeTruthy();
    expect(screen.getByTestId('presence-active')).toBeTruthy();
    expect(screen.getByText('100')).toBeTruthy();
    expect(screen.getByText('12')).toBeTruthy();
    const body = document.body.textContent ?? '';
    expect(body).not.toContain('@');
  });
});

// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { LoginPage } from './LoginPage';
import { fail, ok, renderAuthenticated } from '../test/render';

const mockFetch = vi.fn();
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

describe('LoginPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    mockNavigate.mockReset();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  function fill(email: string, password: string) {
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  }

  it('signs in and moves to the problem list', async () => {
    mockFetch.mockResolvedValueOnce(
      ok({
        token: 'jwt-token',
        user: { id: 1, email: 'student@uni.edu', username: 'student', fullName: 'Student' },
      }),
    );
    renderAuthenticated(<LoginPage />);
    fill('student@uni.edu', 'password123');
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/problems'));
    const [url, init] = mockFetch.mock.calls[0];
    expect(String(url)).toContain('/auth/login');
    expect(JSON.parse(String(init.body))).toEqual({
      email: 'student@uni.edu',
      password: 'password123',
    });
  });

  it('stores the token so later requests can authenticate', async () => {
    mockFetch.mockResolvedValueOnce(
      ok({
        token: 'jwt-token',
        user: { id: 1, email: 'student@uni.edu', username: 'student', fullName: 'Student' },
      }),
    );
    renderAuthenticated(<LoginPage />);
    fill('student@uni.edu', 'password123');
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => expect(localStorage.getItem('codingjudge_token')).toBe('jwt-token'));
  });

  it('shows the server message and stays put on bad credentials', async () => {
    mockFetch.mockResolvedValueOnce(fail('UNAUTHORIZED', 'Invalid email or password', 401));
    renderAuthenticated(<LoginPage />);
    fill('student@uni.edu', 'wrong-password');
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    expect(await screen.findByText('Invalid email or password')).toBeTruthy();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(localStorage.getItem('codingjudge_token')).toBeNull();
  });

  it('re-enables the form after a failure so it can be retried', async () => {
    mockFetch.mockResolvedValueOnce(fail('UNAUTHORIZED', 'Invalid email or password', 401));
    renderAuthenticated(<LoginPage />);
    fill('student@uni.edu', 'wrong-password');
    fireEvent.click(screen.getByRole('button', { name: 'Login' }));

    await screen.findByText('Invalid email or password');
    expect((screen.getByRole('button', { name: 'Login' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('requires both fields before submitting', () => {
    renderAuthenticated(<LoginPage />);
    expect((screen.getByLabelText('Email') as HTMLInputElement).required).toBe(true);
    expect((screen.getByLabelText('Password') as HTMLInputElement).required).toBe(true);
  });

  it('links to registration', () => {
    renderAuthenticated(<LoginPage />);
    expect((screen.getByRole('link', { name: 'Register' }) as HTMLAnchorElement).getAttribute('href')).toBe(
      '/register',
    );
  });
});
import { apiFetch, clearToken, setToken } from './client';
import type { AuthData, User } from '../types';

export interface RegisterPayload {
  email: string;
  username: string;
  password: string;
  fullName: string;
}

export const authApi = {
  async register(payload: RegisterPayload): Promise<User> {
    return apiFetch<User>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async login(email: string, password: string): Promise<AuthData> {
    const data = await apiFetch<AuthData>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    setToken(data.token);
    return data;
  },

  async me(): Promise<User> {
    return apiFetch<User>('/auth/me');
  },

  async sendVerificationOtp(email: string): Promise<string> {
    return apiFetch<string>('/auth/send-verification-otp', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  async verifyOtp(email: string, otp: string): Promise<string> {
    return apiFetch<string>('/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({ email, otp }),
    });
  },

  async sendPasswordResetOtp(email: string): Promise<string> {
    return apiFetch<string>('/auth/send-password-reset-otp', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  async resetPassword(email: string, otp: string, newPassword: string): Promise<string> {
    return apiFetch<string>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ email, otp, newPassword }),
    });
  },

  /** Presence ping for the admin traffic view. Silent by design. */
  async heartbeat(): Promise<void> {
    await apiFetch<string>('/auth/heartbeat', { method: 'POST' });
  },

  logout(): void {
    clearToken();
  },
};

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

  logout(): void {
    clearToken();
  },
};

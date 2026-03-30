import request from './request';
import type { AuthUser } from '@/stores/authStore';

interface LoginResponse {
  code: number;
  data: { token: string; user: AuthUser };
}

interface MeResponse {
  code: number;
  data: AuthUser;
}

export function login(username: string, password: string) {
  return request.post<LoginResponse>('/api/auth/login', { username, password });
}

export function getMe() {
  return request.get<MeResponse>('/api/auth/me');
}

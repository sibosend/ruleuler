import { create } from 'zustand';
import { getMe } from '@/api/auth';

const TOKEN_KEY = 'ruleuler_token';
const TOKEN_COOKIE = 'ruleuler_token';

function setTokenCookie(token: string) {
  document.cookie = `${TOKEN_COOKIE}=${token}; path=/; SameSite=Strict`;
}

function clearTokenCookie() {
  document.cookie = `${TOKEN_COOKIE}=; path=/; max-age=0`;
}

export interface AuthUser {
  username: string;
  roles: string[];
  permissions: string[];
}

export interface AuthState {
  token: string | null;
  user: AuthUser | null;
  setAuth: (token: string, user: AuthUser) => void;
  logout: () => void;
  hasPermission: (code: string) => boolean;
  /** 恢复会话（多次调用只发一次请求） */
  restoreSession: () => Promise<void>;
}

let _restorePromise: Promise<void> | null = null;

// 初始化时同步 localStorage token 到 cookie（兼容已登录用户刷新页面）
const _initToken = localStorage.getItem(TOKEN_KEY);
if (_initToken) setTokenCookie(_initToken);

export const useAuthStore = create<AuthState>((set, get) => ({
  token: _initToken,
  user: null,

  setAuth: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    setTokenCookie(token);
    set({ token, user });
  },

  logout: () => {
    localStorage.removeItem(TOKEN_KEY);
    clearTokenCookie();
    _restorePromise = null;
    set({ token: null, user: null });
  },

  hasPermission: (code) => {
    const { user } = get();
    if (!user) return false;
    if (user.permissions.includes('*')) return true;
    return user.permissions.includes(code);
  },

  restoreSession: () => {
    if (_restorePromise) return _restorePromise;
    const { token } = get();
    if (!token) return Promise.resolve();
    _restorePromise = getMe()
      .then(({ data: res }) => {
        get().setAuth(token, res.data);
      })
      .catch(() => {
        get().logout();
      });
    return _restorePromise;
  },
}));

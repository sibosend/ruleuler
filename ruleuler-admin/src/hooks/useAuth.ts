import { useAuthStore } from '@/stores/authStore';

export function useAuth() {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const logout = useAuthStore((s) => s.logout);
  const setAuth = useAuthStore((s) => s.setAuth);

  return {
    user,
    token,
    username: user?.username ?? null,
    roles: user?.roles ?? [],
    permissions: user?.permissions ?? [],
    isLoggedIn: !!token,
    logout,
    setAuth,
  };
}

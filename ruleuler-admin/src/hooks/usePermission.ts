import { useAuthStore } from '@/stores/authStore';

export function usePermission(code: string): boolean {
  return useAuthStore((s) => s.hasPermission(code));
}

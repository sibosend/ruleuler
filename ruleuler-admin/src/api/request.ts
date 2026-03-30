import axios from 'axios';
import { message } from 'antd';
import { useAuthStore } from '@/stores/authStore';

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL as string,
  timeout: 30_000,
});

request.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  (res) => res,
  (error) => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      if (status === 401) {
        useAuthStore.getState().logout();
        window.location.href = '/admin/login';
        return Promise.reject(error);
      }
      const msg =
        (error.response?.data as { message?: string })?.message ??
        error.message;
      message.error(msg || '请求失败');
    } else {
      message.error('网络连接失败');
    }
    return Promise.reject(error);
  },
);

export default request;

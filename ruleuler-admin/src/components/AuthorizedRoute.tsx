import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Result, Button, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/stores/authStore';

interface AuthorizedRouteProps {
  permissionCode?: string;
  children?: React.ReactNode;
}

const AuthorizedRoute: React.FC<AuthorizedRouteProps> = ({
  permissionCode,
  children,
}) => {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const restoreSession = useAuthStore((s) => s.restoreSession);
  const hasPermission = useAuthStore((s) => s.hasPermission);
  const [loading, setLoading] = useState(false);
  const { t } = useTranslation();

  // 有 token 但无 user（页面刷新场景），调 restoreSession 恢复会话（多实例去重）
  useEffect(() => {
    if (token && !user) {
      setLoading(true);
      restoreSession().finally(() => setLoading(false));
    }
  }, [token, user]);

  // 无 token → 登录页
  if (!token) {
    return <Navigate to="/login" replace />;
  }

  // 正在恢复用户信息
  if (loading || !user) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 200 }} />;
  }

  // 有 permissionCode 但无权限 → 403
  if (permissionCode && !hasPermission(permissionCode)) {
    return (
      <Result
        status="403"
        title="403"
        subTitle={t('forbidden.noPermission')}
        extra={
          <Button type="primary" href="/admin/">
            {t('forbidden.backToHome')}
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
};

export default AuthorizedRoute;

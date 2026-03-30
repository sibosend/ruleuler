import React from 'react';
import { Card, Typography } from 'antd';
import { useAuthStore } from '@/stores/authStore';

const Dashboard: React.FC = () => {
  const username = useAuthStore((s) => s.user?.username);

  return (
    <Card>
      <Typography.Title level={3}>
        欢迎回来，{username ?? '用户'}
      </Typography.Title>
      <Typography.Paragraph>这是 RulEuler Admin 管理后台。</Typography.Paragraph>
    </Card>
  );
};

export default Dashboard;

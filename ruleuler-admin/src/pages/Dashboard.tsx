import React from 'react';
import { Card, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/stores/authStore';

const Dashboard: React.FC = () => {
  const username = useAuthStore((s) => s.user?.username);
  const { t } = useTranslation();

  return (
    <Card>
      <Typography.Title level={3}>
        {t('dashboard.welcomeBack', { name: username ?? t('dashboard.user') })}
      </Typography.Title>
      <Typography.Paragraph>{t('dashboard.description')}</Typography.Paragraph>
    </Card>
  );
};

export default Dashboard;

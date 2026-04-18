import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const Forbidden: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  return (
    <Result
      status="403"
      title="403"
      subTitle={t('forbidden.noPermission')}
      extra={
        <Button type="primary" onClick={() => navigate('/', { replace: true })}>
          {t('forbidden.backToHome')}
        </Button>
      }
    />
  );
};

export default Forbidden;

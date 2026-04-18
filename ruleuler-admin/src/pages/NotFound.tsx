import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const NotFound: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  return (
    <Result
      status="404"
      title="404"
      subTitle={t('notFound.pageNotExist')}
      extra={
        <Button type="primary" onClick={() => navigate('/', { replace: true })}>
          {t('notFound.backToHome')}
        </Button>
      }
    />
  );
};

export default NotFound;

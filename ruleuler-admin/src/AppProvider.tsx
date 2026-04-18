import React from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import App from '@/App';
import { useLocaleStore } from '@/stores/localeStore';

const AppProvider: React.FC = () => {
  const locale = useLocaleStore((s) => s.locale);
  const antdLocale = locale === 'en' ? enUS : zhCN;

  return (
    <ConfigProvider locale={antdLocale}>
      <App />
    </ConfigProvider>
  );
};

export default AppProvider;

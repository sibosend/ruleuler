import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zhCN from './locales/zh-CN.json';
import en from './locales/en.json';

const saved = localStorage.getItem('ruleuler_locale');
i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    en: { translation: en },
  },
  lng: saved || 'zh-CN',
  fallbackLng: false,
  interpolation: { escapeValue: false },
  react: { useSuspense: false },
});

export default i18n;

import { create } from 'zustand';
import i18n from '@/i18n';

type Locale = 'zh-CN' | 'en';

interface LocaleState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
}

export const useLocaleStore = create<LocaleState>((set) => ({
  locale: (localStorage.getItem('ruleuler_locale') as Locale) || 'zh-CN',
  setLocale: (locale) => {
    localStorage.setItem('ruleuler_locale', locale);
    i18n.changeLanguage(locale);
    set({ locale });
  },
}));

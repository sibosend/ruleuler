import React from 'react';
import ReactDOM from 'react-dom/client';
import './i18n';
import AppProvider from '@/AppProvider';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppProvider />
  </React.StrictMode>,
);

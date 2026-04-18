import React from 'react';
import { Dropdown, Button } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useLocaleStore } from '@/stores/localeStore';

const LANG_OPTIONS = [
  { key: 'zh-CN', label: '中文' },
  { key: 'en', label: 'English' },
];

const LanguageSwitch: React.FC = () => {
  const setLocale = useLocaleStore((s) => s.setLocale);
  const locale = useLocaleStore((s) => s.locale);

  const items: MenuProps['items'] = LANG_OPTIONS.map((opt) => ({
    key: opt.key,
    label: opt.label,
  }));

  const current = LANG_OPTIONS.find((o) => o.key === locale) ?? LANG_OPTIONS[0]!;

  return (
    <Dropdown
      menu={{
        items,
        selectedKeys: [locale],
        onClick: ({ key }) => {
          setLocale(key as 'zh-CN' | 'en');
        },
      }}
      placement="bottomRight"
    >
      <Button type="text" icon={<GlobalOutlined />} style={{ marginRight: 4 }}>
        {current.label}
      </Button>
    </Dropdown>
  );
};

export default LanguageSwitch;

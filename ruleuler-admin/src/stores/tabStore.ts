import { create } from 'zustand';

export interface TabItem {
  key: string;      // 路由路径，如 /console/myproject/edit/资源/库/变量库1.vl.xml
  label: string;    // 显示名称
  closable: boolean;
}

interface TabState {
  tabs: TabItem[];
  activeKey: string;
  addTab: (tab: TabItem) => void;
  removeTab: (key: string) => string; // 返回新的 activeKey
  setActiveKey: (key: string) => void;
}

export const useTabStore = create<TabState>((set, get) => ({
  tabs: [{ key: '/', label: '仪表盘', closable: false }],
  activeKey: '/',

  addTab: (tab) => {
    const { tabs } = get();
    if (!tabs.find((t) => t.key === tab.key)) {
      set({ tabs: [...tabs, tab], activeKey: tab.key });
    } else {
      set({ activeKey: tab.key });
    }
  },

  removeTab: (key) => {
    const { tabs, activeKey } = get();
    const idx = tabs.findIndex((t) => t.key === key);
    if (idx < 0) return activeKey;

    const newTabs = tabs.filter((t) => t.key !== key);
    let newActive = activeKey;

    if (activeKey === key) {
      // 优先后一个 tab，不存在则前一个，无 tab 时返回 '/'
      if (idx < newTabs.length) {
        newActive = newTabs[idx]!.key;
      } else if (newTabs.length > 0) {
        newActive = newTabs[newTabs.length - 1]!.key;
      } else {
        newActive = '/';
      }
    }

    set({ tabs: newTabs, activeKey: newActive });
    return newActive;
  },

  setActiveKey: (key) => set({ activeKey: key }),
}));

/**
 * Feature: console-tree-refactor, Property 3: addTab 幂等性与激活
 * Validates: Requirements 5.1 (原 3.2)
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { useTabStore, type TabItem } from '@/stores/tabStore';

const DEFAULT_TAB: TabItem = { key: '/', label: '仪表盘', closable: false };

/** 生成合法的 TabItem（模拟编辑器 tab） */
const tabItemArb: fc.Arbitrary<TabItem> = fc
  .tuple(
    fc.stringMatching(/^\/console\/[a-zA-Z][a-zA-Z0-9_-]{0,10}\/edit\/[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5/_.-]{0,30}$/),
    fc.stringMatching(/^[a-zA-Z\u4e00-\u9fa5][a-zA-Z0-9\u4e00-\u9fa5_.-]{0,20}$/),
  )
  .map(([key, label]) => ({ key, label, closable: true }));

describe('Property 3: addTab 幂等性与激活', () => {
  beforeEach(() => {
    useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });
  });

  it('addTab 后 tabs 包含该 tab 且 activeKey 等于该 tab 的 key', () => {
    fc.assert(
      fc.property(tabItemArb, (tab) => {
        // 重置状态
        useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });

        useTabStore.getState().addTab(tab);

        const { tabs, activeKey } = useTabStore.getState();
        expect(tabs.some((t) => t.key === tab.key)).toBe(true);
        expect(activeKey).toBe(tab.key);
      }),
      { numRuns: 100 },
    );
  });

  it('重复 addTab 同一个 key 不产生重复条目', () => {
    fc.assert(
      fc.property(tabItemArb, (tab) => {
        useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });

        const { addTab } = useTabStore.getState();
        addTab(tab);
        addTab(tab);

        const { tabs } = useTabStore.getState();
        const count = tabs.filter((t) => t.key === tab.key).length;
        expect(count).toBe(1);
      }),
      { numRuns: 100 },
    );
  });

  it('addTab 多个不同 tab 后，最后一个 addTab 的 key 为 activeKey', () => {
    const twoTabsArb = fc
      .tuple(tabItemArb, tabItemArb)
      .filter(([a, b]) => a.key !== b.key);

    fc.assert(
      fc.property(twoTabsArb, ([tab1, tab2]) => {
        useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });

        const { addTab } = useTabStore.getState();
        addTab(tab1);
        addTab(tab2);

        const { tabs, activeKey } = useTabStore.getState();
        expect(activeKey).toBe(tab2.key);
        expect(tabs.some((t) => t.key === tab1.key)).toBe(true);
        expect(tabs.some((t) => t.key === tab2.key)).toBe(true);
      }),
      { numRuns: 100 },
    );
  });
});

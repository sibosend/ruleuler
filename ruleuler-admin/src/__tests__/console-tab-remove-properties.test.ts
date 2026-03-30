/**
 * Feature: console-tree-refactor, Property 5: removeTab 激活相邻 tab
 * Validates: Requirements 5.2
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { useTabStore, type TabItem } from '@/stores/tabStore';

const DEFAULT_TAB: TabItem = { key: '/', label: '仪表盘', closable: false };

/** 生成 N 个唯一 tab key（N >= 2） */
const uniqueTabKeysArb = (min = 2, max = 10): fc.Arbitrary<string[]> =>
  fc
    .uniqueArray(
      fc.stringMatching(
        /^\/console\/[a-zA-Z][a-zA-Z0-9]{0,5}\/edit\/[a-zA-Z][a-zA-Z0-9]{0,10}$/,
      ),
      { minLength: min, maxLength: max },
    );

describe('Property 5: removeTab 激活相邻 tab', () => {
  beforeEach(() => {
    useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });
  });

  it('关闭当前激活 tab 后，activeKey 指向相邻 tab（优先后一个，否则前一个）', () => {
    fc.assert(
      fc.property(
        uniqueTabKeysArb(),
        fc.nat(),
        (keys, idxSeed) => {
          // 重置
          useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });

          const { addTab, setActiveKey, removeTab } = useTabStore.getState();

          // 添加所有 tab
          const tabs: TabItem[] = keys.map((k) => ({
            key: k,
            label: k.split('/').pop()!,
            closable: true,
          }));
          tabs.forEach((t) => addTab(t));

          // 选择一个 tab 作为 activeKey
          const activeIdx = idxSeed % keys.length;
          const activeKey = keys[activeIdx];
          setActiveKey(activeKey);

          // 获取 remove 前的完整 tabs 列表
          const tabsBefore = useTabStore.getState().tabs;
          const idxInAll = tabsBefore.findIndex((t) => t.key === activeKey);

          // 移除当前激活 tab
          const newActive = removeTab(activeKey);

          // 验证：新 activeKey 应该是相邻 tab
          const tabsAfter = tabsBefore.filter((t) => t.key !== activeKey);
          // 优先后一个（同索引位置），不存在则前一个（最后一个）
          let expectedKey: string;
          if (idxInAll < tabsAfter.length) {
            expectedKey = tabsAfter[idxInAll].key;
          } else {
            expectedKey = tabsAfter[tabsAfter.length - 1].key;
          }

          expect(newActive).toBe(expectedKey);
          expect(useTabStore.getState().activeKey).toBe(expectedKey);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('关闭非激活 tab 不改变 activeKey', () => {
    fc.assert(
      fc.property(
        uniqueTabKeysArb(2),
        (keys) => {
          useTabStore.setState({ tabs: [DEFAULT_TAB], activeKey: '/' });

          const { addTab, setActiveKey, removeTab } = useTabStore.getState();

          keys.forEach((k) =>
            addTab({ key: k, label: k.split('/').pop()!, closable: true }),
          );

          // 激活第一个，移除第二个
          setActiveKey(keys[0]);
          const newActive = removeTab(keys[1]);

          expect(newActive).toBe(keys[0]);
          expect(useTabStore.getState().activeKey).toBe(keys[0]);
        },
      ),
      { numRuns: 100 },
    );
  });
});

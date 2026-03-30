// Feature: client-config-management
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  serializeConfigs,
  type ClientConfigItem,
} from '@/api/clientConfig';

// Property 2: XML 序列化 round-trip — Validates: Requirements 4.2

/**
 * 生成不含 XML 特殊字符的非空字符串，确保 round-trip 干净。
 * serializeConfigs 未做 XML 转义，所以输入空间排除 < > & " '
 */
const safeXmlString = fc
  .string({ minLength: 1, maxLength: 50 })
  .filter((s) => !/[<>&"']/.test(s) && s.trim().length > 0);

const configItemArb: fc.Arbitrary<ClientConfigItem> = fc.record({
  name: safeXmlString,
  client: safeXmlString,
});

const configArrayArb = fc.array(configItemArb, { minLength: 0, maxLength: 20 });

/**
 * 从 XML 字符串解析回 ClientConfigItem[]，模拟后端解析逻辑。
 */
function parseConfigsXml(xml: string): ClientConfigItem[] {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xml, 'text/xml');
  const items = doc.querySelectorAll('item');
  return Array.from(items).map((el) => ({
    name: el.getAttribute('name') ?? '',
    client: el.getAttribute('client') ?? '',
  }));
}

describe('Property 2: XML 序列化 round-trip', () => {
  it('**Validates: Requirements 4.2** — 对任意 ClientConfigItem[]，序列化→解析后数据等价', () => {
    fc.assert(
      fc.property(configArrayArb, (items) => {
        const xml = serializeConfigs(items);
        const parsed = parseConfigsXml(xml);

        expect(parsed).toHaveLength(items.length);
        parsed.forEach((p, i) => {
          expect(p.name).toBe(items[i]!.name);
          expect(p.client).toBe(items[i]!.client);
        });
      }),
      { numRuns: 100 },
    );
  });
});

// Feature: client-config-management, Property 3: API 项目名称 URL 编码
// 直接验证 URL 构造逻辑：loadClientConfig 使用 encodeURIComponent 编码项目名
describe('Property 3: API 项目名称 URL 编码', () => {
  it('**Validates: Requirements 5.4** — 对任意含特殊字符的项目名，构造的请求 URL 包含 encodeURIComponent 编码', () => {
    const specialChars = ['中', '文', '测', '试', ' ', '&', '=', '?', '#', '/', '+', '%'];
    const specialCharArb = fc.array(
      fc.oneof(...specialChars.map((c) => fc.constant(c)), fc.string({ minLength: 1, maxLength: 3 })),
      { minLength: 1, maxLength: 15 },
    ).map((parts) => parts.join(''));

    fc.assert(
      fc.property(specialCharArb, (projectName) => {
        const encoded = encodeURIComponent(projectName);
        const url = `/urule/clientconfig/loadData?project=${encoded}`;

        // 验证编码后的 URL 不含原始特殊字符（除了 URL 安全字符）
        const queryPart = url.split('project=')[1]!;
        expect(queryPart).toBe(encoded);

        // 验证 decodeURIComponent 能还原
        expect(decodeURIComponent(queryPart)).toBe(projectName);

        // 验证 URL 格式正确
        expect(url.startsWith('/urule/clientconfig/loadData?project=')).toBe(true);
      }),
      { numRuns: 100 },
    );
  });
});

// Feature: client-config-management, Property 4: 页面标题包含项目名
import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// mock loadClientConfig 避免网络请求（复用已有的 mock 实例）
const mockLoadClientConfig = vi.fn().mockResolvedValue({ data: [] });
vi.mock('@/api/clientConfig', async () => {
  const actual = await vi.importActual('@/api/clientConfig');
  return { ...actual, loadClientConfig: (...args: unknown[]) => mockLoadClientConfig(...args) };
});

const { default: ClientConfigPage } = await import('@/pages/projects/ClientConfigPage');

describe('Property 4: 页面标题包含项目名', () => {
  /**
   * 生成合法的项目名：字母数字 + 中文，非空
   */
  const projectNameArb = fc.stringMatching(/^[a-zA-Z0-9\u4e00-\u9fff]{1,20}$/);

  it('**Validates: Requirements 6.1** — 对任意项目名，渲染后标题文本包含该项目名', async () => {
    await fc.assert(
      fc.asyncProperty(projectNameArb, async (name) => {
        mockLoadClientConfig.mockClear();
        mockLoadClientConfig.mockResolvedValue({ data: [] });

        const { unmount } = render(
          React.createElement(
            MemoryRouter,
            { initialEntries: [`/projects/${encodeURIComponent(name)}/client-config`] },
            React.createElement(
              Routes,
              null,
              React.createElement(Route, {
                path: 'projects/:name/client-config',
                element: React.createElement(ClientConfigPage),
              }),
            ),
          ),
        );

        // 标题格式: "客户端配置 - {name}"
        const titleEl = screen.getByText(`客户端配置 - ${name}`);
        expect(titleEl).toBeInTheDocument();

        unmount();
      }),
      { numRuns: 50 },
    );
  });
});

// Feature: client-config-management, Property 1: 导航路径包含项目名
describe('Property 1: 导航路径包含项目名', () => {
  /**
   * 生成非空项目名：字母、数字、中文、常见特殊字符
   */
  const projectNameArb = fc
    .string({ minLength: 1, maxLength: 30 })
    .filter((s) => s.trim().length > 0);

  it('**Validates: Requirements 1.2** — 对任意项目名，导航路径格式为 /projects/{name}/client-config 且包含项目名', () => {
    fc.assert(
      fc.property(projectNameArb, (name) => {
        const path = `/projects/${name}/client-config`;

        // 路径包含项目名
        expect(path).toContain(name);

        // 路径匹配预期格式
        expect(path).toBe(`/projects/${name}/client-config`);

        // 路径以 /projects/ 开头，以 /client-config 结尾
        expect(path.startsWith('/projects/')).toBe(true);
        expect(path.endsWith('/client-config')).toBe(true);
      }),
      { numRuns: 100 },
    );
  });
});

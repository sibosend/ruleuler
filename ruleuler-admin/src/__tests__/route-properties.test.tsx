/**
 * 前端属性测试 — Property 1, 7, 8, 17
 * Feature: modern-admin-console
 * 测试框架: Vitest + fast-check + @testing-library/react
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import * as fc from 'fast-check';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import {
  filterMenuByPermissions,
  generateBreadcrumbs,
} from '@/routes';
import type { RouteConfig } from '@/routes';

// mock antd — Result 需要真实渲染，Button 简化
vi.mock('antd', () => ({
  Result: ({ status, title }: { status: string; title: string }) =>
    React.createElement('div', { 'data-testid': `result-${status}`, 'data-title': title }, `${status} ${title}`),
  Button: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('a', props, children),
  message: { error: vi.fn() },
}));

// 在 mock 生效后动态 import AuthorizedRoute
const { default: AuthorizedRoute } = await import('@/components/AuthorizedRoute');

beforeEach(() => {
  useAuthStore.setState({ token: null, user: null });
});

// ============================================================
// Generators
// ============================================================

/** 生成合法的路由路径段 */
const pathSegmentArb = fc.stringMatching(/^[a-z][a-z0-9-]{0,10}$/);

/** 生成 permissionCode */
const permCodeArb = fc.stringMatching(/^menu:[a-z][a-z0-9:]{0,20}$/);

/** 生成简化 RouteConfig（不含 icon，纯数据） */
const leafRouteArb: fc.Arbitrary<RouteConfig> = fc.record({
  path: pathSegmentArb.map((s) => `/${s}`),
  label: fc.string({ minLength: 1, maxLength: 20 }),
  permissionCode: fc.option(permCodeArb, { nil: undefined }),
  hideInMenu: fc.option(fc.boolean(), { nil: undefined }),
});

/** 生成带 children 的 RouteConfig */
const routeConfigArb: fc.Arbitrary<RouteConfig> = fc.record({
  path: pathSegmentArb.map((s) => `/${s}`),
  label: fc.string({ minLength: 1, maxLength: 20 }),
  permissionCode: fc.option(permCodeArb, { nil: undefined }),
  hideInMenu: fc.option(fc.boolean(), { nil: undefined }),
  children: fc.option(
    fc.array(leafRouteArb, { minLength: 1, maxLength: 3 }).map((children) =>
      // 确保子路由 path 以父路由 path 为前缀（这里简化处理）
      children,
    ),
    { nil: undefined },
  ),
});

const routeConfigListArb = fc.array(routeConfigArb, { minLength: 1, maxLength: 6 });

// ============================================================
// Feature: modern-admin-console, Property 1: 未登录重定向
// Validates: Requirements 3.4
// ============================================================
describe('Property 1: 未登录重定向', () => {
  it('对任意受保护路由，token 为 null 时应重定向到 /login', () => {
    fc.assert(
      fc.property(
        permCodeArb,
        pathSegmentArb.map((s) => `/${s}`),
        (permCode, routePath) => {
          // 确保未登录
          useAuthStore.setState({ token: null, user: null });

          const { unmount } = render(
            React.createElement(
              MemoryRouter,
              { initialEntries: [routePath] },
              React.createElement(
                Routes,
                null,
                React.createElement(Route, {
                  path: routePath,
                  element: React.createElement(
                    AuthorizedRoute,
                    { permissionCode: permCode },
                    React.createElement('div', null, 'protected-content'),
                  ),
                }),
                React.createElement(Route, {
                  path: '/login',
                  element: React.createElement('div', { 'data-testid': 'login-page' }, 'login'),
                }),
              ),
            ),
          );

          // 应该重定向到 /login
          expect(screen.getByTestId('login-page')).toBeInTheDocument();
          // 不应渲染受保护内容
          expect(screen.queryByText('protected-content')).not.toBeInTheDocument();

          unmount();
        },
      ),
      { numRuns: 100 },
    );
  });
});


// ============================================================
// Feature: modern-admin-console, Property 7: 菜单按权限过滤
// Validates: Requirements 10.2
// ============================================================
describe('Property 7: 菜单按权限过滤', () => {
  /** 递归收集过滤结果中所有叶子节点 */
  function collectLeaves(routes: RouteConfig[]): RouteConfig[] {
    const leaves: RouteConfig[] = [];
    for (const r of routes) {
      if (r.children && r.children.length > 0) {
        leaves.push(...collectLeaves(r.children));
      } else {
        leaves.push(r);
      }
    }
    return leaves;
  }

  it('过滤后的菜单项仅包含用户拥有权限的菜单（非 hideInMenu）', () => {
    fc.assert(
      fc.property(
        routeConfigListArb,
        fc.array(permCodeArb, { minLength: 0, maxLength: 10 }),
        (routes, permissions) => {
          const result = filterMenuByPermissions(routes, permissions);

          // 1. 结果中不应包含 hideInMenu 的项
          for (const item of result) {
            expect(item.hideInMenu).not.toBe(true);
            if (item.children) {
              for (const child of item.children) {
                expect(child.hideInMenu).not.toBe(true);
              }
            }
          }

          // 2. 结果中每个叶子节点，如果有 permissionCode，用户必须拥有该权限
          const hasPermission = (code: string) =>
            permissions.includes('*') || permissions.includes(code);

          const leaves = collectLeaves(result);
          for (const leaf of leaves) {
            if (leaf.permissionCode) {
              expect(hasPermission(leaf.permissionCode)).toBe(true);
            }
          }
        },
      ),
      { numRuns: 100 },
    );
  });

  it('通配符 * 权限应返回所有非 hideInMenu 的菜单', () => {
    fc.assert(
      fc.property(routeConfigListArb, (routes) => {
        const result = filterMenuByPermissions(routes, ['*']);
        // 所有非 hideInMenu 的顶级路由都应出现
        const expectedTopLevel = routes.filter((r) => !r.hideInMenu);
        // 有 children 的路由，只要有非 hideInMenu 的子路由就应出现
        for (const expected of expectedTopLevel) {
          if (expected.children) {
            const visibleChildren = expected.children.filter((c) => !c.hideInMenu);
            if (visibleChildren.length > 0) {
              const found = result.find((r) => r.path === expected.path);
              expect(found).toBeDefined();
            }
          } else {
            const found = result.find((r) => r.path === expected.path);
            expect(found).toBeDefined();
          }
        }
      }),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: modern-admin-console, Property 8: 无权限路由显示 403
// Validates: Requirements 10.3
// ============================================================
describe('Property 8: 无权限路由显示 403', () => {
  it('已登录但无权限时应渲染 403 页面', () => {
    fc.assert(
      fc.property(
        permCodeArb,
        pathSegmentArb.map((s) => `/${s}`),
        // 生成不包含目标 permCode 且不包含 * 的权限列表
        fc.array(permCodeArb, { minLength: 0, maxLength: 5 }),
        (permCode, routePath, otherPerms) => {
          // 确保权限列表不包含目标 permCode 和 *
          const permissions = otherPerms.filter((p) => p !== permCode && p !== '*');

          useAuthStore.setState({
            token: 'valid-token',
            user: { username: 'testuser', roles: ['viewer'], permissions },
          });

          const { unmount } = render(
            React.createElement(
              MemoryRouter,
              { initialEntries: [routePath] },
              React.createElement(
                Routes,
                null,
                React.createElement(Route, {
                  path: routePath,
                  element: React.createElement(
                    AuthorizedRoute,
                    { permissionCode: permCode },
                    React.createElement('div', { 'data-testid': 'child-content' }, 'child'),
                  ),
                }),
              ),
            ),
          );

          // 应渲染 403 Result
          expect(screen.getByTestId('result-403')).toBeInTheDocument();
          // 不应渲染子组件
          expect(screen.queryByTestId('child-content')).not.toBeInTheDocument();

          unmount();
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: modern-admin-console, Property 17: 面包屑生成
// Validates: Requirements 2.3
// ============================================================
describe('Property 17: 面包屑生成', () => {
  /** 生成有层级关系的路由配置和对应的路径 */
  const nestedRouteAndPathArb = fc
    .tuple(
      pathSegmentArb,
      pathSegmentArb,
      fc.string({ minLength: 1, maxLength: 10 }),
      fc.string({ minLength: 1, maxLength: 10 }),
    )
    .filter(([seg1, seg2]) => seg1 !== seg2)
    .map(([seg1, seg2, label1, label2]) => {
      const parentPath = `/${seg1}`;
      const childPath = `/${seg1}/${seg2}`;
      const routes: RouteConfig[] = [
        {
          path: parentPath,
          label: label1,
          children: [
            {
              path: childPath,
              label: label2,
            },
          ],
        },
      ];
      return { routes, pathname: childPath, parentLabel: label1, childLabel: label2 };
    });

  it('面包屑最后一项应对应当前路由的 label', () => {
    fc.assert(
      fc.property(nestedRouteAndPathArb, ({ routes, pathname, childLabel }) => {
        const crumbs = generateBreadcrumbs(pathname, routes);
        // 面包屑不为空
        expect(crumbs.length).toBeGreaterThan(0);
        // 最后一项 label 应为当前路由的 label
        const lastCrumb = crumbs[crumbs.length - 1]!;
        expect(lastCrumb.label).toBe(childLabel);
      }),
      { numRuns: 100 },
    );
  });

  it('面包屑层级应与路由嵌套层级一致', () => {
    fc.assert(
      fc.property(nestedRouteAndPathArb, ({ routes, pathname, parentLabel, childLabel }) => {
        const crumbs = generateBreadcrumbs(pathname, routes);
        // 两级嵌套路由应生成 2 个面包屑
        expect(crumbs.length).toBe(2);
        expect(crumbs[0]!.label).toBe(parentLabel);
        expect(crumbs[1]!.label).toBe(childLabel);
      }),
      { numRuns: 100 },
    );
  });

  it('单级路由应生成 1 个面包屑', () => {
    fc.assert(
      fc.property(
        pathSegmentArb,
        fc.string({ minLength: 1, maxLength: 10 }),
        (seg, label) => {
          const routes: RouteConfig[] = [{ path: `/${seg}`, label }];
          const crumbs = generateBreadcrumbs(`/${seg}`, routes);
          expect(crumbs.length).toBe(1);
          expect(crumbs[0]!.label).toBe(label);
          expect(crumbs[0]!.path).toBe(`/${seg}`);
        },
      ),
      { numRuns: 100 },
    );
  });
});

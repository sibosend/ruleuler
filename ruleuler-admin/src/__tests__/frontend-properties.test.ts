/**
 * 前端属性测试 — Property 2~6
 * Feature: modern-admin-console
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import * as fc from 'fast-check';
import MockAdapter from 'axios-mock-adapter';
import { useAuthStore } from '@/stores/authStore';

// mock antd message
const { mockMessageError } = vi.hoisted(() => ({
  mockMessageError: vi.fn(),
}));
vi.mock('antd', () => ({
  message: { error: mockMessageError },
}));

// 动态 import request（在 mock 生效后）
const { default: request } = await import('@/api/request');

let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(request);
  useAuthStore.setState({ token: null, user: null });
  vi.clearAllMocks();
});

afterEach(() => {
  mock.restore();
});

// ============================================================
// Feature: modern-admin-console, Property 2: 请求自动附加 token
// Validates: Requirements 5.5, 7.2
// ============================================================
describe('Property 2: 请求自动附加 token', () => {
  it('对任意 token 字符串，请求 Header 应包含 Bearer <token>', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.string({ minLength: 1, maxLength: 200 }),
        async (token) => {
          useAuthStore.setState({ token, user: null });
          mock.onGet('/test-token').reply(200, {});

          await request.get('/test-token');

          const lastReq = mock.history.get[mock.history.get.length - 1]!;
          expect(lastReq.headers?.['Authorization']).toBe(`Bearer ${token}`);

          mock.resetHistory();
        },
      ),
      { numRuns: 100 },
    );
  });

  it('token 为 null 时不附加 Authorization header', async () => {
    useAuthStore.setState({ token: null, user: null });
    mock.onGet('/test-no-token').reply(200, {});

    await request.get('/test-no-token');

    const lastReq = mock.history.get[0]!;
    expect(lastReq.headers?.['Authorization']).toBeUndefined();
  });
});

// ============================================================
// Feature: modern-admin-console, Property 3: 401 自动清除 token 并重定向
// Validates: Requirements 5.6, 7.3
// ============================================================
describe('Property 3: 401 自动清除 token 并重定向', () => {
  it('对任意 API 路径，401 响应应清除 token 并重定向', async () => {
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
      configurable: true,
    });

    await fc.assert(
      fc.asyncProperty(
        fc.stringMatching(/^\/[a-z]{1,20}$/).filter((s) => s.length > 1),
        async (path) => {
          useAuthStore.setState({
            token: 'valid-token',
            user: { username: 'u', roles: [], permissions: [] },
          });
          mock.onGet(path).reply(401);

          await request.get(path).catch(() => {});

          // token 应被清除
          expect(useAuthStore.getState().token).toBeNull();
          expect(useAuthStore.getState().user).toBeNull();
          // location 应被设置为 /admin/login
          expect(window.location.href).toBe('/admin/login');

          // reset for next iteration
          mock.reset();
          mock.onGet(/.+/).reply(401); // re-register
          window.location.href = '';
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: modern-admin-console, Property 4: 非 2xx 响应统一错误提示
// Validates: Requirements 4.6, 7.4
// ============================================================
describe('Property 4: 非 2xx 响应统一错误提示', () => {
  it('对任意非 2xx 且非 401 状态码，应触发 message.error', async () => {
    // 确保 401 重定向不干扰
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
      configurable: true,
    });

    await fc.assert(
      fc.asyncProperty(
        fc.integer({ min: 400, max: 599 }).filter((s) => s !== 401),
        async (status) => {
          useAuthStore.setState({ token: 'tok', user: null });
          mock.onGet('/err-test').reply(status, { message: '出错了' });

          await request.get('/err-test').catch(() => {});

          expect(mockMessageError).toHaveBeenCalled();

          mockMessageError.mockClear();
          mock.reset();
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: modern-admin-console, Property 5: 认证成功后状态正确存储
// Validates: Requirements 5.3
// ============================================================
describe('Property 5: 认证成功后状态正确存储', () => {
  it('对任意合法登录响应，setAuth 后 store 数据一致', () => {
    const codeArb = fc.stringMatching(/^[a-z][a-z0-9:/_]{0,30}$/);

    fc.assert(
      fc.property(
        fc.record({
          token: fc.string({ minLength: 1, maxLength: 200 }),
          username: fc.string({ minLength: 1, maxLength: 50 }),
          roles: fc.array(fc.string({ minLength: 1, maxLength: 30 }), {
            minLength: 1,
            maxLength: 5,
          }),
          permissions: fc.array(codeArb, { minLength: 0, maxLength: 10 }),
        }),
        ({ token, username, roles, permissions }) => {
          useAuthStore.getState().setAuth(token, {
            username,
            roles,
            permissions,
          });

          const state = useAuthStore.getState();
          expect(state.token).toBe(token);
          expect(state.user?.username).toBe(username);
          expect(state.user?.roles).toEqual(roles);
          expect(state.user?.permissions).toEqual(permissions);

          // cleanup
          useAuthStore.getState().logout();
        },
      ),
      { numRuns: 100 },
    );
  });
});

// ============================================================
// Feature: modern-admin-console, Property 6: 权限判断函数正确性
// Validates: Requirements 10.5, 10.6
// ============================================================
describe('Property 6: 权限判断函数正确性', () => {
  const codeArb = fc.stringMatching(/^[a-z][a-z0-9:/_]{0,30}$/);

  it('权限列表包含 * 时，任意 code 返回 true', () => {
    fc.assert(
      fc.property(codeArb, (code) => {
        useAuthStore.setState({
          token: 't',
          user: { username: 'u', roles: ['admin'], permissions: ['*'] },
        });
        expect(useAuthStore.getState().hasPermission(code)).toBe(true);
      }),
      { numRuns: 100 },
    );
  });

  it('权限列表包含该 code 时返回 true', () => {
    fc.assert(
      fc.property(
        codeArb,
        fc.array(codeArb, { minLength: 0, maxLength: 10 }),
        (code, extras) => {
          const permissions = [...extras, code];
          useAuthStore.setState({
            token: 't',
            user: { username: 'u', roles: [], permissions },
          });
          expect(useAuthStore.getState().hasPermission(code)).toBe(true);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('权限列表不包含 * 也不包含 code 时返回 false', () => {
    fc.assert(
      fc.property(
        codeArb,
        fc
          .array(codeArb, { minLength: 0, maxLength: 10 })
          .filter((arr) => !arr.includes('*')),
        (code, permissions) => {
          const filtered = permissions.filter((p) => p !== code);
          useAuthStore.setState({
            token: 't',
            user: { username: 'u', roles: [], permissions: filtered },
          });
          expect(useAuthStore.getState().hasPermission(code)).toBe(false);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('user 为 null 时返回 false', () => {
    fc.assert(
      fc.property(codeArb, (code) => {
        useAuthStore.setState({ token: null, user: null });
        expect(useAuthStore.getState().hasPermission(code)).toBe(false);
      }),
      { numRuns: 100 },
    );
  });
});

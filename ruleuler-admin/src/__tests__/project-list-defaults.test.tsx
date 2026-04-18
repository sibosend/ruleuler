/**
 * Bug 2 修复验证：默认存储类型为db
 * Spec: project-list-fixes
 * Validates: Requirements 2.2
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import MockAdapter from 'axios-mock-adapter';

// mock react-router-dom
const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

// mock antd message
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  };
});

// mock loadProjects 避免组件挂载时真实请求
vi.mock('@/api/project', async () => {
  const actual = await vi.importActual<typeof import('@/api/project')>('@/api/project');
  return {
    ...actual,
    loadProjects: vi.fn().mockResolvedValue({ data: [] }),
    deleteProject: vi.fn(),
    exportProject: vi.fn(),
    importProject: vi.fn(),
  };
});

import { formatStorageType } from '@/pages/projects/ProjectList';
import ProjectList from '@/pages/projects/ProjectList';
import { default as request } from '@/api/request';

let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(request);
  vi.clearAllMocks();
});

// ============================================================
// formatStorageType 函数测试（Preservation）
// ============================================================
describe('formatStorageType', () => {
  const t = (key: string) => {
    const map: Record<string, string> = {
      'project.storageDb': '数据库',
      'project.storageJcr': 'JCR',
    };
    return map[key] ?? key;
  };

  it('db → 数据库', () => {
    expect(formatStorageType('db', t)).toBe('数据库');
  });

  it('jcr → JCR', () => {
    expect(formatStorageType('jcr', t)).toBe('JCR');
  });

  it('undefined → JCR', () => {
    expect(formatStorageType(undefined, t)).toBe('JCR');
  });
});

// ============================================================
// createProject API 默认参数测试
// ============================================================
describe('createProject 默认 storageType 为 db', () => {
  it('不传 storageType 时 POST body 包含 storageType: db', async () => {
    mock.onPost('/api/projects').reply(200, {});

    const { createProject } = await vi.importActual<typeof import('@/api/project')>('@/api/project');
    await createProject('test-proj');

    const body = JSON.parse(mock.history.post[0].data);
    expect(body.storageType).toBe('db');
  });
});

// 存储类型 Select 已移除，创建项目默认使用 db

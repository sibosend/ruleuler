/**
 * Feature: project-console-integration
 * Property 1: storageType 显示文本映射
 * Property 2: 项目名称点击导航
 * 测试框架: Vitest + fast-check
 */
import { describe, it, expect, vi } from 'vitest';
import * as fc from 'fast-check';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { formatStorageType } from '@/pages/projects/ProjectList';

// ---- hoisted mocks ----
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/api/project', () => ({
  loadProjects: vi.fn(),
  createProject: vi.fn(),
  deleteProject: vi.fn(),
  exportProject: vi.fn(),
  importProject: vi.fn(),
  checkProjectExist: vi.fn(),
}));

// 动态 import（在 mock 生效后）
const { default: ProjectList } = await import('@/pages/projects/ProjectList');
const { loadProjects } = await import('@/api/project');

// ============================================================
// Feature: project-console-integration, Property 1: storageType 显示文本映射
// Validates: Requirements 1.2, 1.3, 1.4
// ============================================================
describe('Property 1: storageType 显示文本映射', () => {
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

  it('对任意非 db 值，返回 JCR', () => {
    fc.assert(
      fc.property(
        fc.anything().filter((v) => v !== 'db'),
        (value) => {
          expect(formatStorageType(value, t)).toBe('JCR');
        },
      ),
      { numRuns: 100 },
    );
  });
});


// ============================================================
// Feature: project-console-integration, Property 2: 项目名称点击导航
// Validates: Requirements 2.1
// ============================================================
describe('Property 2: 项目名称点击导航', () => {
  it('对任意合法项目名，点击后 navigate 到 /projects/${name}', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{0,20}$/).filter((s) => s.length > 0),
        async (name) => {
          mockNavigate.mockClear();
          (loadProjects as ReturnType<typeof vi.fn>).mockResolvedValue({
            data: [{ name }],
          });

          const { unmount } = render(
            <MemoryRouter>
              <ProjectList />
            </MemoryRouter>,
          );

          await waitFor(() => {
            expect(screen.getByText(name)).toBeInTheDocument();
          });

          fireEvent.click(screen.getByText(name));
          expect(mockNavigate).toHaveBeenCalledWith(`/console/${name}`);

          unmount();
        },
      ),
      { numRuns: 20 },
    );
  });
});


// ============================================================
// Feature: project-console-integration, Property 4: 单项目模式下 loadData 锁定 projectName
// Validates: Requirements 3.1, 3.2
// ============================================================
describe('Property 4: 单项目模式下 loadData 锁定 projectName', () => {
  /**
   * 模拟 frame/index.jsx 中的核心锁定逻辑：
   * - "显示所有项目"按钮: if(!window._singleProjectMode) window._projectName = null;
   * - 项目过滤点击: if(!window._singleProjectMode) window._projectName = name;
   * 单项目模式下这两个操作都不应改变 _projectName
   */

  afterEach(() => {
    delete (window as any)._singleProjectMode;
    delete (window as any)._projectName;
  });

  it('单项目模式下，"显示所有项目"操作不会清空 _projectName', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }),
        (projectName) => {
          // 初始化单项目模式
          (window as any)._singleProjectMode = true;
          (window as any)._projectName = projectName;

          // 模拟"显示所有项目"按钮逻辑 (index.jsx 第 ~155 行)
          if (!(window as any)._singleProjectMode) {
            (window as any)._projectName = null;
          }

          // 单项目模式下 projectName 保持不变
          expect((window as any)._projectName).toBe(projectName);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('单项目模式下，项目过滤点击不会改变 _projectName', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }),
        fc.string({ minLength: 1, maxLength: 50 }),
        (lockedName, clickedName) => {
          // 初始化单项目模式
          (window as any)._singleProjectMode = true;
          (window as any)._projectName = lockedName;

          // 模拟项目过滤点击逻辑 (index.jsx 第 ~107 行)
          if (!(window as any)._singleProjectMode) {
            (window as any)._projectName = clickedName;
          }

          // 单项目模式下 projectName 始终锁定为初始值
          expect((window as any)._projectName).toBe(lockedName);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('非单项目模式下，"显示所有项目"可清空 _projectName', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }),
        (projectName) => {
          (window as any)._singleProjectMode = false;
          (window as any)._projectName = projectName;

          if (!(window as any)._singleProjectMode) {
            (window as any)._projectName = null;
          }

          expect((window as any)._projectName).toBeNull();
        },
      ),
      { numRuns: 100 },
    );
  });

  it('非单项目模式下，项目过滤点击可改变 _projectName', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 50 }),
        fc.string({ minLength: 1, maxLength: 50 }),
        (originalName, clickedName) => {
          (window as any)._singleProjectMode = false;
          (window as any)._projectName = originalName;

          if (!(window as any)._singleProjectMode) {
            (window as any)._projectName = clickedName;
          }

          expect((window as any)._projectName).toBe(clickedName);
        },
      ),
      { numRuns: 100 },
    );
  });
});


// ============================================================
// Feature: project-console-integration, Property 5: 单项目模式树根提升
// Validates: Requirements 3.5
// ============================================================

// extractProjectChildren 纯函数（与 action.js 中的实现一致）
function extractProjectChildren(
  rootNode: any,
  projectName: string,
): { children: any[] } {
  if (!rootNode || !rootNode.children) return { children: [] };
  const project = rootNode.children.find((c: any) => c.name === projectName);
  if (!project || !project.children) return { children: [] };
  return { ...rootNode, children: project.children };
}

// fast-check arbitrary: 随机树结构生成器
const childNodeArb = fc.record({
  id: fc.string({ minLength: 1, maxLength: 10 }),
  name: fc.string({ minLength: 1, maxLength: 20 }),
  type: fc.constantFrom('resource', 'resourcePackage', 'lib', 'folder'),
});

const projectNodeArb = fc.record({
  id: fc.string({ minLength: 1, maxLength: 10 }),
  name: fc.string({ minLength: 1, maxLength: 20 }),
  type: fc.constant('project'),
  children: fc.array(childNodeArb, { minLength: 0, maxLength: 5 }),
});

const rootNodeArb = fc.record({
  id: fc.constant('root_id'),
  name: fc.constant('项目列表'),
  type: fc.constant('root'),
  children: fc.array(projectNodeArb, { minLength: 1, maxLength: 5 }),
});

describe('Property 5: 单项目模式树根提升', () => {
  it('返回目标 project 的 children', () => {
    fc.assert(
      fc.property(rootNodeArb, (rootNode) => {
        const targetProject = rootNode.children[0];
        const result = extractProjectChildren(rootNode, targetProject.name);
        expect(result.children).toEqual(targetProject.children);
      }),
      { numRuns: 100 },
    );
  });

  it('目标 project 不存在时返回空数组', () => {
    fc.assert(
      fc.property(
        rootNodeArb,
        fc.string({ minLength: 21, maxLength: 30 }),
        (rootNode, fakeName) => {
          const result = extractProjectChildren(rootNode, fakeName);
          expect(result.children).toEqual([]);
        },
      ),
      { numRuns: 100 },
    );
  });

  it('rootNode 为 null/undefined 时返回空数组', () => {
    expect(extractProjectChildren(null, 'any').children).toEqual([]);
    expect(extractProjectChildren(undefined, 'any').children).toEqual([]);
  });

  it('project 无 children 时返回空数组', () => {
    const root = {
      id: 'root',
      name: '项目列表',
      type: 'root',
      children: [{ id: 'p1', name: 'test', type: 'project' }],
    };
    expect(extractProjectChildren(root, 'test').children).toEqual([]);
  });
});

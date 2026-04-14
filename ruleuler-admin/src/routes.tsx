import React from 'react';
import {
  DashboardOutlined,
  ProjectOutlined,
  CodeOutlined,
  MonitorOutlined,
  SettingOutlined,
  UserOutlined,
  SafetyOutlined,
  CloudUploadOutlined,
  FileSearchOutlined,
} from '@ant-design/icons';

/** 路由配置项 */
export interface RouteConfig {
  path: string;
  label: string;
  icon?: React.ReactNode;
  permissionCode?: string;
  /** 是否在侧边栏菜单中隐藏 */
  hideInMenu?: boolean;
  children?: RouteConfig[];
}

/**
 * 路由配置表（纯数据结构，不含组件引用）
 * 组件在 App.tsx 中通过 lazy import 绑定
 */
export const routeConfigs: RouteConfig[] = [
  {
    path: '/',
    label: '仪表盘',
    icon: <DashboardOutlined />,
    permissionCode: 'menu:dashboard',
  },
  {
    path: '/projects',
    label: '项目管理',
    icon: <ProjectOutlined />,
    permissionCode: 'menu:projects',
  },
  {
    path: '/projects/:name',
    label: '项目详情',
    permissionCode: 'menu:projects',
    hideInMenu: true,
  },
  {
    path: '/projects/:name/client-config',
    label: '客户端配置',
    permissionCode: 'menu:projects',
    hideInMenu: true,
  },
  {
    path: '/projects/:name/autotest',
    label: '自动测试',
    permissionCode: 'menu:projects',
    hideInMenu: true,
  },
  {
    path: '/projects/:name/autotest/pack/:packId',
    label: '用例包详情',
    permissionCode: 'menu:projects',
    hideInMenu: true,
  },
  {
    path: '/projects/:name/autotest/run/:runId',
    label: '测试报告',
    permissionCode: 'menu:projects',
    hideInMenu: true,
  },
  {
    path: '/releases',
    label: '上线管理',
    icon: <CloudUploadOutlined />,
    permissionCode: 'menu:approvals',
    children: [
      {
        path: '/releases/pending',
        label: '待审核',
        permissionCode: 'menu:approvals',
      },
      {
        path: '/releases/pending-publish',
        label: '待上线',
        permissionCode: 'menu:approvals',
      },
      {
        path: '/releases/my',
        label: '我的申请',
        permissionCode: 'menu:approvals',
      },
      {
        path: '/releases/all',
        label: '全部记录',
        permissionCode: 'menu:approvals',
      },
    ],
  },
  {
    path: '/console',
    label: '规则编辑器',
    icon: <CodeOutlined />,
    permissionCode: 'menu:console',
  },
  {
    path: '/console/:project',
    label: '规则编辑器',
    permissionCode: 'menu:console',
    hideInMenu: true,
  },
  {
    path: '/console/:project/edit/*',
    label: '规则编辑器',
    permissionCode: 'menu:console',
    hideInMenu: true,
  },
  {
    path: '/monitoring',
    label: '变量监控',
    icon: <MonitorOutlined />,
    permissionCode: 'menu:monitoring',
    children: [
      {
        path: '/monitoring/realtime',
        label: '实时监控',
        permissionCode: 'menu:monitoring',
      },
      {
        path: '/monitoring/trend',
        label: '执行走势',
        permissionCode: 'menu:monitoring',
      },
      {
        path: '/monitoring/compare',
        label: '周期对比',
        permissionCode: 'menu:monitoring',
      },
      {
        path: '/monitoring/executions',
        label: '执行日志',
        permissionCode: 'menu:monitoring',
      },
    ],
  },
  {
    path: '/monitoring/executions/:id',
    label: '执行详情',
    permissionCode: 'menu:monitoring',
    hideInMenu: true,
  },
  {
    path: '/system',
    label: '系统设置',
    icon: <SettingOutlined />,
    permissionCode: 'menu:system',
    children: [
      {
        path: '/system/users',
        label: '用户管理',
        icon: <UserOutlined />,
        permissionCode: 'menu:system:users',
      },
      {
        path: '/system/roles',
        label: '角色管理',
        icon: <SafetyOutlined />,
        permissionCode: 'menu:system:roles',
      },
      {
        path: '/system/audit',
        label: '审计日志',
        icon: <FileSearchOutlined />,
        permissionCode: 'menu:system:audit',
      },
    ],
  },
];

/**
 * 扁平化路由配置（含子路由）
 */
export function flattenRoutes(routes: RouteConfig[]): RouteConfig[] {
  const result: RouteConfig[] = [];
  for (const route of routes) {
    result.push(route);
    if (route.children) {
      result.push(...flattenRoutes(route.children));
    }
  }
  return result;
}

/**
 * 菜单过滤：根据用户权限列表过滤可见菜单项
 * 导出为独立函数，方便属性测试
 */
export function filterMenuByPermissions(
  routes: RouteConfig[],
  permissions: string[],
): RouteConfig[] {
  const hasPermission = (code: string): boolean => {
    if (permissions.includes('*')) return true;
    return permissions.includes(code);
  };

  return routes
    .filter((route) => {
      if (route.hideInMenu) return false;
      if (!route.permissionCode) return true;
      // 对有子菜单的项，只要有任一子菜单有权限就显示父级
      if (route.children) {
        const visibleChildren = route.children.filter(
          (child) =>
            !child.hideInMenu &&
            (!child.permissionCode || hasPermission(child.permissionCode)),
        );
        return visibleChildren.length > 0;
      }
      return hasPermission(route.permissionCode);
    })
    .map((route) => {
      if (!route.children) return route;
      return {
        ...route,
        children: route.children.filter(
          (child) =>
            !child.hideInMenu &&
            (!child.permissionCode || hasPermission(child.permissionCode)),
        ),
      };
    });
}

/** 面包屑项 */
export interface BreadcrumbItem {
  path: string;
  label: string;
}

/**
 * 根据当前路径生成面包屑数组
 * 导出为独立函数，方便属性测试
 */
export function generateBreadcrumbs(
  pathname: string,
  routes: RouteConfig[],
): BreadcrumbItem[] {
  const allRoutes = flattenRoutes(routes);
  const crumbs: BreadcrumbItem[] = [];

  // 按路径段逐级匹配
  const segments = pathname.split('/').filter(Boolean);
  let currentPath = '';

  for (const segment of segments) {
    currentPath += `/${segment}`;
    const matched = allRoutes.find((r) => {
      // 精确匹配或参数匹配（如 /projects/:name）
      if (r.path === currentPath) return true;
      // 参数路由匹配
      const routeSegments = r.path.split('/').filter(Boolean);
      const pathSegments = currentPath.split('/').filter(Boolean);
      if (routeSegments.length !== pathSegments.length) return false;
      return routeSegments.every(
        (rs, i) => rs.startsWith(':') || rs === pathSegments[i],
      );
    });
    if (matched) {
      crumbs.push({ path: currentPath, label: matched.label });
    }
  }

  return crumbs;
}

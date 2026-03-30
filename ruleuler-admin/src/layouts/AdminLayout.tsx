import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Dropdown, Tabs, theme } from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { useAuthStore } from '@/stores/authStore';
import { useTabStore } from '@/stores/tabStore';
import {
  routeConfigs,
  filterMenuByPermissions,
  flattenRoutes,
} from '@/routes';
import type { RouteConfig } from '@/routes';

const { Header, Sider, Content } = Layout;

/** 将 RouteConfig 转为 antd Menu items */
function toMenuItems(routes: RouteConfig[]): MenuProps['items'] {
  return routes.map((route) => {
    if (route.children && route.children.length > 0) {
      return {
        key: route.path,
        icon: route.icon,
        label: route.label,
        children: route.children.map((child) => ({
          key: child.path,
          icon: child.icon,
          label: child.label,
        })),
      };
    }
    return {
      key: route.path,
      icon: route.icon,
      label: route.label,
    };
  });
}

const AdminLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const permissions = user?.permissions ?? [];
  const logout = useAuthStore((s) => s.logout);
  const { tabs, activeKey, addTab, removeTab, setActiveKey } = useTabStore();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const allRoutes = flattenRoutes(routeConfigs);
  const visibleMenus = filterMenuByPermissions(routeConfigs, permissions);
  const menuItems = toMenuItems(visibleMenus);

  // 路由变化时自动添加 tab
  useEffect(() => {
    const matched = allRoutes.find((r) => {
      if (r.path === location.pathname) return true;
      const rSegs = r.path.split('/').filter(Boolean);
      const pSegs = location.pathname.split('/').filter(Boolean);
      if (rSegs.length !== pSegs.length) return false;
      return rSegs.every((s, i) => s.startsWith(':') || s === pSegs[i]);
    });
    if (matched) {
      // 编辑器 tab 由 ConsolePage 管理，不在这里重复添加
      if (location.pathname.startsWith('/console/') && location.pathname.includes('/edit/')) {
        return;
      }
      let label = matched.label;
      if (matched.path.includes(':name')) {
        const name = location.pathname.split('/').pop();
        if (name) label = name;
      }
      addTab({
        key: location.pathname,
        label,
        closable: location.pathname !== '/',
      });
    }
  }, [location.pathname]);

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  const handleTabChange = (key: string) => {
    setActiveKey(key);
    navigate(key);
  };

  const handleTabEdit = (
    targetKey: React.MouseEvent | React.KeyboardEvent | string,
    action: 'add' | 'remove',
  ) => {
    if (action !== 'remove') return;
    const key = targetKey as string;
    const newActive = removeTab(key);
    navigate(newActive);
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  const selectedKeys = [location.pathname];
  const openKeys = routeConfigs
    .filter((r) => r.children && location.pathname.startsWith(r.path))
    .map((r) => r.path);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} theme="dark">
        <div
          style={{
            height: 32,
            margin: 16,
            color: '#fff',
            fontWeight: 'bold',
            fontSize: collapsed ? 14 : 18,
            textAlign: 'center',
            lineHeight: '32px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
          }}
        >
          {collapsed ? 'RE' : 'RulEuler Admin'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 16px',
            height: 56,
            lineHeight: '56px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', flex: 1, overflow: 'hidden' }}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{ flexShrink: 0 }}
            />
            <Tabs
              type="editable-card"
              hideAdd
              activeKey={activeKey}
              onChange={handleTabChange}
              onEdit={handleTabEdit}
              items={tabs.map((t) => ({
                key: t.key,
                label: t.label,
                closable: t.closable,
              }))}
              tabBarStyle={{ marginBottom: 0 }}
              style={{ flex: 1, marginBottom: 0, fontSize: 15 }}
            />
          </div>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Button type="text" icon={<UserOutlined />}>
              {user?.username ?? '未登录'}
            </Button>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: '8px 16px 16px',
            padding: 24,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
            minHeight: 280,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;

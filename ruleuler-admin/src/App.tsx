import React, { Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

const ReaEditorPage = React.lazy(() => import('@/pages/rea/ReaEditorPage'));
const ReaExpressionDoc = React.lazy(() => import('@/pages/docs/ReaExpressionDoc'));
import AdminLayout from '@/layouts/AdminLayout';
import AuthorizedRoute from '@/components/AuthorizedRoute';
import Login from '@/pages/Login';
import Dashboard from '@/pages/Dashboard';
import ProjectList from '@/pages/projects/ProjectList';
import ProjectDetail from '@/pages/projects/ProjectDetail';
import ClientConfigPage from '@/pages/projects/ClientConfigPage';
import ConsolePage from '@/pages/console/ConsolePage';
import UserList from '@/pages/system/UserList';
import RoleList from '@/pages/system/RoleList';
import NotFound from '@/pages/NotFound';

const PackListPage = React.lazy(() => import('@/pages/autotest/PackListPage'));
const PackDetailPage = React.lazy(() => import('@/pages/autotest/PackDetailPage'));
const TestReportPage = React.lazy(() => import('@/pages/autotest/TestReportPage'));

const App: React.FC = () => (
  <BrowserRouter basename="/admin">
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/rea-editor" element={<Suspense fallback={<div>Loading...</div>}><ReaEditorPage /></Suspense>} />
      <Route path="/docs/rea-expression" element={<Suspense fallback={<div>Loading...</div>}><ReaExpressionDoc /></Suspense>} />
      <Route
        element={
          <AuthorizedRoute>
            <AdminLayout />
          </AuthorizedRoute>
        }
      >
        <Route
          index
          element={
            <AuthorizedRoute permissionCode="menu:dashboard">
              <Dashboard />
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <ProjectList />
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects/:name"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <ProjectDetail />
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects/:name/client-config"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <ClientConfigPage />
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects/:name/autotest"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <Suspense fallback={<div>Loading...</div>}><PackListPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects/:name/autotest/pack/:packId"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <Suspense fallback={<div>Loading...</div>}><PackDetailPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="projects/:name/autotest/run/:runId"
          element={
            <AuthorizedRoute permissionCode="menu:projects">
              <Suspense fallback={<div>Loading...</div>}><TestReportPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="console"
          element={
            <AuthorizedRoute permissionCode="menu:console">
              <ConsolePage />
            </AuthorizedRoute>
          }
        />
        <Route
          path="console/:project"
          element={
            <AuthorizedRoute permissionCode="menu:console">
              <ConsolePage />
            </AuthorizedRoute>
          }
        />
        <Route
          path="console/:project/edit/*"
          element={
            <AuthorizedRoute permissionCode="menu:console">
              <ConsolePage />
            </AuthorizedRoute>
          }
        />
        <Route
          path="system/users"
          element={
            <AuthorizedRoute permissionCode="menu:system:users">
              <UserList />
            </AuthorizedRoute>
          }
        />
        <Route
          path="system/roles"
          element={
            <AuthorizedRoute permissionCode="menu:system:roles">
              <RoleList />
            </AuthorizedRoute>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  </BrowserRouter>
);

export default App;

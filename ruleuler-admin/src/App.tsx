import React, { Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

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
import AuditLogPage from '@/pages/system/AuditLogPage';
import NotFound from '@/pages/NotFound';

const PackListPage = React.lazy(() => import('@/pages/autotest/PackListPage'));
const PackDetailPage = React.lazy(() => import('@/pages/autotest/PackDetailPage'));
const TestReportPage = React.lazy(() => import('@/pages/autotest/TestReportPage'));

const MonitoringPage = React.lazy(() => import('@/pages/monitoring/MonitoringPage'));
const PeriodComparePage = React.lazy(() => import('@/pages/monitoring/PeriodComparePage'));
const ExecutionLogPage = React.lazy(() => import('@/pages/monitoring/ExecutionLogPage'));
const ExecutionDetailPage = React.lazy(() => import('@/pages/monitoring/ExecutionDetailPage'));
const ExecutionTrendPage = React.lazy(() => import('@/pages/monitoring/ExecutionTrendPage'));

const ReleaseListPage = React.lazy(() => import('@/pages/release/ReleaseListPage'));
const GrayscaleRulePage = React.lazy(() => import('@/pages/release/GrayscaleRulePage'));

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
          path="autotest/report/:runId"
          element={
            <Suspense fallback={<div>Loading...</div>}><TestReportPage /></Suspense>
          }
        />
        <Route
          path="releases"
          element={<Navigate to="/releases/pending" replace />}
        />
        <Route
          path="releases/pending"
          element={
            <AuthorizedRoute permissionCode="menu:approvals">
              <Suspense fallback={<div>Loading...</div>}><ReleaseListPage mode="pending" /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="releases/pending-publish"
          element={
            <AuthorizedRoute permissionCode="menu:approvals">
              <Suspense fallback={<div>Loading...</div>}><ReleaseListPage mode="pending-publish" /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="releases/my"
          element={
            <AuthorizedRoute permissionCode="menu:approvals">
              <Suspense fallback={<div>Loading...</div>}><ReleaseListPage mode="my" /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="releases/all"
          element={
            <AuthorizedRoute permissionCode="menu:approvals">
              <Suspense fallback={<div>Loading...</div>}><ReleaseListPage mode="all" /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="releases/grayscale"
          element={
            <AuthorizedRoute permissionCode="pack:grayscale:manage">
              <Suspense fallback={<div>Loading...</div>}><GrayscaleRulePage /></Suspense>
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
          path="monitoring"
          element={
            <Navigate to="/monitoring/realtime" replace />
          }
        />
        <Route
          path="monitoring/realtime"
          element={
            <AuthorizedRoute permissionCode="menu:monitoring">
              <Suspense fallback={<div>Loading...</div>}><MonitoringPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="monitoring/trend"
          element={
            <AuthorizedRoute permissionCode="menu:monitoring">
              <Suspense fallback={<div>Loading...</div>}><ExecutionTrendPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="monitoring/compare"
          element={
            <AuthorizedRoute permissionCode="menu:monitoring">
              <Suspense fallback={<div>Loading...</div>}><PeriodComparePage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="monitoring/executions"
          element={
            <AuthorizedRoute permissionCode="menu:monitoring">
              <Suspense fallback={<div>Loading...</div>}><ExecutionLogPage /></Suspense>
            </AuthorizedRoute>
          }
        />
        <Route
          path="monitoring/executions/:id"
          element={
            <AuthorizedRoute permissionCode="menu:monitoring">
              <Suspense fallback={<div>Loading...</div>}><ExecutionDetailPage /></Suspense>
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
        <Route
          path="system/audit"
          element={
            <AuthorizedRoute permissionCode="menu:system:audit">
              <AuditLogPage />
            </AuthorizedRoute>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  </BrowserRouter>
);

export default App;

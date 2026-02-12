import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'
import Layout from '@/components/Layout'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import Dashboard from '@/pages/Dashboard'
import ClustersPage from '@/pages/clusters/ClustersPage'
import ClusterDetailPage from '@/pages/clusters/ClusterDetailPage'
import TicketsPage from '@/pages/tickets/TicketsPage'
import TicketDetailPage from '@/pages/tickets/TicketDetailPage'
import BundleViewerPage from '@/pages/bundles/BundleViewerPage'
import UsersPage from '@/pages/admin/UsersPage'
import NotificationsPage from '@/pages/admin/NotificationsPage'
import OperatorDashboard from '@/pages/operator/OperatorDashboard'
import AllClustersPage from '@/pages/operator/AllClustersPage'
import TenantsPage from '@/pages/operator/TenantsPage'
import LicensesPage from '@/pages/operator/LicensesPage'
import RecommendationsPage from '@/pages/operator/RecommendationsPage'

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<Dashboard />} />
        <Route path="clusters" element={<ClustersPage />} />
        <Route path="clusters/:id" element={<ClusterDetailPage />} />
        <Route path="tickets" element={<TicketsPage />} />
        <Route path="tickets/:id" element={<TicketDetailPage />} />
        <Route path="bundles/:id" element={<BundleViewerPage />} />
        <Route path="admin/users" element={<UsersPage />} />
        <Route path="admin/notifications" element={<NotificationsPage />} />
        <Route path="operator" element={<OperatorDashboard />} />
        <Route path="operator/clusters" element={<AllClustersPage />} />
        <Route path="operator/tenants" element={<TenantsPage />} />
        <Route path="operator/licenses" element={<LicensesPage />} />
        <Route path="operator/recommendations" element={<RecommendationsPage />} />
      </Route>
    </Routes>
  )
}

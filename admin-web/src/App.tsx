import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './components/Layout'
import { AuditLogListPage } from './pages/AuditLogList'
import { CampaignDetailPage } from './pages/CampaignDetail'
import { CampaignListPage } from './pages/CampaignList'
import { CreatorDetailPage } from './pages/CreatorDetail'
import { CreatorListPage } from './pages/CreatorList'
import { LoginPage } from './pages/Login'
import { NotFoundPage } from './pages/NotFound'
import { TemplateDetailPage } from './pages/TemplateDetail'
import { TemplateListPage } from './pages/TemplateList'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/campaigns" replace />} />
        <Route path="/campaigns" element={<CampaignListPage />} />
        <Route path="/campaigns/:id" element={<CampaignDetailPage />} />
        <Route path="/creators" element={<CreatorListPage />} />
        <Route path="/creators/:id" element={<CreatorDetailPage />} />
        <Route path="/templates" element={<TemplateListPage />} />
        <Route path="/templates/:id" element={<TemplateDetailPage />} />
        <Route path="/audit-logs" element={<AuditLogListPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}

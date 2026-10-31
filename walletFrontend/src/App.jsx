import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthContext.jsx'
import ProtectedRoute from './components/layout/ProtectedRoute.jsx'
import AppShell from './components/layout/AppShell.jsx'

import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import HomePage from './pages/HomePage.jsx'
import SendMoneyPage from './pages/SendMoneyPage.jsx'
import TransactionsPage from './pages/TransactionsPage.jsx'
import TransactionDetailPage from './pages/TransactionDetailPage.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'
import WalletSettingsPage from './pages/WalletSettingsPage.jsx'

// Admin pages
import AdminDashboardPage from './pages/admin/AdminDashboardPage.jsx'
import AdminUsersPage from './pages/admin/AdminUsersPage.jsx'
import AdminUserDetailPage from './pages/admin/AdminUserDetailPage.jsx'
import AdminTransactionsPage from './pages/admin/AdminTransactionsPage.jsx'
import AdminTransactionDetailPage from './pages/admin/AdminTransactionDetailPage.jsx'
import AdminSettlementsPage from './pages/admin/AdminSettlementsPage.jsx'
import AdminFraudPage from './pages/admin/AdminFraudPage.jsx'
import AdminJobMonitorPage from './pages/admin/AdminJobMonitorPage.jsx'
import AdminReconciliationPage from './pages/admin/AdminReconciliationPage.jsx'
import AdminRegisterPage from './pages/admin/AdminRegisterPage.jsx'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* User routes */}
          <Route
            element={
              <ProtectedRoute role="USER">
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<HomePage />} />
            <Route path="/send" element={<SendMoneyPage />} />
            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/transactions/:id" element={<TransactionDetailPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/wallet/settings" element={<WalletSettingsPage />} />
          </Route>

          {/* Admin routes */}
          <Route
            element={
              <ProtectedRoute role="ADMIN">
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/admin" element={<AdminDashboardPage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/:userId" element={<AdminUserDetailPage />} />
            <Route path="/admin/transactions" element={<AdminTransactionsPage />} />
            <Route path="/admin/transactions/:id" element={<AdminTransactionDetailPage />} />
            <Route path="/admin/settlements" element={<AdminSettlementsPage />} />
            <Route path="/admin/fraud" element={<AdminFraudPage />} />
            <Route path="/admin/jobs" element={<AdminJobMonitorPage />} />
            <Route path="/admin/reconciliation" element={<AdminReconciliationPage />} />
            <Route path="/admin/register-admin" element={<AdminRegisterPage />} />
          </Route>

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

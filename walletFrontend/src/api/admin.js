import api from './axiosInstance.js'

export const adminApi = {
  // Dashboard
  dashboard: () => api.get('/admin/dashboard').then((r) => r.data),

  // Users
  users: (params) => api.get('/admin/users', { params }).then((r) => r.data),
  userDetail: (id) => api.get(`/admin/users/${id}`).then((r) => r.data),
  freezeWallet: (userId) => api.post(`/admin/users/${userId}/freeze`).then((r) => r.data),
  unfreezeWallet: (userId) => api.post(`/admin/users/${userId}/unfreeze`).then((r) => r.data),

  // Transactions
  transactions: (params) => api.get('/admin/transactions', { params }).then((r) => r.data),
  transactionDetail: (id) => api.get(`/admin/transactions/${id}`).then((r) => r.data),

  // Settlements
  settlements: () => api.get('/admin/settlements').then((r) => r.data),
  manualSettle: (id) => api.post(`/admin/transactions/${id}/settle`).then((r) => r.data),

  // Fraud
  fraudAlerts: (status = 'PENDING') =>
    api.get('/admin/fraud-alerts', { params: { status } }).then((r) => r.data),
  reviewFraudAlert: (alertId, data) =>
    api.post(`/admin/fraud-alerts/${alertId}/review`, data).then((r) => r.data),

  // Jobs
  jobs: (jobName) =>
    api.get('/admin/jobs', { params: jobName ? { jobName } : {} }).then((r) => r.data),

  // Reconciliation
  reconciliation: (date) =>
    api.get('/admin/reconciliation', { params: date ? { date } : {} }).then((r) => r.data),

  // Admin register (requires admin token)
  registerAdmin: (data) => api.post('/auth/register/admin', data).then((r) => r.data),
}

import api from './axiosInstance.js'

export const transactionsApi = {
  miniStatement: () => api.get('/transactions/mini-statement').then((r) => r.data),
  history: (params) => api.get('/transactions', { params }).then((r) => r.data),
  stats: () => api.get('/transactions/stats').then((r) => r.data),
  detail: (id) => api.get(`/transactions/${id}`).then((r) => r.data),
}

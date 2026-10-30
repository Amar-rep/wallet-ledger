import api from './axiosInstance.js'

export const walletApi = {
  get: () => api.get('/wallet').then((r) => r.data),
  addMoney: (amount) => api.post('/wallet/add-money', { amount }).then((r) => r.data),
  updateStatus: (status) => api.patch('/wallet/status', { status }).then((r) => r.data),
}

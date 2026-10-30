import api from './axiosInstance.js'

export const transfersApi = {
  searchUser: (query) => api.get('/users/search', { params: { query } }).then((r) => r.data),
  send: (data) => api.post('/transfers', data).then((r) => r.data),
}

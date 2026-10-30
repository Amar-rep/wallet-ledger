import api from './axiosInstance.js'

export const authApi = {
  register: (data) => api.post('/auth/register', data).then((r) => r.data),
  registerAdmin: (data) => api.post('/auth/register/admin', data).then((r) => r.data),
  login: (data) => api.post('/auth/login', data).then((r) => r.data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }).then((r) => r.data),
  me: () => api.get('/auth/me').then((r) => r.data),
}

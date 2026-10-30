import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// ── Request interceptor — attach access token ─────────────────────────────────
api.interceptors.request.use((config) => {
  const token = window.__accessToken || localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Token refresh state ───────────────────────────────────────────────────────
// Tracks an in-flight refresh so parallel 401s don't each trigger their own.
let isRefreshing = false
let refreshQueue = [] // [{ resolve, reject }]

function processQueue(error, token = null) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error)
    else resolve(token)
  })
  refreshQueue = []
}

function clearSession() {
  window.__accessToken = null
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
  window.location.href = '/login'
}

// ── Response interceptor — silent token refresh on 401 ───────────────────────
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config

    // Only attempt refresh on 401 and only once per request
    if (error.response?.status !== 401 || originalRequest._retried) {
      return Promise.reject(error)
    }

    // The refresh endpoint itself returned 401 → session is dead
    if (originalRequest.url?.includes('/auth/refresh')) {
      clearSession()
      return Promise.reject(error)
    }

    const storedRefreshToken = localStorage.getItem('refreshToken')
    if (!storedRefreshToken) {
      clearSession()
      return Promise.reject(error)
    }

    originalRequest._retried = true

    if (isRefreshing) {
      // Another request is already refreshing — queue this one
      return new Promise((resolve, reject) => {
        refreshQueue.push({ resolve, reject })
      }).then((newToken) => {
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return api(originalRequest)
      })
    }

    isRefreshing = true

    try {
      // Call refresh directly with axios (not our `api` instance) to avoid
      // interceptor loops. Note: this endpoint is public so no auth header needed.
      const { data } = await axios.post('/api/auth/refresh', {
        refreshToken: storedRefreshToken,
      })

      const newAccess = data.accessToken
      const newRefresh = data.refreshToken

      // Persist new tokens
      window.__accessToken = newAccess
      localStorage.setItem('accessToken', newAccess)
      if (newRefresh) localStorage.setItem('refreshToken', newRefresh)

      processQueue(null, newAccess)

      originalRequest.headers.Authorization = `Bearer ${newAccess}`
      return api(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      clearSession()
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

export default api

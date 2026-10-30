/**
 * authService.js – thin wrapper over /api/auth endpoints
 */

const BASE = '/api/auth'

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : {}

  if (!res.ok) {
    const message =
      data.message ||
      (data.fieldErrors ? Object.values(data.fieldErrors).join(' • ') : null) ||
      `HTTP ${res.status}`
    throw new Error(message)
  }

  return data
}

/**
 * POST /api/auth/login
 * @param {{ email: string, password: string }} credentials
 * @returns {Promise<LoginResponse>}
 */
export function login(credentials) {
  return request('/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

/**
 * POST /api/auth/register
 * @param {{ name: string, email: string, phone: string, password: string, pin: string }} payload
 * @returns {Promise<LoginResponse>}
 */
export function register(payload) {
  return request('/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * POST /api/auth/refresh
 * @param {string} refreshToken
 * @returns {Promise<TokenRefreshResponse>}
 */
export function refresh(refreshToken) {
  return request('/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  })
}

/**
 * GET /api/auth/me  (requires Authorization header)
 * @param {string} accessToken
 * @returns {Promise<UserProfileResponse>}
 */
export function getMe(accessToken) {
  return request('/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}

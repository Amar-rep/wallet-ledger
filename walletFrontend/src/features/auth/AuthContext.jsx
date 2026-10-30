import { createContext, useContext, useState, useCallback } from 'react'
import { authApi } from '../../api/auth.js'

const AuthContext = createContext(null)

function loadPersistedSession() {
  try {
    const token = localStorage.getItem('accessToken')
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    if (token && user) return { token, user }
  } catch {}
  return null
}

export function AuthProvider({ children }) {
  const persisted = loadPersistedSession()
  const [token, setToken] = useState(persisted?.token || null)
  const [user, setUser] = useState(persisted?.user || null)

  // Keep the global variable in sync for the axios interceptor
  if (token) window.__accessToken = token

  const _applySession = useCallback((data) => {
    window.__accessToken = data.token
    setToken(data.token)
    setUser(data.user)
    localStorage.setItem('accessToken', data.token)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('user', JSON.stringify(data.user))
  }, [])

  const login = useCallback(async (credentials) => {
    const data = await authApi.login(credentials)
    _applySession(data)
    return data.user
  }, [_applySession])

  const register = useCallback(async (payload) => {
    const data = await authApi.register(payload)
    return data
  }, [])

  // Called by the admin registration page after creating a new admin account.
  // Does NOT log in as the new admin — the current admin stays logged in.
  const registerAdmin = useCallback(async (payload) => {
    const data = await authApi.registerAdmin(payload)
    return data
  }, [])

  const logout = useCallback(() => {
    window.__accessToken = null
    setToken(null)
    setUser(null)
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }, [])

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        login,
        register,
        registerAdmin,
        logout,
        isAuthenticated: !!token,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

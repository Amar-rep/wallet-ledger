import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/AuthContext.jsx'

export default function ProtectedRoute({ children, role }) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (role && user?.role !== role) {
    // ADMIN going to /  → redirect to /admin; USER going to /admin → redirect to /
    return <Navigate to={user?.role === 'ADMIN' ? '/admin' : '/'} replace />
  }

  return children
}

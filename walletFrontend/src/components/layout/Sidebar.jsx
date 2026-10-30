import { NavLink } from 'react-router-dom'
import {
  Home, Send, List, Bell, Settings, Shield, Users, BarChart2, Layers, AlertTriangle,
  Activity, ClipboardList, UserPlus, X,
} from 'lucide-react'
import { useAuth } from '../../features/auth/AuthContext.jsx'

const userNav = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/send', label: 'Send Money', icon: Send },
  { to: '/transactions', label: 'Transactions', icon: List },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/wallet/settings', label: 'Wallet Settings', icon: Settings },
]

const adminNav = [
  { to: '/admin', label: 'Dashboard', icon: BarChart2, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/transactions', label: 'Transactions', icon: List },
  { to: '/admin/settlements', label: 'Settlements', icon: Layers },
  { to: '/admin/fraud', label: 'Fraud Review', icon: AlertTriangle },
  { to: '/admin/jobs', label: 'Job Monitor', icon: Activity },
  { to: '/admin/reconciliation', label: 'Reconciliation', icon: ClipboardList },
]

const adminManagementNav = [
  { to: '/admin/register-admin', label: 'Add Admin', icon: UserPlus },
]

function NavItem({ to, label, icon: Icon, end }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
          isActive
            ? 'bg-primary-600 text-white'
            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
        }`
      }
    >
      <Icon className="w-4 h-4 flex-shrink-0" aria-hidden />
      {label}
    </NavLink>
  )
}

export default function Sidebar({ open, onClose }) {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const nav = isAdmin ? adminNav : userNav

  return (
    <>
      {/* Mobile overlay */}
      {open && (
        <div
          className="fixed inset-0 bg-black/40 z-30 lg:hidden"
          onClick={onClose}
          aria-hidden
        />
      )}

      <aside
        className={`fixed top-0 left-0 h-full w-60 bg-white border-r border-slate-200 z-40 flex flex-col transition-transform duration-200
          ${open ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0 lg:static lg:flex`}
        aria-label="Main navigation"
      >
        {/* Logo */}
        <div className="h-16 flex items-center px-5 border-b border-slate-200 gap-2">
          <div className="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center">
            <Shield className="w-4 h-4 text-white" aria-hidden />
          </div>
          <span className="text-lg font-bold text-slate-900">PayLedger</span>
          <button
            onClick={onClose}
            className="ml-auto lg:hidden p-1.5 rounded-lg text-slate-400 hover:bg-slate-100"
            aria-label="Close menu"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Role badge */}
        <div className="px-5 py-3">
          {isAdmin && (
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800">
              Admin
            </span>
          )}
        </div>

        {/* Nav */}
        <nav className="flex-1 px-3 pb-6 space-y-0.5 overflow-y-auto">
          {nav.map((item) => (
            <NavItem key={item.to} {...item} />
          ))}
          {isAdmin && (
            <>
              <div className="my-2 border-t border-slate-100" />
              <p className="px-3 pb-1 text-xs font-medium text-slate-400 uppercase tracking-wide">
                Management
              </p>
              {adminManagementNav.map((item) => (
                <NavItem key={item.to} {...item} />
              ))}
            </>
          )}
        </nav>
      </aside>
    </>
  )
}

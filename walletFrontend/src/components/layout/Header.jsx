import { useState, useRef, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Bell, LogOut, User, ChevronDown, Menu } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../../api/notifications.js'
import { useAuth } from '../../features/auth/AuthContext.jsx'
import { formatDateTimeIST } from '../../lib/formatters.js'

function NotificationDot({ count }) {
  if (!count) return null
  return (
    <span
      aria-label={`${count > 9 ? '9+' : count} unread notifications`}
      className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center"
    >
      {count > 9 ? '9+' : count}
    </span>
  )
}

export default function Header({ onMenuClick }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [notifOpen, setNotifOpen] = useState(false)
  const [userOpen, setUserOpen] = useState(false)
  const notifRef = useRef(null)
  const userRef = useRef(null)

  const { data: unreadData } = useQuery({
    queryKey: ['unread-count'],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 30_000,
  })

  const { data: latestNotifs } = useQuery({
    queryKey: ['notifications-latest'],
    queryFn: () => notificationsApi.list({ page: 0, size: 5 }),
    enabled: notifOpen,
  })

  const markAllRead = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['unread-count'] })
      qc.invalidateQueries({ queryKey: ['notifications-latest'] })
    },
  })

  // close dropdowns on outside click
  useEffect(() => {
    function handler(e) {
      if (!notifRef.current?.contains(e.target)) setNotifOpen(false)
      if (!userRef.current?.contains(e.target)) setUserOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  function handleLogout() {
    logout()
    navigate('/login')
  }

  const unread = unreadData?.unreadCount || 0

  return (
    <header className="h-16 bg-white border-b border-slate-200 flex items-center px-4 gap-3 z-30 sticky top-0">
      <button
        onClick={onMenuClick}
        className="lg:hidden p-2 rounded-lg text-slate-500 hover:bg-slate-100"
        aria-label="Open menu"
      >
        <Menu className="w-5 h-5" />
      </button>

      <div className="flex-1" />

      {/* Notifications */}
      <div ref={notifRef} className="relative">
        <button
          onClick={() => setNotifOpen((v) => !v)}
          aria-label="Notifications"
          aria-expanded={notifOpen}
          className="relative p-2 rounded-lg text-slate-500 hover:bg-slate-100 transition-colors"
        >
          <Bell className="w-5 h-5" />
          <NotificationDot count={unread} />
        </button>

        {notifOpen && (
          <div className="absolute right-0 mt-2 w-80 bg-white rounded-xl shadow-lg border border-slate-200 z-50">
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
              <span className="font-semibold text-sm text-slate-800">Notifications</span>
              {unread > 0 && (
                <button
                  onClick={() => markAllRead.mutate()}
                  className="text-xs text-primary-600 hover:underline"
                >
                  Mark all read
                </button>
              )}
            </div>
            <ul aria-live="polite" className="divide-y divide-slate-50 max-h-72 overflow-y-auto">
              {latestNotifs?.content?.length ? (
                latestNotifs.content.map((n) => (
                  <li
                    key={n.id}
                    className={`px-4 py-3 text-sm transition-colors hover:bg-slate-50 cursor-pointer ${!n.read ? 'bg-primary-50' : ''}`}
                    onClick={() => {
                      setNotifOpen(false)
                      if (n.transactionId) navigate(`/transactions/${n.transactionId}`)
                    }}
                  >
                    <p className="text-slate-800 font-medium leading-snug">{n.message}</p>
                    <p className="text-slate-500 text-xs mt-0.5">{formatDateTimeIST(n.createdAt)}</p>
                  </li>
                ))
              ) : (
                <li className="px-4 py-8 text-center text-slate-500 text-sm">No notifications yet</li>
              )}
            </ul>
            <div className="px-4 py-3 border-t border-slate-100">
              <Link
                to="/notifications"
                className="text-xs text-primary-600 hover:underline"
                onClick={() => setNotifOpen(false)}
              >
                View all notifications →
              </Link>
            </div>
          </div>
        )}
      </div>

      {/* User menu */}
      <div ref={userRef} className="relative">
        <button
          onClick={() => setUserOpen((v) => !v)}
          aria-expanded={userOpen}
          aria-label="User menu"
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-slate-700 hover:bg-slate-100 transition-colors"
        >
          <div className="w-7 h-7 rounded-full bg-primary-600 text-white text-xs font-bold flex items-center justify-center">
            {user?.name?.[0]?.toUpperCase() || 'U'}
          </div>
          <span className="hidden sm:block text-sm font-medium">{user?.name}</span>
          <ChevronDown className="w-4 h-4 text-slate-400" />
        </button>

        {userOpen && (
          <div className="absolute right-0 mt-2 w-44 bg-white rounded-xl shadow-lg border border-slate-200 z-50 py-1">
            <div className="px-4 py-2 border-b border-slate-100">
              <p className="text-xs font-medium text-slate-800 truncate">{user?.name}</p>
              <p className="text-xs text-slate-500 truncate">{user?.email}</p>
            </div>
            <button
              onClick={handleLogout}
              className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors"
            >
              <LogOut className="w-4 h-4" /> Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  )
}

import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AlertTriangle, Info } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import Sidebar from './Sidebar.jsx'
import Header from './Header.jsx'
import { walletApi } from '../../api/wallet.js'
import { useAuth } from '../../features/auth/AuthContext.jsx'

function WalletStatusBanner({ status }) {
  if (!status || status === 'ACTIVE') return null

  const isFrozen = status === 'FROZEN'
  return (
    <div
      role="alert"
      aria-live="polite"
      className={`flex items-center gap-3 px-5 py-3 text-sm font-medium border-b ${
        isFrozen
          ? 'bg-blue-50 text-blue-800 border-blue-200'
          : 'bg-amber-50 text-amber-800 border-amber-200'
      }`}
    >
      {isFrozen ? <AlertTriangle className="w-4 h-4 flex-shrink-0" /> : <Info className="w-4 h-4 flex-shrink-0" />}
      {isFrozen
        ? 'Your wallet is frozen by an administrator. Transfers are disabled. Contact support.'
        : <>Your wallet is inactive. Transfers are disabled.&nbsp;
          <a href="/wallet/settings" className="underline font-semibold">Reactivate</a>
        </>
      }
    </div>
  )
}

export default function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { user } = useAuth()
  const isUser = user?.role === 'USER'

  const { data: wallet } = useQuery({
    queryKey: ['wallet'],
    queryFn: walletApi.get,
    enabled: isUser,
    staleTime: 60_000,
  })

  return (
    <div className="flex h-screen overflow-hidden bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <Header onMenuClick={() => setSidebarOpen(true)} />
        {isUser && <WalletStatusBanner status={wallet?.status} />}
        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

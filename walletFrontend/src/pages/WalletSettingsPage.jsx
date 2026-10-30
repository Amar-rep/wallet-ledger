import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, AlertTriangle, Snowflake } from 'lucide-react'
import { toast } from 'sonner'
import { walletApi } from '../api/wallet.js'
import { formatINR } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import { useAuth } from '../features/auth/AuthContext.jsx'
import Card from '../components/ui/Card.jsx'
import Button from '../components/ui/Button.jsx'
import Badge from '../components/ui/Badge.jsx'
import { SkeletonCard } from '../components/ui/Skeleton.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'

const STATUS_INFO = {
  ACTIVE:   { icon: CheckCircle2,  color: 'text-green-600', bg: 'bg-green-50', desc: 'Your wallet is active. All features are available.' },
  INACTIVE: { icon: AlertTriangle, color: 'text-amber-600', bg: 'bg-amber-50', desc: 'Your wallet is inactive. You cannot send or receive money.' },
  FROZEN:   { icon: Snowflake,     color: 'text-blue-600',  bg: 'bg-blue-50',  desc: 'Your wallet has been frozen by an administrator. Contact support to resolve this.' },
}

export default function WalletSettingsPage() {
  const { user } = useAuth()
  const qc = useQueryClient()

  const { data: wallet, isLoading, error, refetch } = useQuery({
    queryKey: ['wallet'],
    queryFn: walletApi.get,
  })

  const activate = useMutation({
    mutationFn: () => walletApi.updateStatus('ACTIVE'),
    onSuccess: (updated) => {
      qc.setQueryData(['wallet'], updated)
      toast.success('Wallet reactivated successfully.')
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  const deactivate = useMutation({
    mutationFn: () => walletApi.updateStatus('INACTIVE'),
    onSuccess: (updated) => {
      qc.setQueryData(['wallet'], updated)
      toast.success('Wallet deactivated.')
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  if (isLoading) return <div className="max-w-lg mx-auto"><SkeletonCard lines={5} /></div>
  if (error) return <div className="max-w-lg mx-auto"><ErrorState message={parseApiError(error).message} onRetry={refetch} /></div>

  const { icon: Icon, color, bg, desc } = STATUS_INFO[wallet.status] || STATUS_INFO.ACTIVE
  const isFrozen = wallet.status === 'FROZEN'

  return (
    <div className="max-w-lg mx-auto space-y-5">
      <h1 className="text-xl font-bold text-slate-900">Wallet settings</h1>

      {/* Status card */}
      <Card className="p-5">
        <div className="flex items-center gap-3 mb-4">
          <div className={`w-10 h-10 rounded-lg ${bg} flex items-center justify-center`}>
            <Icon className={`w-5 h-5 ${color}`} />
          </div>
          <div>
            <p className="font-semibold text-slate-800">Wallet status</p>
            <Badge status={wallet.status} />
          </div>
        </div>
        <p className="text-sm text-slate-600 mb-4">{desc}</p>

        {!isFrozen && (
          wallet.status === 'INACTIVE' ? (
            <Button onClick={() => activate.mutate()} loading={activate.isPending}>
              Reactivate wallet
            </Button>
          ) : (
            <Button
              variant="secondary"
              onClick={() => deactivate.mutate()}
              loading={deactivate.isPending}
            >
              Deactivate wallet
            </Button>
          )
        )}
      </Card>

      {/* Wallet info */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Wallet info</h2>
        {[
          ['Wallet ID', `#${wallet.walletId}`],
          ['Available balance', formatINR(wallet.availableBalance)],
          ["Today's spending", formatINR(wallet.todaySpending)],
          ['Daily limit', formatINR(50000)],
          ['Account name', user?.name],
          ['Email', user?.email],
        ].map(([k, v]) => (
          <div key={k} className="flex justify-between px-4 py-3 border-b border-slate-100 last:border-0 text-sm">
            <span className="text-slate-500">{k}</span>
            <span className="font-medium text-slate-800">{v}</span>
          </div>
        ))}
      </Card>
    </div>
  )
}

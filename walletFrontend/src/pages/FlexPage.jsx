import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Zap, ShieldCheck, AlertTriangle, CheckCircle2, Clock, RefreshCw, Info,
} from 'lucide-react'
import { toast } from 'sonner'
import { flexApi } from '../api/flex.js'
import { walletApi } from '../api/wallet.js'
import { formatINR, formatDateTimeIST } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Card from '../components/ui/Card.jsx'
import Badge from '../components/ui/Badge.jsx'
import Button from '../components/ui/Button.jsx'
import { SkeletonCard } from '../components/ui/Skeleton.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'
import EmptyState from '../components/ui/EmptyState.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../components/ui/Table.jsx'

const STATUS_STYLE = {
  OPEN:       'bg-blue-100 text-blue-700',
  REPAID:     'bg-green-100 text-green-700',
  RECOVERING: 'bg-amber-100 text-amber-700',
  RECOVERED:  'bg-green-100 text-green-700',
  WRITTEN_OFF:'bg-slate-100 text-slate-500',
}

const STATUS_ICON = {
  OPEN:        <Clock className="w-3 h-3" />,
  REPAID:      <CheckCircle2 className="w-3 h-3" />,
  RECOVERING:  <AlertTriangle className="w-3 h-3" />,
  RECOVERED:   <CheckCircle2 className="w-3 h-3" />,
  WRITTEN_OFF: <ShieldCheck className="w-3 h-3" />,
}

function FlexStatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLE[status] || 'bg-slate-100 text-slate-600'}`}>
      {STATUS_ICON[status]}
      {status?.replace('_', ' ')}
    </span>
  )
}

export default function FlexPage() {
  const qc = useQueryClient()

  const { data: wallet, isLoading: walletLoading, error: walletError, refetch: refetchWallet } = useQuery({
    queryKey: ['wallet'],
    queryFn: walletApi.get,
    staleTime: 30_000,
  })

  const { data: credits = [], isLoading: creditsLoading } = useQuery({
    queryKey: ['flex-credits'],
    queryFn: flexApi.myCredits,
    staleTime: 30_000,
  })

  const toggleMutation = useMutation({
    mutationFn: walletApi.toggleFlex,
    onSuccess: (res) => {
      toast.success(res.message)
      qc.invalidateQueries({ queryKey: ['wallet'] })
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  if (walletLoading) return (
    <div className="space-y-4 max-w-2xl">
      <SkeletonCard lines={4} />
      <SkeletonCard lines={6} />
    </div>
  )
  if (walletError) return <ErrorState message={parseApiError(walletError).message} onRetry={refetchWallet} />

  const flexEnabled = wallet?.flexEnabled ?? false
  const flexDebt = parseFloat(wallet?.flexDebt ?? 0)
  const hasDebt = flexDebt > 0

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-xl font-bold text-slate-900 flex items-center gap-2">
          <Zap className="w-5 h-5 text-primary-600" /> Flex Instant Credit
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Receive money instantly when a sender transfers to you — instead of waiting the standard 3-day settlement.
        </p>
      </div>

      {/* Opt-in card */}
      <Card className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            <h2 className="font-semibold text-slate-800 mb-1">Flex Instant Credit</h2>
            <p className="text-sm text-slate-500 mb-3">
              When enabled, eligible incoming transfers are credited to your wallet immediately.
              The platform's float covers the amount until the original transfer settles in 3 days.
            </p>

            {/* Rules */}
            <ul className="text-xs text-slate-500 space-y-1 mb-4">
              {[
                'Only transfers up to ₹5,000 qualify.',
                'You must have zero outstanding Flex debt to receive a new credit.',
                'If the original transfer fails, the credited amount is recovered from your balance.',
                'Any unrecovered amount becomes Flex debt, repaid automatically from future incoming funds.',
              ].map((r) => (
                <li key={r} className="flex items-start gap-1.5">
                  <Info className="w-3 h-3 mt-0.5 flex-shrink-0 text-slate-400" />
                  {r}
                </li>
              ))}
            </ul>

            <Button
              loading={toggleMutation.isPending}
              variant={flexEnabled ? 'secondary' : 'primary'}
              onClick={() => toggleMutation.mutate()}
            >
              <Zap className="w-4 h-4" />
              {flexEnabled ? 'Disable Flex' : 'Enable Flex'}
            </Button>
          </div>

          {/* Status indicator */}
          <div className={`w-16 h-16 rounded-2xl flex items-center justify-center flex-shrink-0 ${
            flexEnabled ? 'bg-primary-100' : 'bg-slate-100'
          }`}>
            <Zap className={`w-7 h-7 ${flexEnabled ? 'text-primary-600' : 'text-slate-400'}`} />
          </div>
        </div>

        {flexEnabled && (
          <div className="mt-4 pt-4 border-t border-slate-100 flex items-center gap-2 text-sm text-green-700 bg-green-50 rounded-lg px-3 py-2">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            Flex is active — you'll receive eligible transfers instantly.
          </div>
        )}
      </Card>

      {/* Debt banner */}
      {hasDebt && (
        <Card className="p-4 border-l-4 border-l-amber-500 bg-amber-50/40">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-amber-900 text-sm">Outstanding Flex debt: {formatINR(flexDebt)}</p>
              <p className="text-xs text-amber-800 mt-0.5">
                This will be automatically deducted from your next incoming transfer or top-up.
                New Flex credits are paused until your debt is cleared.
              </p>
            </div>
          </div>
        </Card>
      )}

      {/* Flex credit history */}
      <Card className="overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-800">Flex credit history</h2>
          <Button size="sm" variant="ghost" onClick={() => qc.invalidateQueries({ queryKey: ['flex-credits'] })}>
            <RefreshCw className="w-3.5 h-3.5" />
          </Button>
        </div>

        {creditsLoading ? (
          <div className="p-4 space-y-2">
            {[1, 2, 3].map((i) => <div key={i} className="h-8 bg-slate-100 rounded animate-pulse" />)}
          </div>
        ) : credits.length === 0 ? (
          <EmptyState icon={Zap} title="No Flex credits yet"
            description="Enable Flex and receive an eligible transfer to see history here." />
        ) : (
          <>
            {/* Mobile cards */}
            <div className="divide-y divide-slate-100 sm:hidden">
              {credits.map((c) => (
                <div key={c.id} className="px-4 py-3 space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold text-slate-900">{formatINR(c.amount)}</span>
                    <FlexStatusBadge status={c.status} />
                  </div>
                  <div className="text-xs text-slate-500">
                    Disbursed {formatDateTimeIST(c.disbursedAt)}
                  </div>
                  {c.amountOutstanding > 0 && (
                    <div className="text-xs text-amber-700">
                      Outstanding: {formatINR(c.amountOutstanding)}
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* Desktop table */}
            <div className="hidden sm:block">
              <Table>
                <Thead>
                  <tr>
                    <Th>Amount</Th>
                    <Th>Status</Th>
                    <Th>Recovered</Th>
                    <Th>Outstanding</Th>
                    <Th>Disbursed</Th>
                    <Th>Settled / Recovered</Th>
                  </tr>
                </Thead>
                <Tbody>
                  {credits.map((c) => (
                    <Tr key={c.id}>
                      <Td className="font-semibold">{formatINR(c.amount)}</Td>
                      <Td><FlexStatusBadge status={c.status} /></Td>
                      <Td className="text-slate-600">{formatINR(c.amountRecovered)}</Td>
                      <Td className={c.amountOutstanding > 0 ? 'text-amber-700 font-medium' : 'text-slate-400'}>
                        {formatINR(c.amountOutstanding)}
                      </Td>
                      <Td className="text-slate-500 text-xs">{formatDateTimeIST(c.disbursedAt)}</Td>
                      <Td className="text-slate-500 text-xs">
                        {c.settledAt
                          ? formatDateTimeIST(c.settledAt)
                          : c.recoveredAt
                          ? formatDateTimeIST(c.recoveredAt)
                          : '—'}
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

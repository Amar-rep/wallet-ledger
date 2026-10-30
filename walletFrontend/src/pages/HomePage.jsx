import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowUpRight, ArrowDownLeft, Send, Plus, TrendingUp, TrendingDown, Calendar } from 'lucide-react'
import { toast } from 'sonner'
import { walletApi } from '../api/wallet.js'
import { transactionsApi } from '../api/transactions.js'
import { formatINR, formatDateOnly, formatDateTimeIST, DAILY_LIMIT } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Card from '../components/ui/Card.jsx'
import Badge from '../components/ui/Badge.jsx'
import Button from '../components/ui/Button.jsx'
import Modal from '../components/ui/Modal.jsx'
import Input from '../components/ui/Input.jsx'
import StatCard from '../components/ui/StatCard.jsx'
import { SkeletonCard } from '../components/ui/Skeleton.jsx'
import EmptyState from '../components/ui/EmptyState.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'

const addMoneySchema = z.object({
  amount: z
    .string()
    .min(1, 'Amount is required')
    .refine((v) => !isNaN(parseFloat(v)) && parseFloat(v) >= 1, 'Minimum ₹1'),
})

const QUICK_AMOUNTS = [500, 1000, 2000, 5000]

function AddMoneyModal({ open, onClose, wallet }) {
  const qc = useQueryClient()
  const { register, handleSubmit, setValue, watch, formState: { errors }, reset } = useForm({
    resolver: zodResolver(addMoneySchema),
  })
  const amount = watch('amount', '')

  const mutation = useMutation({
    mutationFn: (amt) => walletApi.addMoney(parseFloat(amt).toFixed(2)),
    onSuccess: (updated) => {
      qc.setQueryData(['wallet'], updated)
      qc.invalidateQueries({ queryKey: ['mini-statement'] })
      toast.success(`${formatINR(updated.availableBalance)} — wallet updated`)
      reset()
      onClose()
    },
    onError: (err) => {
      const { message } = parseApiError(err)
      toast.error(message)
    },
  })

  return (
    <Modal open={open} onClose={onClose} title="Add money">
      <p className="text-sm text-slate-500 mb-4">This is a simulated top-up for demo purposes.</p>
      <div className="flex flex-wrap gap-2 mb-4">
        {QUICK_AMOUNTS.map((a) => (
          <button
            key={a}
            type="button"
            onClick={() => setValue('amount', String(a))}
            className={`px-3 py-1.5 rounded-lg border text-sm font-medium transition-colors ${
              amount === String(a)
                ? 'border-primary-600 bg-primary-50 text-primary-700'
                : 'border-slate-200 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {formatINR(a)}
          </button>
        ))}
      </div>
      <form
        onSubmit={handleSubmit((d) => mutation.mutate(d.amount))}
        className="space-y-4"
      >
        <Input
          label="Custom amount"
          type="number"
          min="1"
          step="0.01"
          prefix="₹"
          placeholder="0.00"
          error={errors.amount?.message}
          {...register('amount')}
        />
        <Button type="submit" loading={mutation.isPending} className="w-full">
          Add money
        </Button>
      </form>
    </Modal>
  )
}

function WalletCard({ wallet, onAddMoney }) {
  const spending = parseFloat(wallet.todaySpending) || 0
  const pct = Math.min((spending / DAILY_LIMIT) * 100, 100)
  const nearLimit = pct >= 80

  return (
    <div className="rounded-2xl bg-gradient-to-br from-primary-600 to-primary-800 text-white p-6 shadow-lg">
      <div className="flex items-start justify-between mb-2">
        <div>
          <p className="text-primary-200 text-sm font-medium">Available balance</p>
          <p className="text-4xl font-bold mt-1">{formatINR(wallet.availableBalance)}</p>
        </div>
        <Badge status={wallet.status} className="mt-1" />
      </div>

      <div className="mt-5">
        <div className="flex justify-between text-xs text-primary-200 mb-1">
          <span>Today's spending: {formatINR(spending)}</span>
          <span className={nearLimit ? 'text-amber-300 font-medium' : ''}>
            Limit: {formatINR(DAILY_LIMIT)}
          </span>
        </div>
        <div className="h-1.5 rounded-full bg-white/20">
          <div
            className={`h-full rounded-full transition-all ${nearLimit ? 'bg-amber-400' : 'bg-white/70'}`}
            style={{ width: `${pct}%` }}
            role="progressbar"
            aria-valuenow={Math.round(pct)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Daily spending"
          />
        </div>
        {nearLimit && (
          <p className="text-xs text-amber-300 mt-1">Above 80% of daily limit</p>
        )}
      </div>

      <div className="mt-4 flex gap-2">
        <button
          onClick={onAddMoney}
          className="flex items-center gap-2 px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg text-sm font-medium transition-colors"
        >
          <Plus className="w-4 h-4" /> Add money
        </button>
      </div>
    </div>
  )
}

function MiniStatementRow({ txn }) {
  const navigate = useNavigate()
  const isSent = txn.direction === 'SENT'
  return (
    <button
      className="w-full flex items-center gap-3 px-4 py-3 hover:bg-slate-50 transition-colors text-left"
      onClick={() => navigate(`/transactions/${txn.id}`)}
    >
      <div className={`w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0 ${isSent ? 'bg-red-50' : 'bg-green-50'}`}>
        {isSent
          ? <ArrowUpRight className="w-4 h-4 text-red-500" aria-hidden />
          : <ArrowDownLeft className="w-4 h-4 text-green-600" aria-hidden />
        }
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-800 truncate">{txn.counterpartyName || 'Unknown'}</p>
        {txn.status === 'PENDING' && txn.settlementDueDate && (
          <p className="text-xs text-slate-400">
            Settles {formatDateOnly(txn.settlementDueDate)}
            {txn.daysRemaining != null ? ` · ${txn.daysRemaining} day${txn.daysRemaining !== 1 ? 's' : ''} left` : ''}
          </p>
        )}
        {txn.status !== 'PENDING' && (
          <p className="text-xs text-slate-400">{formatDateTimeIST(txn.initiatedAt)}</p>
        )}
      </div>
      <div className="text-right flex-shrink-0">
        <p className={`text-sm font-semibold ${isSent ? 'text-red-600' : 'text-green-600'}`}>
          {isSent ? '−' : '+'}{formatINR(txn.amount)}
        </p>
        <Badge status={txn.status} />
      </div>
    </button>
  )
}

export default function HomePage() {
  const [addMoneyOpen, setAddMoneyOpen] = useState(false)

  const { data: wallet, isLoading: walletLoading, error: walletError, refetch: refetchWallet } = useQuery({
    queryKey: ['wallet'],
    queryFn: walletApi.get,
  })

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['txn-stats'],
    queryFn: transactionsApi.stats,
  })

  const { data: mini, isLoading: miniLoading, error: miniError, refetch: refetchMini } = useQuery({
    queryKey: ['mini-statement'],
    queryFn: transactionsApi.miniStatement,
  })

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Wallet card */}
      {walletLoading ? (
        <SkeletonCard lines={4} />
      ) : walletError ? (
        <ErrorState message="Could not load wallet" onRetry={refetchWallet} />
      ) : wallet ? (
        <WalletCard wallet={wallet} onAddMoney={() => setAddMoneyOpen(true)} />
      ) : null}

      {/* Stats row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          label="Total sent"
          value={formatINR(stats?.totalSent ?? 0)}
          icon={TrendingDown}
          loading={statsLoading}
          colorClass="text-red-500"
        />
        <StatCard
          label="Total received"
          value={formatINR(stats?.totalReceived ?? 0)}
          icon={TrendingUp}
          loading={statsLoading}
          colorClass="text-green-600"
        />
        <StatCard
          label="This month"
          value={`${stats?.thisMonthTransactionCount ?? 0} txns`}
          icon={Calendar}
          loading={statsLoading}
        />
      </div>

      {/* Send money CTA */}
      <Link to="/send">
        <Button className="w-full" size="lg">
          <Send className="w-4 h-4" /> Send money
        </Button>
      </Link>

      {/* Mini statement */}
      <Card>
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
          <h2 className="font-semibold text-slate-800">Recent transactions</h2>
          <Link to="/transactions" className="text-xs text-primary-600 hover:underline">
            View all
          </Link>
        </div>
        {miniLoading ? (
          <div className="divide-y divide-slate-100">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-3" aria-hidden>
                <div className="w-9 h-9 rounded-full bg-slate-200 animate-pulse" />
                <div className="flex-1 space-y-1">
                  <div className="h-3 bg-slate-200 rounded animate-pulse w-1/2" />
                  <div className="h-2 bg-slate-200 rounded animate-pulse w-1/3" />
                </div>
              </div>
            ))}
          </div>
        ) : miniError ? (
          <ErrorState message="Could not load transactions" onRetry={refetchMini} />
        ) : mini?.length ? (
          <div className="divide-y divide-slate-100">
            {mini.map((txn) => <MiniStatementRow key={txn.id} txn={txn} />)}
          </div>
        ) : (
          <EmptyState
            icon={Send}
            title="No transactions yet"
            description="Send money to someone to get started."
          />
        )}
      </Card>

      <AddMoneyModal open={addMoneyOpen} onClose={() => setAddMoneyOpen(false)} wallet={wallet} />
    </div>
  )
}

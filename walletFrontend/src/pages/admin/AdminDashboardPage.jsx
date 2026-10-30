import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import {
  Users, ArrowUpRight, ArrowDownRight, IndianRupee, Clock, AlertTriangle,
} from 'lucide-react'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateOnly } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import StatCard from '../../components/ui/StatCard.jsx'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'
import { SkeletonCard, Skeleton } from '../../components/ui/Skeleton.jsx'

function VolumeTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-white border border-slate-200 rounded-xl shadow-lg px-3 py-2 text-sm">
      <p className="text-slate-500 text-xs mb-0.5">{label}</p>
      <p className="font-semibold text-slate-900">{formatINR(payload[0].value)}</p>
    </div>
  )
}

function formatAxisINR(value) {
  if (value >= 100000) return `₹${(value / 100000).toFixed(1)}L`
  if (value >= 1000)   return `₹${(value / 1000).toFixed(0)}K`
  return `₹${value}`
}

export default function AdminDashboardPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: adminApi.dashboard,
    staleTime: 60_000,
  })

  const { data: fraudAlerts } = useQuery({
    queryKey: ['fraud-alerts-pending'],
    queryFn: () => adminApi.fraudAlerts('PENDING'),
    staleTime: 60_000,
  })

  const { data: settlements } = useQuery({
    queryKey: ['admin-settlements'],
    queryFn: adminApi.settlements,
    staleTime: 60_000,
  })

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-bold text-slate-900">Dashboard</h1>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => <SkeletonCard key={i} lines={3} />)}
        </div>
        <SkeletonCard lines={8} />
      </div>
    )
  }

  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch} />

  const chartData = (data?.last30DaysVolume || []).map((p) => ({
    date: formatDateOnly(p.date).replace(/\d{4}$/, '').trim().replace(/,$/, ''),
    volume: parseFloat(p.volume) || 0,
  }))

  const todayDue   = settlements?.pendingTransactions?.filter((t) => {
    const today = new Date().toISOString().slice(0, 10)
    return t.settlementDueDate === today
  }) || []

  const pendingFraud = fraudAlerts || []

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-slate-900">Dashboard</h1>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard label="Total users"        value={data.totalUsers.toLocaleString('en-IN')} icon={Users} />
        <StatCard label="Initiated today"    value={data.todayTransfersInitiated.toLocaleString('en-IN')} icon={ArrowUpRight} />
        <StatCard label="Settled today"      value={data.todayTransfersSettled.toLocaleString('en-IN')} icon={ArrowDownRight} colorClass="text-green-600" />
        <StatCard label="Today's volume"     value={formatINR(data.todayVolume)} icon={IndianRupee} />
        <StatCard label="Pending settlement" value={formatINR(data.totalPendingAmount)} icon={Clock} />
        <StatCard
          label="Pending fraud alerts"
          value={data.pendingFraudAlerts.toString()}
          icon={AlertTriangle}
          colorClass={data.pendingFraudAlerts > 0 ? 'text-red-600' : 'text-slate-400'}
          subtext={data.pendingFraudAlerts > 0 ? (
            <Link to="/admin/fraud" className="text-red-600 hover:underline text-xs font-medium">
              Review now →
            </Link>
          ) : null}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Volume chart */}
        <Card className="lg:col-span-2 p-5">
          <h2 className="font-semibold text-slate-800 mb-4">Daily transaction volume — last 30 days</h2>
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={chartData} margin={{ top: 4, right: 8, left: 8, bottom: 0 }}>
              <defs>
                <linearGradient id="volGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor="#4F46E5" stopOpacity={0.15} />
                  <stop offset="95%" stopColor="#4F46E5" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis
                dataKey="date"
                tick={{ fontSize: 10, fill: '#94a3b8' }}
                interval={4}
                tickLine={false}
                axisLine={false}
              />
              <YAxis
                tickFormatter={formatAxisINR}
                tick={{ fontSize: 10, fill: '#94a3b8' }}
                tickLine={false}
                axisLine={false}
                width={52}
              />
              <Tooltip content={<VolumeTooltip />} />
              <Area
                type="monotone"
                dataKey="volume"
                stroke="#4F46E5"
                strokeWidth={2}
                fill="url(#volGrad)"
                dot={false}
                activeDot={{ r: 4, fill: '#4F46E5' }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </Card>

        {/* Needs attention */}
        <Card className="p-5">
          <h2 className="font-semibold text-slate-800 mb-4">Needs attention</h2>

          {pendingFraud.length > 0 && (
            <div className="mb-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">
                Fraud alerts
              </p>
              <ul className="space-y-2">
                {pendingFraud.slice(0, 3).map((a) => (
                  <li key={a.id} className="flex items-center justify-between text-sm">
                    <span className="text-slate-700 truncate">{a.userName}</span>
                    <Badge status="PENDING">{a.alertType.replace('_', ' ')}</Badge>
                  </li>
                ))}
                {pendingFraud.length > 3 && (
                  <li>
                    <Link to="/admin/fraud" className="text-xs text-primary-600 hover:underline">
                      +{pendingFraud.length - 3} more →
                    </Link>
                  </li>
                )}
              </ul>
            </div>
          )}

          {todayDue.length > 0 && (
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">
                Settlements due today
              </p>
              <ul className="space-y-2">
                {todayDue.slice(0, 3).map((t) => (
                  <li key={t.id} className="flex items-center justify-between text-sm">
                    <Link
                      to={`/admin/transactions/${t.id}`}
                      className="text-slate-700 hover:text-primary-600 truncate"
                    >
                      #{t.id}
                    </Link>
                    <span className="font-medium text-slate-800">{formatINR(t.amount)}</span>
                  </li>
                ))}
                {todayDue.length > 3 && (
                  <li>
                    <Link to="/admin/settlements" className="text-xs text-primary-600 hover:underline">
                      View all settlements →
                    </Link>
                  </li>
                )}
              </ul>
            </div>
          )}

          {pendingFraud.length === 0 && todayDue.length === 0 && (
            <p className="text-sm text-slate-400">Nothing needs attention right now.</p>
          )}
        </Card>
      </div>
    </div>
  )
}

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  TrendingUp, TrendingDown, Users, AlertTriangle, Clock, CheckCircle2, XCircle,
} from 'lucide-react'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateOnly } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonRow, SkeletonCard } from '../../components/ui/Skeleton.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'

const LIMIT_OPTIONS = [7, 14, 30, 60, 90]

function SummaryCard({ label, value, sub, icon: Icon, colorClass }) {
  return (
    <Card className="p-4 flex items-start gap-3">
      <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${colorClass} bg-opacity-10`}>
        <Icon className={`w-4 h-4 ${colorClass}`} />
      </div>
      <div>
        <p className="text-xs text-slate-500">{label}</p>
        <p className="text-lg font-bold text-slate-900">{value}</p>
        {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
      </div>
    </Card>
  )
}

export default function AdminDailySummaryPage() {
  const [limit, setLimit] = useState(30)

  const { data: rows, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-daily-summaries', limit],
    queryFn: () => adminApi.dailySummaries(limit),
    staleTime: 5 * 60_000,
  })

  // Aggregate totals from loaded rows
  const totals = rows?.reduce(
    (acc, r) => ({
      initiated: acc.initiated + r.transfersInitiated,
      settled: acc.settled + r.transfersSettled,
      initiatedAmt: acc.initiatedAmt + parseFloat(r.initiatedAmount ?? 0),
      settledAmt: acc.settledAmt + parseFloat(r.settledAmount ?? 0),
      fraud: acc.fraud + r.fraudAlertsRaised,
      registrations: acc.registrations + r.newRegistrations,
      mismatches: acc.mismatches + (r.reconciliationOk ? 0 : 1),
    }),
    { initiated: 0, settled: 0, initiatedAmt: 0, settledAmt: 0, fraud: 0, registrations: 0, mismatches: 0 }
  )

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-xl font-bold text-slate-900">Daily Summary</h1>
        <div className="flex items-center gap-2">
          <span className="text-sm text-slate-500">Show last</span>
          <select
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
          >
            {LIMIT_OPTIONS.map((n) => (
              <option key={n} value={n}>{n} days</option>
            ))}
          </select>
        </div>
      </div>

      {/* Aggregate stat cards */}
      {isLoading ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
          {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} lines={2} />)}
        </div>
      ) : totals ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
          <SummaryCard
            label={`Transfers initiated (${limit}d)`}
            value={totals.initiated.toLocaleString()}
            sub={formatINR(totals.initiatedAmt)}
            icon={TrendingUp}
            colorClass="text-blue-600"
          />
          <SummaryCard
            label={`Transfers settled (${limit}d)`}
            value={totals.settled.toLocaleString()}
            sub={formatINR(totals.settledAmt)}
            icon={TrendingDown}
            colorClass="text-green-600"
          />
          <SummaryCard
            label={`Fraud alerts (${limit}d)`}
            value={totals.fraud.toLocaleString()}
            icon={AlertTriangle}
            colorClass="text-amber-600"
          />
          <SummaryCard
            label={`New users (${limit}d)`}
            value={totals.registrations.toLocaleString()}
            icon={Users}
            colorClass="text-primary-600"
          />
        </div>
      ) : null}

      {/* Table */}
      <Card>
        {isLoading ? (
          <Table>
            <Thead>
              <tr>
                <Th>Date</Th><Th>Initiated</Th><Th>Initiated amt</Th>
                <Th>Settled</Th><Th>Settled amt</Th><Th>Pending amt</Th>
                <Th>Fraud</Th><Th>New users</Th><Th>Recon</Th>
              </tr>
            </Thead>
            <tbody>
              {Array.from({ length: 10 }).map((_, i) => <SkeletonRow key={i} cols={9} />)}
            </tbody>
          </Table>
        ) : error ? (
          <ErrorState message={parseApiError(error).message} onRetry={refetch} />
        ) : !rows?.length ? (
          <EmptyState
            icon={Clock}
            title="No summaries yet"
            description="The daily summary job hasn't run yet. It runs automatically each night."
          />
        ) : (
          <Table>
            <Thead>
              <tr>
                <Th>Date</Th>
                <Th>Initiated</Th>
                <Th>Initiated amt</Th>
                <Th>Settled</Th>
                <Th>Settled amt</Th>
                <Th>Pending amt</Th>
                <Th>Fraud alerts</Th>
                <Th>New users</Th>
                <Th>Recon</Th>
              </tr>
            </Thead>
            <Tbody>
              {rows.map((r) => (
                <Tr key={r.summaryDate}>
                  <Td className="font-medium whitespace-nowrap">{formatDateOnly(r.summaryDate)}</Td>
                  <Td>{r.transfersInitiated}</Td>
                  <Td className="text-blue-700 font-medium">{formatINR(r.initiatedAmount)}</Td>
                  <Td>{r.transfersSettled}</Td>
                  <Td className="text-green-700 font-medium">{formatINR(r.settledAmount)}</Td>
                  <Td className="text-amber-700">{formatINR(r.pendingSettlementAmount)}</Td>
                  <Td>
                    {r.fraudAlertsRaised > 0 ? (
                      <span className="font-semibold text-red-600">{r.fraudAlertsRaised}</span>
                    ) : (
                      <span className="text-slate-400">0</span>
                    )}
                  </Td>
                  <Td>{r.newRegistrations}</Td>
                  <Td>
                    {r.reconciliationOk ? (
                      <span className="inline-flex items-center gap-1 text-green-700 text-xs font-medium">
                        <CheckCircle2 className="w-3.5 h-3.5" /> OK
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-red-600 text-xs font-semibold">
                        <XCircle className="w-3.5 h-3.5" /> Mismatch
                      </span>
                    )}
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>
    </div>
  )
}

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  RefreshCw, CheckCircle2, AlertTriangle, Calendar, IndianRupee,
} from 'lucide-react'
import { formatISO, startOfDay, parseISO } from 'date-fns'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateOnly } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Input from '../../components/ui/Input.jsx'
import { SkeletonCard } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

export default function AdminReconciliationPage() {
  const today = new Date()
  const [selectedDate, setSelectedDate] = useState(formatISO(startOfDay(today)).split('T')[0])

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-reconciliation', selectedDate],
    queryFn: () => adminApi.reconciliation(selectedDate),
    staleTime: 60_000,
  })

  const handleDateChange = (e) => {
    setSelectedDate(e.target.value)
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <h1 className="text-xl font-bold text-slate-900">Reconciliation</h1>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} lines={3} />)}
        </div>
        <SkeletonCard lines={6} />
      </div>
    )
  }

  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch} />

  const totals = data || {
    date: selectedDate,
    totalDebits: 0,
    totalCredits: 0,
    pendingSettlementAmount: 0,
    mismatch: false,
    difference: 0,
  }

  const difference = parseFloat(totals.totalCredits) - parseFloat(totals.totalDebits)
  const isBalanced = totals.mismatch === false && Math.abs(difference) < 0.01
  const hasMismatch = totals.mismatch

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold text-slate-900">Reconciliation</h1>

      {/* Date picker */}
      <Card className="p-4 border border-slate-200">
        <div className="flex items-center gap-3">
          <Calendar className="w-4 h-4 text-slate-400" />
          <label className="text-sm font-medium text-slate-700">Select date:</label>
          <input
            type="date"
            value={selectedDate}
            onChange={handleDateChange}
            className="px-3 py-2 text-sm border border-slate-200 rounded-lg outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
          <Button
            size="sm"
            variant="secondary"
            onClick={refetch}
            className="ml-auto"
          >
            <RefreshCw className="w-4 h-4" />
            Refresh
          </Button>
        </div>
      </Card>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Total debits</p>
              <p className="text-2xl font-bold text-slate-900 mt-1">{formatINR(totals.totalDebits)}</p>
              <p className="text-xs text-slate-400 mt-1">Money sent out</p>
            </div>
            <IndianRupee className="w-8 h-8 text-slate-300" />
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Total credits</p>
              <p className="text-2xl font-bold text-slate-900 mt-1">{formatINR(totals.totalCredits)}</p>
              <p className="text-xs text-slate-400 mt-1">Money received</p>
            </div>
            <IndianRupee className="w-8 h-8 text-slate-300" />
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Net difference</p>
              <p className={`text-2xl font-bold mt-1 ${isBalanced ? 'text-green-600' : 'text-orange-600'}`}>
                {formatINR(Math.abs(difference))}
              </p>
              <p className="text-xs text-slate-400 mt-1">
                {isBalanced ? '✓ Balanced' : difference > 0 ? 'Credit excess' : 'Debit excess'}
              </p>
            </div>
            {isBalanced ? (
              <CheckCircle2 className="w-8 h-8 text-green-300" />
            ) : (
              <AlertTriangle className="w-8 h-8 text-orange-300" />
            )}
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Difference</p>
              <p className={`text-2xl font-bold mt-1 ${isBalanced ? 'text-slate-400' : 'text-orange-600'}`}>
                {formatINR(Math.abs(totals.difference || 0))}
              </p>
              <p className="text-xs text-slate-400 mt-1">{isBalanced ? 'None' : 'Debit/credit gap'}</p>
            </div>
            <RefreshCw className="w-8 h-8 text-slate-300" />
          </div>
        </Card>
      </div>

      {/* Pending settlement card */}
      {totals.pendingSettlementAmount > 0 && (
        <Card className="p-5 border-l-4 border-l-blue-500 bg-blue-50/30">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="font-semibold text-slate-800 mb-1">Pending settlements</h3>
              <p className="text-sm text-slate-600 mb-2">
                Amount waiting to be settled from today's transactions:
              </p>
              <p className="text-2xl font-bold text-blue-600">
                {formatINR(totals.pendingSettlementAmount)}
              </p>
              <p className="text-xs text-slate-500 mt-1">
                Due in 3 days as per ACH schedule
              </p>
            </div>
            <Calendar className="w-8 h-8 text-blue-400 flex-shrink-0" />
          </div>
        </Card>
      )}

      {/* Mismatch alert */}
      {hasMismatch && (
        <Card className="p-5 border-l-4 border-l-red-500 bg-red-50/30">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-6 h-6 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <h3 className="font-semibold text-red-900 mb-1">Mismatch detected</h3>
              <p className="text-sm text-red-800 mb-2">
                {totals.mismatch
                  ? `Ledger imbalance detected: debits and credits differ by ${formatINR(Math.abs(totals.difference || 0))}.`
                  : 'An imbalance has been detected in today\'s ledger.'}
              </p>
              <div className="mt-3 flex gap-2">
                <Button size="sm" variant="secondary" className="text-red-600 hover:text-red-700">
                  View details
                </Button>
                <Button size="sm" variant="ghost" className="text-red-600 hover:text-red-700">
                  Contact support
                </Button>
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* Status message */}
      {!hasMismatch && isBalanced && (
        <Card className="p-5 border-l-4 border-l-green-500 bg-green-50/30">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="w-6 h-6 text-green-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-green-900 mb-1">All clear</h3>
              <p className="text-sm text-green-800">
                Ledger for {formatDateOnly(selectedDate)} is balanced. No mismatches detected.
              </p>
            </div>
          </div>
        </Card>
      )}

      {/* Details grid */}
      <Card className="p-5">
        <h2 className="font-semibold text-slate-800 mb-4">Summary for {formatDateOnly(selectedDate)}</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="border border-slate-100 rounded-lg p-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Debits</p>
            <p className="text-lg font-semibold text-slate-900">{formatINR(totals.totalDebits)}</p>
            <p className="text-xs text-slate-500 mt-1">Total amount sent</p>
          </div>
          <div className="border border-slate-100 rounded-lg p-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Credits</p>
            <p className="text-lg font-semibold text-slate-900">{formatINR(totals.totalCredits)}</p>
            <p className="text-xs text-slate-500 mt-1">Total amount received</p>
          </div>
          <div className="border border-slate-100 rounded-lg p-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Difference</p>
            <p className={`text-lg font-semibold ${isBalanced ? 'text-green-600' : 'text-orange-600'}`}>
              {isBalanced ? '₹0 (balanced)' : formatINR(Math.abs(totals.difference || 0))}
            </p>
            <p className="text-xs text-slate-500 mt-1">Debit minus credit</p>
          </div>
          <div className="border border-slate-100 rounded-lg p-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Pending</p>
            <p className="text-lg font-semibold text-slate-900">{formatINR(totals.pendingSettlementAmount)}</p>
            <p className="text-xs text-slate-500 mt-1">Awaiting settlement</p>
          </div>
        </div>
      </Card>
    </div>
  )
}

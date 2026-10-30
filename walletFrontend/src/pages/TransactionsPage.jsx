import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Filter, ArrowUpRight, ArrowDownLeft, X } from 'lucide-react'
import { transactionsApi } from '../api/transactions.js'
import { formatINR, formatDateTimeIST, formatDateOnly } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Card from '../components/ui/Card.jsx'
import Badge from '../components/ui/Badge.jsx'
import Button from '../components/ui/Button.jsx'
import Pagination from '../components/ui/Pagination.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../components/ui/Table.jsx'
import { SkeletonRow } from '../components/ui/Skeleton.jsx'
import EmptyState from '../components/ui/EmptyState.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'

const DIRECTION_OPTIONS = [
  { value: 'ALL', label: 'All' },
  { value: 'SENT', label: 'Sent' },
  { value: 'RECEIVED', label: 'Received' },
]
const STATUS_OPTIONS = [
  { value: 'ALL', label: 'All' },
  { value: 'SUCCESS', label: 'Success' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'DECLINED', label: 'Declined' },
]

function FilterBar({ params, onUpdate, onReset }) {
  return (
    <div className="flex flex-wrap gap-3 items-end p-4 border-b border-slate-100 bg-slate-50/50 rounded-t-xl">
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">From</label>
        <input
          type="date"
          value={params.fromDate || ''}
          onChange={(e) => onUpdate('fromDate', e.target.value || undefined)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">To</label>
        <input
          type="date"
          value={params.toDate || ''}
          onChange={(e) => onUpdate('toDate', e.target.value || undefined)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Direction</label>
        <select
          value={params.type || 'ALL'}
          onChange={(e) => onUpdate('type', e.target.value)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
          aria-label="Direction filter"
        >
          {DIRECTION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Status</label>
        <select
          value={params.status || 'ALL'}
          onChange={(e) => onUpdate('status', e.target.value)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
          aria-label="Status filter"
        >
          {STATUS_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>
      <Button variant="ghost" size="sm" onClick={onReset} className="flex items-center gap-1">
        <X className="w-3.5 h-3.5" /> Reset
      </Button>
    </div>
  )
}

// Mobile card for a single transaction
function TxnCard({ txn, onClick }) {
  const isSent = txn.direction === 'SENT'
  return (
    <button
      onClick={onClick}
      className="w-full text-left flex items-center gap-3 p-4 border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors"
    >
      <div className={`w-9 h-9 rounded-full flex items-center justify-center flex-shrink-0 ${isSent ? 'bg-red-50' : 'bg-green-50'}`}>
        {isSent ? <ArrowUpRight className="w-4 h-4 text-red-500" /> : <ArrowDownLeft className="w-4 h-4 text-green-600" />}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-slate-800 truncate">{txn.counterpartyName || '—'}</p>
        <p className="text-xs text-slate-400">{formatDateTimeIST(txn.initiatedAt)}</p>
        {txn.status === 'PENDING' && txn.settlementDueDate && (
          <p className="text-xs text-amber-600">Settles {formatDateOnly(txn.settlementDueDate)}</p>
        )}
      </div>
      <div className="text-right flex-shrink-0 space-y-1">
        <p className={`text-sm font-semibold ${isSent ? 'text-red-600' : 'text-green-600'}`}>
          {isSent ? '−' : '+'}{formatINR(txn.amount)}
        </p>
        <Badge status={txn.status} />
      </div>
    </button>
  )
}

export default function TransactionsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  function getParam(key, def) { return searchParams.get(key) || def }

  const page = parseInt(getParam('page', '0'))
  const size = parseInt(getParam('size', '10'))
  const filters = {
    fromDate: searchParams.get('fromDate') || undefined,
    toDate: searchParams.get('toDate') || undefined,
    type: searchParams.get('type') || 'ALL',
    status: searchParams.get('status') || 'ALL',
  }

  function updateParam(key, value) {
    const p = new URLSearchParams(searchParams)
    if (value === undefined || value === 'ALL' || value === '') {
      p.delete(key)
    } else {
      p.set(key, value)
    }
    p.set('page', '0')
    setSearchParams(p)
  }

  function resetFilters() {
    setSearchParams({ page: '0', size: String(size) })
  }

  const queryParams = {
    ...filters,
    type: filters.type === 'ALL' ? undefined : filters.type,
    status: filters.status === 'ALL' ? undefined : filters.status,
    page,
    size,
  }

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['transactions', queryParams],
    queryFn: () => transactionsApi.history(queryParams),
    keepPreviousData: true,
  })

  const txns = data?.content || []

  return (
    <div className="max-w-5xl mx-auto space-y-4">
      <h1 className="text-xl font-bold text-slate-900">Transactions</h1>

      <Card className="overflow-hidden">
        <FilterBar
          params={filters}
          onUpdate={updateParam}
          onReset={resetFilters}
        />

        {/* Desktop table */}
        <div className="hidden md:block">
          {isLoading ? (
            <Table>
              <Thead><tr><Th>Date</Th><Th>Counterparty</Th><Th>Type</Th><Th>Remark</Th><Th>Amount</Th><Th>Status</Th><Th>Settlement</Th></tr></Thead>
              <Tbody>{Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} cols={7} />)}</Tbody>
            </Table>
          ) : error ? (
            <ErrorState message={parseApiError(error).message} onRetry={refetch} />
          ) : txns.length === 0 ? (
            <EmptyState title="No transactions found" description="Try adjusting your filters." />
          ) : (
            <Table>
              <Thead>
                <tr>
                  <Th>Date</Th><Th>Counterparty</Th><Th>Type</Th><Th>Remark</Th><Th>Amount</Th><Th>Status</Th><Th>Settlement</Th>
                </tr>
              </Thead>
              <Tbody>
                {txns.map((txn) => {
                  const isSent = txn.direction === 'SENT'
                  return (
                    <Tr key={txn.id} onClick={() => navigate(`/transactions/${txn.id}`)}>
                      <Td className="text-xs text-slate-500 whitespace-nowrap">{formatDateTimeIST(txn.initiatedAt)}</Td>
                      <Td className="font-medium">{txn.counterpartyName || '—'}</Td>
                      <Td>
                        <span className="flex items-center gap-1">
                          {isSent
                            ? <ArrowUpRight className="w-3.5 h-3.5 text-red-500" />
                            : <ArrowDownLeft className="w-3.5 h-3.5 text-green-600" />}
                          {txn.direction}
                        </span>
                      </Td>
                      <Td className="text-slate-500 max-w-[120px] truncate">{txn.remark || '—'}</Td>
                      <Td className={`font-semibold ${isSent ? 'text-red-600' : 'text-green-600'}`}>
                        {isSent ? '−' : '+'}{formatINR(txn.amount)}
                      </Td>
                      <Td><Badge status={txn.status} /></Td>
                      <Td className="text-xs text-slate-500">
                        {txn.settlementDueDate ? (
                          <>
                            {formatDateOnly(txn.settlementDueDate)}
                            {txn.status === 'PENDING' && txn.daysRemaining != null && (
                              <span className="ml-1 text-amber-600">· {txn.daysRemaining}d left</span>
                            )}
                          </>
                        ) : '—'}
                      </Td>
                    </Tr>
                  )
                })}
              </Tbody>
            </Table>
          )}
        </div>

        {/* Mobile cards */}
        <div className="md:hidden">
          {isLoading ? (
            <div className="p-4 space-y-3">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-16 bg-slate-200 rounded-xl animate-pulse" />
              ))}
            </div>
          ) : error ? (
            <ErrorState message={parseApiError(error).message} onRetry={refetch} />
          ) : txns.length === 0 ? (
            <EmptyState title="No transactions found" description="Try adjusting your filters." />
          ) : (
            txns.map((txn) => (
              <TxnCard key={txn.id} txn={txn} onClick={() => navigate(`/transactions/${txn.id}`)} />
            ))
          )}
        </div>

        {data && data.totalPages > 1 && (
          <div className="px-4 pb-4">
            <Pagination
              page={page}
              totalPages={data.totalPages}
              onPageChange={(p) => { const sp = new URLSearchParams(searchParams); sp.set('page', String(p)); setSearchParams(sp) }}
              pageSize={size}
              onPageSizeChange={(s) => { const sp = new URLSearchParams(searchParams); sp.set('size', String(s)); sp.set('page', '0'); setSearchParams(sp) }}
            />
          </div>
        )}
      </Card>
    </div>
  )
}

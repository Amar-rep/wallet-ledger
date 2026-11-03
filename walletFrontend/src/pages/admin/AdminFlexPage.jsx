import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Zap, AlertTriangle, CheckCircle2, Clock, XCircle, Filter,
} from 'lucide-react'
import { toast } from 'sonner'
import { flexApi } from '../../api/flex.js'
import { formatINR, formatDateTimeIST } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Button from '../../components/ui/Button.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Input from '../../components/ui/Input.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonCard } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

const STATUS_STYLE = {
  OPEN:        'bg-blue-100 text-blue-700',
  REPAID:      'bg-green-100 text-green-700',
  RECOVERING:  'bg-amber-100 text-amber-700',
  RECOVERED:   'bg-green-100 text-green-600',
  WRITTEN_OFF: 'bg-slate-100 text-slate-500',
}

const STATUSES = ['', 'OPEN', 'REPAID', 'RECOVERING', 'RECOVERED', 'WRITTEN_OFF']

function StatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLE[status] || 'bg-slate-100 text-slate-600'}`}>
      {status?.replace('_', ' ') || '—'}
    </span>
  )
}

function WriteOffModal({ credit, onClose }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')

  const mutation = useMutation({
    mutationFn: () => flexApi.writeOff(credit.id, reason),
    onSuccess: (res) => {
      toast.success(res.message)
      qc.invalidateQueries({ queryKey: ['admin-flex'] })
      onClose()
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  return (
    <Modal open={!!credit} onClose={onClose} title="Write off Flex credit" size="sm">
      <div className="space-y-4">
        <div className="bg-slate-50 rounded-lg p-3 text-sm space-y-1">
          <div className="flex justify-between">
            <span className="text-slate-500">Credit #</span>
            <span className="font-medium">{credit?.id}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Beneficiary</span>
            <span className="font-medium">{credit?.beneficiaryName}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">Outstanding</span>
            <span className="font-semibold text-amber-700">{formatINR(credit?.amountOutstanding)}</span>
          </div>
        </div>
        <p className="text-sm text-slate-600">
          This will forgive the outstanding debt and mark the Flex credit as Written Off.
          The action cannot be reversed.
        </p>
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Reason (required)</label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            placeholder="e.g. Customer account closed, unrecoverable after 90 days"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"
          />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            variant="danger"
            loading={mutation.isPending}
            disabled={!reason.trim()}
            onClick={() => mutation.mutate()}
          >
            <XCircle className="w-4 h-4" /> Write off
          </Button>
        </div>
      </div>
    </Modal>
  )
}

export default function AdminFlexPage() {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState('')
  const [writeOffTarget, setWriteOffTarget] = useState(null)

  const { data: credits = [], isLoading, error, refetch } = useQuery({
    queryKey: ['admin-flex', statusFilter],
    queryFn: () => flexApi.adminList(statusFilter || null),
    staleTime: 30_000,
  })

  if (isLoading) return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-slate-900">Flex credits</h1>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[1,2,3,4].map((i) => <SkeletonCard key={i} lines={3} />)}
      </div>
      <SkeletonCard lines={8} />
    </div>
  )
  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch} />

  // Summary counts
  const counts = credits.reduce((acc, c) => {
    acc[c.status] = (acc[c.status] || 0) + 1
    return acc
  }, {})
  const totalOutstanding = credits.reduce((s, c) => s + parseFloat(c.amountOutstanding || 0), 0)

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold text-slate-900 flex items-center gap-2">
        <Zap className="w-5 h-5 text-primary-600" /> Flex credits
      </h1>

      {/* Summary cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {[
          { label: 'Open',        value: counts.OPEN        || 0, color: 'text-blue-600',   bg: 'bg-blue-50'   },
          { label: 'Recovering',  value: counts.RECOVERING  || 0, color: 'text-amber-600',  bg: 'bg-amber-50'  },
          { label: 'Repaid',      value: counts.REPAID      || 0, color: 'text-green-600',  bg: 'bg-green-50'  },
          { label: 'Outstanding', value: formatINR(totalOutstanding), color: 'text-red-600',   bg: 'bg-red-50'    },
        ].map(({ label, value, color, bg }) => (
          <Card key={label} className={`p-4 ${bg}`}>
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">{label}</p>
            <p className={`text-xl font-bold mt-1 ${color}`}>{value}</p>
          </Card>
        ))}
      </div>

      {/* Filter */}
      <Card className="p-3 flex items-center gap-3 border border-slate-200">
        <Filter className="w-4 h-4 text-slate-400 flex-shrink-0" />
        <label className="text-sm text-slate-500 flex-shrink-0">Status:</label>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="text-sm border-0 bg-transparent outline-none text-slate-700 cursor-pointer"
        >
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || 'All'}</option>
          ))}
        </select>
      </Card>

      {/* Table */}
      <Card className="overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-800">Flex credit records</h2>
          <span className="text-xs text-slate-500">{credits.length} records</span>
        </div>

        {credits.length === 0 ? (
          <EmptyState icon={Zap} title="No Flex credits found"
            description={statusFilter ? `No credits with status "${statusFilter}".` : 'No Flex credits yet.'} />
        ) : (
          <>
            {/* Mobile cards */}
            <div className="divide-y divide-slate-100 sm:hidden">
              {credits.map((c) => (
                <div key={c.id} className="px-4 py-3 space-y-2">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="font-semibold text-slate-900">{formatINR(c.amount)}</span>
                      <span className="text-xs text-slate-500 ml-2">{c.beneficiaryName}</span>
                    </div>
                    <StatusBadge status={c.status} />
                  </div>
                  {c.amountOutstanding > 0 && (
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-amber-700">Outstanding: {formatINR(c.amountOutstanding)}</span>
                      {c.status === 'RECOVERING' && (
                        <Button size="sm" variant="danger" onClick={() => setWriteOffTarget(c)}>Write off</Button>
                      )}
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
                    <Th>ID</Th>
                    <Th>Txn #</Th>
                    <Th>Beneficiary</Th>
                    <Th>Amount</Th>
                    <Th>Recovered</Th>
                    <Th>Outstanding</Th>
                    <Th>Status</Th>
                    <Th>Disbursed</Th>
                    <Th />
                  </tr>
                </Thead>
                <Tbody>
                  {credits.map((c) => (
                    <Tr key={c.id} className={c.status === 'RECOVERING' ? 'bg-amber-50/30' : ''}>
                      <Td className="text-slate-400 text-xs font-mono">#{c.id}</Td>
                      <Td className="text-slate-500 text-xs font-mono">#{c.transactionId}</Td>
                      <Td className="font-medium text-slate-800">{c.beneficiaryName}</Td>
                      <Td className="font-semibold">{formatINR(c.amount)}</Td>
                      <Td className="text-slate-600">{formatINR(c.amountRecovered)}</Td>
                      <Td className={c.amountOutstanding > 0 ? 'text-amber-700 font-semibold' : 'text-slate-400'}>
                        {formatINR(c.amountOutstanding)}
                      </Td>
                      <Td><StatusBadge status={c.status} /></Td>
                      <Td className="text-xs text-slate-500">{formatDateTimeIST(c.disbursedAt)}</Td>
                      <Td>
                        {c.status === 'RECOVERING' && (
                          <Button size="sm" variant="danger" onClick={() => setWriteOffTarget(c)}>
                            Write off
                          </Button>
                        )}
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            </div>
          </>
        )}
      </Card>

      <WriteOffModal
        credit={writeOffTarget}
        onClose={() => setWriteOffTarget(null)}
      />
    </div>
  )
}

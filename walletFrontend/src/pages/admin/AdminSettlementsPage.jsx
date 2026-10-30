import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Clock, Calendar, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { differenceInDays, parseISO } from 'date-fns'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateOnly, formatDateTimeIST } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Modal from '../../components/ui/Modal.jsx'
import StatCard from '../../components/ui/StatCard.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonCard, SkeletonRow } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

function SettleModal({ txn, onClose, onDone }) {
  const qc = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => adminApi.manualSettle(txn.id),
    onSuccess: (res) => {
      toast.success(res.message || 'Settlement triggered.')
      qc.invalidateQueries({ queryKey: ['admin-settlements'] })
      onDone()
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  return (
    <Modal open={!!txn} onClose={onClose} title="Manually settle transaction" size="sm">
      <p className="text-sm text-slate-600 mb-2">
        This triggers immediate settlement for transaction <span className="font-mono font-medium">#{txn?.id}</span>.
      </p>
      <p className="text-sm text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2 mb-5">
        The amount ({formatINR(txn?.amount)}) will be credited to {txn?.receiverName} right now, bypassing the normal ACH schedule.
      </p>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={mutation.isPending} onClick={() => mutation.mutate()}>
          <CheckCircle2 className="w-4 h-4"/> Settle now
        </Button>
      </div>
    </Modal>
  )
}

function OverdueBadge({ dueDate }) {
  if (!dueDate) return null
  const today = new Date().toISOString().slice(0, 10)
  if (dueDate >= today) return null
  const days = differenceInDays(new Date(), parseISO(dueDate))
  return (
    <span className="ml-1.5 inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700">
      Overdue by {days}d
    </span>
  )
}

export default function AdminSettlementsPage() {
  const navigate = useNavigate()
  const [settleTarget, setSettleTarget] = useState(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-settlements'],
    queryFn: adminApi.settlements,
    staleTime: 30_000,
  })

  if (isLoading) return <div className="space-y-4"><div className="grid grid-cols-1 sm:grid-cols-4 gap-4">{Array.from({length:4}).map((_,i)=><SkeletonCard key={i} lines={3}/>)}</div><SkeletonCard lines={8}/></div>
  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch}/>

  const txns = data.pendingTransactions || []

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold text-slate-900">Settlement monitor</h1>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Due today"      value={data.dueToday.toString()}   icon={Calendar} />
        <StatCard label="Due tomorrow"   value={data.dueTomorrow.toString()} icon={Clock} />
        <StatCard label="Overdue"        value={data.overdue.toString()}     icon={AlertTriangle} colorClass={data.overdue>0?'text-red-600':'text-slate-400'}/>
        <StatCard label="Total pending"  value={formatINR(data.totalPendingAmount)} icon={Clock} />
      </div>

      {/* Table */}
      <Card className="overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-800">Pending transactions</h2>
          <span className="text-xs text-slate-500">{txns.length} total</span>
        </div>
        {txns.length === 0 ? (
          <EmptyState icon={CheckCircle2} title="No pending settlements" description="All transactions are settled." />
        ) : (
          <Table>
            <Thead>
              <tr><Th>ID</Th><Th>Sender</Th><Th>Receiver</Th><Th>Amount</Th><Th>Due date</Th><Th>Flag</Th><Th>Action</Th></tr>
            </Thead>
            <Tbody>
              {txns.map(t => {
                const today = new Date().toISOString().slice(0,10)
                const isOverdue = t.settlementDueDate && t.settlementDueDate < today
                return (
                  <Tr key={t.id} className={isOverdue ? 'bg-red-50/30' : ''}>
                    <Td className="font-mono text-xs text-slate-500">
                      <button onClick={()=>navigate(`/admin/transactions/${t.id}`)} className="text-primary-600 hover:underline">
                        #{t.id}
                      </button>
                    </Td>
                    <Td>{t.senderName||'—'}</Td>
                    <Td>{t.receiverName}</Td>
                    <Td className="font-semibold">{formatINR(t.amount)}</Td>
                    <Td>
                      <span className={isOverdue ? 'text-red-600 font-medium' : 'text-slate-700'}>
                        {formatDateOnly(t.settlementDueDate)}
                      </span>
                      <OverdueBadge dueDate={t.settlementDueDate}/>
                    </Td>
                    <Td>{t.flagged && <span className="text-xs text-red-600">⚑</span>}</Td>
                    <Td>
                      <Button size="sm" variant="secondary" onClick={()=>setSettleTarget(t)}>
                        Settle now
                      </Button>
                    </Td>
                  </Tr>
                )
              })}
            </Tbody>
          </Table>
        )}
      </Card>

      <SettleModal
        txn={settleTarget}
        onClose={() => setSettleTarget(null)}
        onDone={() => setSettleTarget(null)}
      />
    </div>
  )
}

import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Copy, CheckCircle2, Clock, Circle } from 'lucide-react'
import { toast } from 'sonner'
import { transactionsApi } from '../api/transactions.js'
import { formatINR, formatDateTimeIST, formatDateOnly } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Card from '../components/ui/Card.jsx'
import Badge from '../components/ui/Badge.jsx'
import Button from '../components/ui/Button.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../components/ui/Table.jsx'
import { SkeletonCard } from '../components/ui/Skeleton.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'

function CopyButton({ value, label }) {
  function copy() {
    navigator.clipboard.writeText(value).then(() => toast.success(`${label} copied`))
  }
  return (
    <button
      onClick={copy}
      aria-label={`Copy ${label}`}
      className="ml-2 p-1 rounded text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
    >
      <Copy className="w-3.5 h-3.5" />
    </button>
  )
}

function DetailRow({ label, value, children }) {
  return (
    <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center px-4 py-3 border-b border-slate-100 last:border-0 gap-1">
      <span className="text-sm text-slate-500 flex-shrink-0">{label}</span>
      <div className="flex items-center text-sm font-medium text-slate-800">
        {children || value}
      </div>
    </div>
  )
}

function SettlementTimeline({ txn }) {
  const steps = [
    { label: 'Initiated', done: true, time: formatDateTimeIST(txn.initiatedAt) },
    { label: 'In holding', done: txn.status !== 'FAILED' && txn.status !== 'DECLINED', time: 'Funds held' },
    {
      label: txn.status === 'SUCCESS' ? `Settled` : `Settles on ${formatDateOnly(txn.settlementDueDate)}`,
      done: txn.status === 'SUCCESS',
      time: txn.settledAt ? formatDateTimeIST(txn.settledAt) : txn.daysRemaining != null ? `${txn.daysRemaining} day${txn.daysRemaining !== 1 ? 's' : ''} remaining` : null,
    },
  ]

  return (
    <div className="px-4 py-5">
      <h3 className="text-sm font-medium text-slate-700 mb-4">Settlement progress</h3>
      <div className="relative flex items-start gap-0">
        {steps.map((s, i) => (
          <div key={i} className="flex-1 flex flex-col items-center">
            <div className="relative flex items-center w-full">
              {i > 0 && (
                <div className={`flex-1 h-0.5 ${steps[i - 1].done && s.done ? 'bg-primary-500' : 'bg-slate-200'}`} />
              )}
              <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 border-2 ${
                s.done ? 'bg-primary-600 border-primary-600 text-white' : 'bg-white border-slate-300 text-slate-400'
              }`}>
                {s.done ? <CheckCircle2 className="w-4 h-4" /> : <Circle className="w-4 h-4" />}
              </div>
              {i < steps.length - 1 && (
                <div className={`flex-1 h-0.5 ${s.done && steps[i + 1].done ? 'bg-primary-500' : 'bg-slate-200'}`} />
              )}
            </div>
            <p className={`text-xs font-medium mt-1.5 text-center ${s.done ? 'text-primary-700' : 'text-slate-400'}`}>{s.label}</p>
            {s.time && <p className="text-xs text-slate-400 text-center mt-0.5">{s.time}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}

const ENTRY_TYPE_LABEL = { DEBIT: 'Debit', CREDIT: 'Credit', REFUND: 'Refund' }

export default function TransactionDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const { data: txn, isLoading, error, refetch } = useQuery({
    queryKey: ['transaction', id],
    queryFn: () => transactionsApi.detail(id),
    retry: (count, err) => err?.response?.status !== 404 && count < 1,
  })

  if (isLoading) {
    return (
      <div className="max-w-2xl mx-auto space-y-4">
        <SkeletonCard lines={6} />
        <SkeletonCard lines={4} />
      </div>
    )
  }

  if (error?.response?.status === 404) {
    return (
      <div className="max-w-2xl mx-auto">
        <Card className="p-10 text-center">
          <p className="text-lg font-semibold text-slate-700">Transaction not found</p>
          <p className="text-sm text-slate-500 mt-1 mb-4">This transaction doesn't exist or you don't have access.</p>
          <Button variant="secondary" onClick={() => navigate('/transactions')}>
            Back to transactions
          </Button>
        </Card>
      </div>
    )
  }

  if (error) {
    return (
      <div className="max-w-2xl mx-auto">
        <ErrorState message={parseApiError(error).message} onRetry={refetch} />
      </div>
    )
  }

  const isSent = txn.direction === 'SENT'
  const showFailureReason = txn.status === 'FAILED' || txn.status === 'DECLINED'

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      {/* Back */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="w-4 h-4" /> Back
      </button>

      {/* Header card */}
      <Card className="p-6 flex flex-col items-center text-center gap-2">
        <p className={`text-4xl font-bold ${isSent ? 'text-red-600' : 'text-green-600'}`}>
          {isSent ? '−' : '+'}{formatINR(txn.amount)}
        </p>
        <div className="flex items-center gap-2">
          <Badge status={txn.status} />
          <span className="text-slate-400 text-xs">·</span>
          <span className="text-xs text-slate-500">{isSent ? 'Sent' : 'Received'}</span>
        </div>
        <p className="text-xs text-slate-400">{formatDateTimeIST(txn.initiatedAt)}</p>
      </Card>

      {/* Failure reason */}
      {showFailureReason && txn.failureReason && (
        <div role="alert" className="flex items-start gap-3 p-4 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700">
          <span className="font-medium flex-shrink-0">Reason:</span>
          <span>{txn.failureReason}</span>
        </div>
      )}

      {/* Details grid */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Details</h2>
        <DetailRow label="Sender">{txn.senderName}</DetailRow>
        <DetailRow label="Receiver">{txn.receiverName}</DetailRow>
        <DetailRow label="Remark">{txn.remark || <span className="text-slate-400">—</span>}</DetailRow>
        <DetailRow label="Request ID">
          <span className="font-mono text-xs truncate max-w-[180px]">{txn.requestId}</span>
          <CopyButton value={txn.requestId} label="Request ID" />
        </DetailRow>
        <DetailRow label="Initiated">{formatDateTimeIST(txn.initiatedAt)}</DetailRow>
        <DetailRow label="Settled">{txn.settledAt ? formatDateTimeIST(txn.settledAt) : <span className="text-slate-400">Pending</span>}</DetailRow>
      </Card>

      {/* Settlement timeline for PENDING */}
      {txn.status === 'PENDING' && txn.settlementDueDate && (
        <Card>
          <SettlementTimeline txn={txn} />
        </Card>
      )}

      {/* Ledger entries */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Ledger entries</h2>
        {txn.ledgerEntries?.length ? (
          <>
            <Table>
              <Thead>
                <tr>
                  <Th>Type</Th>
                  <Th>Wallet</Th>
                  <Th>Amount</Th>
                  <Th>Balance after</Th>
                  <Th>Time</Th>
                </tr>
              </Thead>
              <Tbody>
                {txn.ledgerEntries.map((e) => (
                  <Tr key={e.id}>
                    <Td><Badge status={e.entryType}>{ENTRY_TYPE_LABEL[e.entryType] || e.entryType}</Badge></Td>
                    <Td className="font-mono text-xs">#{e.walletId}</Td>
                    <Td className="font-medium">{formatINR(e.amount)}</Td>
                    <Td>{formatINR(e.balanceAfter)}</Td>
                    <Td className="text-xs text-slate-500 whitespace-nowrap">{formatDateTimeIST(e.createdAt)}</Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
            {txn.ledgerEntries.every((e) => e.entryType === 'DEBIT') && (
              <p className="px-4 py-3 text-xs text-slate-500 border-t border-slate-100">
                The credit entry is created when this transfer settles.
              </p>
            )}
          </>
        ) : (
          <p className="px-4 py-4 text-sm text-slate-500">No ledger entries yet.</p>
        )}
      </Card>
    </div>
  )
}

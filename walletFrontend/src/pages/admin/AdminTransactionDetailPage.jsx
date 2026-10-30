import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Copy, Flag } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateTimeIST, formatDateOnly } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonCard } from '../../components/ui/Skeleton.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

const ENTRY_LABEL = { DEBIT: 'Debit', CREDIT: 'Credit', REFUND: 'Refund' }

function CopyBtn({ value, label }) {
  return (
    <button onClick={() => navigator.clipboard.writeText(value).then(()=>toast.success(`${label} copied`))}
      aria-label={`Copy ${label}`} className="ml-1.5 p-1 rounded text-slate-400 hover:text-slate-600 hover:bg-slate-100">
      <Copy className="w-3.5 h-3.5"/>
    </button>
  )
}

function DetailRow({ label, children }) {
  return (
    <div className="flex flex-col sm:flex-row sm:justify-between px-4 py-3 border-b border-slate-100 last:border-0 gap-1 text-sm">
      <span className="text-slate-500">{label}</span>
      <div className="font-medium text-slate-800 flex items-center">{children}</div>
    </div>
  )
}

export default function AdminTransactionDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const { data: t, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-transaction', id],
    queryFn: () => adminApi.transactionDetail(id),
    retry: (n, e) => e?.response?.status !== 404 && n < 1,
  })

  if (isLoading) return <div className="max-w-2xl mx-auto space-y-4"><SkeletonCard lines={7}/><SkeletonCard lines={4}/></div>
  if (error?.response?.status === 404) return (
    <div className="max-w-2xl mx-auto"><Card className="p-10 text-center">
      <p className="font-semibold text-slate-700">Transaction not found</p>
      <Button variant="secondary" className="mt-4" onClick={()=>navigate('/admin/transactions')}>Back</Button>
    </Card></div>
  )
  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch}/>

  const showFailure = t.status === 'FAILED' || t.status === 'DECLINED'

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      <button onClick={()=>navigate(-1)} className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="w-4 h-4"/> Back
      </button>

      {/* Header */}
      <Card className="p-6 text-center flex flex-col items-center gap-2">
        <p className="text-4xl font-bold text-slate-900">{formatINR(t.amount)}</p>
        <div className="flex items-center gap-2 flex-wrap justify-center">
          <Badge status={t.status}/>
          {t.flagged && (
            <span className="flex items-center gap-1 text-xs text-red-600 bg-red-50 px-2 py-0.5 rounded-full">
              <Flag className="w-3 h-3"/> Flagged
            </span>
          )}
        </div>
        <p className="text-xs text-slate-400">{formatDateTimeIST(t.initiatedAt)}</p>
      </Card>

      {/* Failure reason */}
      {showFailure && t.failureReason && (
        <div role="alert" className="px-4 py-3 bg-red-50 border border-red-200 rounded-xl text-sm text-red-700 flex gap-2">
          <span className="font-medium">Reason:</span> {t.failureReason}
        </div>
      )}

      {/* Details */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Details</h2>
        <DetailRow label="Sender">
          <button onClick={()=>navigate(`/admin/users/${t.senderUserId}`)} className="text-primary-600 hover:underline">
            {t.senderName}
          </button>
        </DetailRow>
        <DetailRow label="Receiver">
          <button onClick={()=>navigate(`/admin/users/${t.receiverUserId}`)} className="text-primary-600 hover:underline">
            {t.receiverName}
          </button>
        </DetailRow>
        <DetailRow label="Remark">{t.remark || <span className="text-slate-400">—</span>}</DetailRow>
        <DetailRow label="Request ID">
          <span className="font-mono text-xs truncate max-w-[180px]">{t.requestId}</span>
          <CopyBtn value={t.requestId} label="Request ID"/>
        </DetailRow>
        <DetailRow label="Initiated">{formatDateTimeIST(t.initiatedAt)}</DetailRow>
        <DetailRow label="Settlement due">{formatDateOnly(t.settlementDueDate)}</DetailRow>
        <DetailRow label="Settled">{t.settledAt ? formatDateTimeIST(t.settledAt) : <span className="text-slate-400">Pending</span>}</DetailRow>
        {t.settlementBatchId && (
          <DetailRow label="Settlement batch">
            <span className="font-mono text-xs">#{t.settlementBatchId}</span>
          </DetailRow>
        )}
      </Card>

      {/* Ledger */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Ledger entries</h2>
        {t.ledgerEntries?.length ? (
          <Table>
            <Thead><tr><Th>Type</Th><Th>Wallet</Th><Th>Amount</Th><Th>Balance after</Th><Th>Time</Th></tr></Thead>
            <Tbody>
              {t.ledgerEntries.map(e=>(
                <Tr key={e.id}>
                  <Td><Badge status={e.entryType}>{ENTRY_LABEL[e.entryType]||e.entryType}</Badge></Td>
                  <Td className="font-mono text-xs">#{e.walletId}</Td>
                  <Td className="font-medium">{formatINR(e.amount)}</Td>
                  <Td>{formatINR(e.balanceAfter)}</Td>
                  <Td className="text-xs text-slate-400 whitespace-nowrap">{formatDateTimeIST(e.createdAt)}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        ) : (
          <p className="px-4 py-4 text-sm text-slate-500">No ledger entries yet.</p>
        )}
      </Card>
    </div>
  )
}

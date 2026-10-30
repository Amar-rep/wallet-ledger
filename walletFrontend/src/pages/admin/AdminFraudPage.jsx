import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Shield, ShieldOff, CheckCircle2, ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateTimeIST } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Modal from '../../components/ui/Modal.jsx'
import { SkeletonCard } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

const TABS = ['PENDING', 'BLOCKED', 'DISMISSED']

const ALERT_TYPE_LABEL = {
  HIGH_VALUE: 'High value',
  DAILY_LIMIT: 'Daily limit',
  DUPLICATE: 'Duplicate',
}
const ALERT_TYPE_COLOR = {
  HIGH_VALUE:  'bg-red-100 text-red-800',
  DAILY_LIMIT: 'bg-amber-100 text-amber-800',
  DUPLICATE:   'bg-orange-100 text-orange-800',
}

function AlertTypeBadge({ type }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${ALERT_TYPE_COLOR[type]||'bg-slate-100 text-slate-700'}`}>
      {ALERT_TYPE_LABEL[type] || type}
    </span>
  )
}

function ReviewModal({ alert, action, onClose, onDone }) {
  const qc = useQueryClient()
  const { register, handleSubmit, formState: { isSubmitting } } = useForm()
  const isBlock = action === 'BLOCK'

  const mutation = useMutation({
    mutationFn: (data) => adminApi.reviewFraudAlert(alert.id, { action, note: data.note || undefined }),
    onSuccess: () => {
      toast.success(isBlock ? 'Transaction blocked and sender refunded.' : 'Alert dismissed.')
      qc.invalidateQueries({ queryKey: ['fraud-alerts'] })
      onDone()
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  return (
    <Modal open={!!alert} onClose={onClose} title={isBlock ? 'Block transaction' : 'Dismiss alert'} size="sm">
      {isBlock && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2 text-sm text-red-700 mb-4">
          This will <strong>fail the transaction</strong> and refund {formatINR(alert?.amount)} to the sender immediately.
          Both parties will be notified. This cannot be undone.
        </div>
      )}
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-1">Note (optional)</label>
          <textarea
            {...register('note')}
            rows={3}
            maxLength={255}
            placeholder="Add a reason or note…"
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-600"
          />
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button variant={isBlock ? 'danger' : 'primary'} type="submit" loading={isSubmitting || mutation.isPending}>
            {isBlock ? 'Block & refund' : 'Dismiss'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function AlertCard({ alert, isPending }) {
  const navigate = useNavigate()
  const [modal, setModal] = useState(null) // { action: 'BLOCK'|'DISMISS' }

  const canBlock   = isPending && alert.alertType === 'HIGH_VALUE' && !alert.readOnly
  const canDismiss = isPending && !alert.readOnly
  const isInfoOnly = alert.alertType === 'DAILY_LIMIT' || alert.alertType === 'DUPLICATE'

  return (
    <div className={`p-4 rounded-xl border ${alert.readOnly ? 'bg-slate-50 border-slate-200 opacity-70' : 'bg-white border-slate-200'}`}>
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          <AlertTypeBadge type={alert.alertType} />
          <Badge status={alert.status} />
          {alert.readOnly && (
            <span className="text-xs text-slate-500 italic">Settled — read-only</span>
          )}
        </div>
        <span className="text-xs text-slate-400">{formatDateTimeIST(alert.createdAt)}</span>
      </div>

      <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
        <div><span className="text-slate-500">User:</span> <span className="font-medium">{alert.userName}</span></div>
        <div><span className="text-slate-500">Amount:</span> <span className="font-semibold">{formatINR(alert.amount)}</span></div>
        {alert.description && (
          <div className="sm:col-span-2 text-slate-600">{alert.description}</div>
        )}
        {alert.transactionId && (
          <div>
            <button
              onClick={() => navigate(`/admin/transactions/${alert.transactionId}`)}
              className="flex items-center gap-1 text-primary-600 hover:underline text-xs"
            >
              View transaction <ExternalLink className="w-3 h-3"/>
            </button>
          </div>
        )}
        {!isPending && alert.reviewedBy && (
          <div className="sm:col-span-2 text-xs text-slate-400">
            Reviewed by {alert.reviewedBy} · {formatDateTimeIST(alert.reviewedAt)}
          </div>
        )}
      </div>

      {isPending && !alert.readOnly && (
        <div className="mt-3 flex items-center gap-2 flex-wrap">
          {canBlock && (
            <Button variant="danger" size="sm" onClick={() => setModal({ action: 'BLOCK' })}>
              <ShieldOff className="w-3.5 h-3.5"/> Block
            </Button>
          )}
          {canDismiss && (
            <Button variant="secondary" size="sm" onClick={() => setModal({ action: 'DISMISS' })}>
              <CheckCircle2 className="w-3.5 h-3.5"/> Dismiss
            </Button>
          )}
          {isInfoOnly && (
            <span className="text-xs text-slate-500">
              Already blocked at transfer time — dismiss to acknowledge.
            </span>
          )}
        </div>
      )}

      {modal && (
        <ReviewModal
          alert={alert}
          action={modal.action}
          onClose={() => setModal(null)}
          onDone={() => setModal(null)}
        />
      )}
    </div>
  )
}

export default function AdminFraudPage() {
  const [tab, setTab] = useState('PENDING')

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['fraud-alerts', tab],
    queryFn: () => adminApi.fraudAlerts(tab),
    staleTime: 30_000,
  })

  const alerts = data || []

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-slate-900">Fraud review</h1>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 rounded-lg p-1 w-fit" role="tablist">
        {TABS.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              tab === t ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.charAt(0) + t.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="space-y-3">{Array.from({length:3}).map((_,i)=><SkeletonCard key={i} lines={4}/>)}</div>
      ) : error ? (
        <ErrorState message={parseApiError(error).message} onRetry={refetch}/>
      ) : alerts.length === 0 ? (
        <EmptyState icon={Shield} title={`No ${tab.toLowerCase()} alerts`} description="Nothing to review here."/>
      ) : (
        <div className="space-y-3" role="tabpanel">
          {alerts.map((a) => (
            <AlertCard key={a.id} alert={a} isPending={tab === 'PENDING'} />
          ))}
        </div>
      )}
    </div>
  )
}

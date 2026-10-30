import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck, Info, AlertTriangle } from 'lucide-react'
import { toast } from 'sonner'
import { notificationsApi } from '../api/notifications.js'
import { formatDateTimeIST } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Card from '../components/ui/Card.jsx'
import Button from '../components/ui/Button.jsx'
import Pagination from '../components/ui/Pagination.jsx'
import { SkeletonCard } from '../components/ui/Skeleton.jsx'
import EmptyState from '../components/ui/EmptyState.jsx'
import ErrorState from '../components/ui/ErrorState.jsx'

function NotifIcon({ severity }) {
  if (severity === 'CRITICAL')
    return <div className="w-8 h-8 rounded-full bg-red-100 flex items-center justify-center flex-shrink-0">
      <AlertTriangle className="w-4 h-4 text-red-500" />
    </div>
  return <div className="w-8 h-8 rounded-full bg-primary-50 flex items-center justify-center flex-shrink-0">
    <Info className="w-4 h-4 text-primary-600" />
  </div>
}

export default function NotificationsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['notifications', page, unreadOnly],
    queryFn: () => notificationsApi.list({ page, size: 20, unreadOnly }),
  })

  const markRead = useMutation({
    mutationFn: (id) => notificationsApi.markRead(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['unread-count'] })
    },
  })

  const markAllRead = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] })
      qc.invalidateQueries({ queryKey: ['unread-count'] })
      toast.success('All notifications marked as read')
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  const notifs = data?.content || []

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-slate-900">Notifications</h1>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-600 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={unreadOnly}
              onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0) }}
              className="rounded border-slate-300 text-primary-600 focus:ring-primary-600"
            />
            Unread only
          </label>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => markAllRead.mutate()}
            loading={markAllRead.isPending}
          >
            <CheckCheck className="w-3.5 h-3.5" /> Mark all read
          </Button>
        </div>
      </div>

      <Card>
        {isLoading ? (
          <div className="p-4 space-y-3">
            {Array.from({ length: 5 }).map((_, i) => <SkeletonCard key={i} lines={2} />)}
          </div>
        ) : error ? (
          <ErrorState message={parseApiError(error).message} onRetry={refetch} />
        ) : notifs.length === 0 ? (
          <EmptyState icon={Bell} title="No notifications" description={unreadOnly ? "You're all caught up." : 'Notifications will appear here.'} />
        ) : (
          <ul className="divide-y divide-slate-100" aria-live="polite">
            {notifs.map((n) => (
              <li
                key={n.id}
                className={`flex items-start gap-3 px-4 py-4 transition-colors hover:bg-slate-50 ${!n.read ? 'bg-primary-50/40' : ''}`}
              >
                <NotifIcon severity={n.severity} />
                <div
                  className="flex-1 min-w-0 cursor-pointer"
                  onClick={() => {
                    if (!n.read) markRead.mutate(n.id)
                    if (n.transactionId) navigate(`/transactions/${n.transactionId}`)
                  }}
                >
                  <p className={`text-sm leading-snug ${!n.read ? 'font-medium text-slate-900' : 'text-slate-700'}`}>
                    {n.message}
                  </p>
                  <p className="text-xs text-slate-400 mt-0.5">{formatDateTimeIST(n.createdAt)}</p>
                </div>
                {!n.read && (
                  <div className="w-2 h-2 rounded-full bg-primary-500 flex-shrink-0 mt-1.5" aria-label="Unread" />
                )}
              </li>
            ))}
          </ul>
        )}

        {data && data.totalPages > 1 && (
          <div className="px-4 pb-4">
            <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>
        )}
      </Card>
    </div>
  )
}

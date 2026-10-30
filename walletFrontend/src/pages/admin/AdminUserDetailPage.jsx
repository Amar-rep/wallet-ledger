import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Lock, Unlock } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateTimeIST, formatDateOnly, getInitials } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Modal from '../../components/ui/Modal.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonCard } from '../../components/ui/Skeleton.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'

export default function AdminUserDetailPage() {
  const { userId } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [freezeOpen, setFreezeOpen] = useState(false)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: () => adminApi.userDetail(userId),
  })

  const freeze = useMutation({
    mutationFn: () =>
      data?.user.walletStatus === 'FROZEN'
        ? adminApi.unfreezeWallet(userId)
        : adminApi.freezeWallet(userId),
    onSuccess: () => {
      const wasFrozen = data?.user.walletStatus === 'FROZEN'
      toast.success(wasFrozen ? 'Wallet unfrozen.' : 'Wallet frozen.')
      qc.invalidateQueries({ queryKey: ['admin-user', userId] })
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      setFreezeOpen(false)
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  if (isLoading) return <div className="max-w-3xl mx-auto space-y-4"><SkeletonCard lines={6} /><SkeletonCard lines={5} /></div>
  if (error?.response?.status === 404) return (
    <div className="max-w-3xl mx-auto">
      <Card className="p-10 text-center">
        <p className="font-semibold text-slate-700">User not found</p>
        <Button variant="secondary" className="mt-4" onClick={() => navigate('/admin/users')}>Back</Button>
      </Card>
    </div>
  )
  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch} />

  const { user, recentTransactions } = data
  const isFrozen = user.walletStatus === 'FROZEN'

  return (
    <div className="max-w-3xl mx-auto space-y-5">
      <button onClick={() => navigate('/admin/users')} className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="w-4 h-4" /> Back to users
      </button>

      {/* Profile card */}
      <Card className="p-6 flex flex-col sm:flex-row items-start gap-5">
        <div className="w-16 h-16 rounded-full bg-primary-600 text-white text-xl font-bold flex items-center justify-center flex-shrink-0">
          {getInitials(user.name)}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-1">
            <h1 className="text-xl font-bold text-slate-900">{user.name}</h1>
            <Badge status={user.walletStatus} />
            <span className="text-xs text-slate-400 bg-slate-100 px-2 py-0.5 rounded-full">{user.role}</span>
          </div>
          <p className="text-slate-500 text-sm">{user.email} · {user.phone}</p>
          <p className="text-xs text-slate-400 mt-0.5">Registered {formatDateTimeIST(user.registeredAt)}</p>
        </div>
        <div className="flex-shrink-0">
          {!isFrozen ? (
            <Button variant="danger" size="sm" onClick={() => setFreezeOpen(true)}>
              <Lock className="w-3.5 h-3.5" /> Freeze wallet
            </Button>
          ) : (
            <Button variant="secondary" size="sm" onClick={() => setFreezeOpen(true)}>
              <Unlock className="w-3.5 h-3.5" /> Unfreeze wallet
            </Button>
          )}
        </div>
      </Card>

      {/* Wallet info */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Wallet</h2>
        {[
          ['Balance', formatINR(user.walletBalance)],
          ['Status', <Badge status={user.walletStatus} />],
        ].map(([k, v]) => (
          <div key={k} className="flex justify-between px-4 py-3 border-b border-slate-100 last:border-0 text-sm">
            <span className="text-slate-500">{k}</span>
            <span className="font-medium text-slate-800">{v}</span>
          </div>
        ))}
      </Card>

      {/* Recent transactions */}
      <Card>
        <h2 className="px-4 pt-4 pb-2 text-sm font-semibold text-slate-600 uppercase tracking-wide">Recent transactions</h2>
        {recentTransactions?.length ? (
          <div className="hidden md:block">
            <Table>
              <Thead>
                <tr><Th>Date</Th><Th>Counterparty</Th><Th>Direction</Th><Th>Amount</Th><Th>Status</Th><Th>Settlement</Th></tr>
              </Thead>
              <Tbody>
                {recentTransactions.map((t) => (
                  <Tr key={t.id} onClick={() => navigate(`/admin/transactions/${t.id}`)}>
                    <Td className="text-xs text-slate-400">{formatDateTimeIST(t.initiatedAt)}</Td>
                    <Td className="font-medium">{t.counterpartyName || '—'}</Td>
                    <Td><Badge status={t.direction}>{t.direction}</Badge></Td>
                    <Td className={`font-semibold ${t.direction === 'SENT' ? 'text-red-600' : 'text-green-600'}`}>
                      {t.direction === 'SENT' ? '−' : '+'}{formatINR(t.amount)}
                    </Td>
                    <Td><Badge status={t.status} /></Td>
                    <Td className="text-xs text-slate-400">{formatDateOnly(t.settlementDueDate)}</Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </div>
        ) : (
          <EmptyState title="No transactions" description="This user has no transactions yet." />
        )}
      </Card>

      {/* Freeze confirm modal */}
      <Modal open={freezeOpen} onClose={() => setFreezeOpen(false)} title={isFrozen ? 'Unfreeze wallet' : 'Freeze wallet'} size="sm">
        <p className="text-sm text-slate-600 mb-5">
          {isFrozen
            ? `Unfreezing will restore ${user.name}'s ability to send and receive money.`
            : `Freezing will prevent ${user.name} from sending money. Incoming settlements will be declined and refunded. Only an admin can undo this.`}
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setFreezeOpen(false)}>Cancel</Button>
          <Button variant={isFrozen ? 'primary' : 'danger'} loading={freeze.isPending} onClick={() => freeze.mutate()}>
            {isFrozen ? 'Unfreeze' : 'Freeze'}
          </Button>
        </div>
      </Modal>
    </div>
  )
}

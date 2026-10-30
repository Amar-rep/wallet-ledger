import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Search, Lock, Unlock, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateTimeIST } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Modal from '../../components/ui/Modal.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonRow } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

function FreezeModal({ user, onClose, onDone }) {
  const qc = useQueryClient()
  const isFrozen = user?.walletStatus === 'FROZEN'

  const mutation = useMutation({
    mutationFn: () => isFrozen ? adminApi.unfreezeWallet(user.userId) : adminApi.freezeWallet(user.userId),
    onSuccess: () => {
      toast.success(isFrozen ? 'Wallet unfrozen.' : 'Wallet frozen.')
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      qc.invalidateQueries({ queryKey: ['admin-user', user.userId] })
      onDone()
    },
    onError: (err) => toast.error(parseApiError(err).message),
  })

  return (
    <Modal open={!!user} onClose={onClose} title={isFrozen ? 'Unfreeze wallet' : 'Freeze wallet'} size="sm">
      <p className="text-sm text-slate-600 mb-5">
        {isFrozen
          ? `This will reactivate ${user?.name}'s wallet. They will be able to send and receive money again.`
          : `This will freeze ${user?.name}'s wallet. They will not be able to send money and incoming settlements will be declined and refunded.`}
      </p>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button
          variant={isFrozen ? 'primary' : 'danger'}
          loading={mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {isFrozen ? 'Unfreeze' : 'Freeze wallet'}
        </Button>
      </div>
    </Modal>
  )
}

export default function AdminUsersPage() {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [freezeTarget, setFreezeTarget] = useState(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-users', page, search],
    queryFn: () => adminApi.users({ page, size: 20, search: search || undefined }),
    staleTime: 30_000,
  })

  const users = data?.content || []

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <h1 className="text-xl font-bold text-slate-900">Users</h1>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
            <input
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0) }}
              placeholder="Search name, email, phone…"
              className="pl-9 pr-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-600 w-60"
              aria-label="Search users"
            />
          </div>
        </div>
      </div>

      <Card className="overflow-hidden">
        {/* Desktop table */}
        <div className="hidden md:block">
          {isLoading ? (
            <Table>
              <Thead><tr><Th>Name</Th><Th>Email</Th><Th>Phone</Th><Th>Balance</Th><Th>Status</Th><Th>Registered</Th><Th></Th></tr></Thead>
              <Tbody>{Array.from({ length: 6 }).map((_, i) => <SkeletonRow key={i} cols={7} />)}</Tbody>
            </Table>
          ) : error ? (
            <ErrorState message={parseApiError(error).message} onRetry={refetch} />
          ) : users.length === 0 ? (
            <EmptyState title="No users found" description="Try a different search." />
          ) : (
            <Table>
              <Thead>
                <tr>
                  <Th>Name</Th><Th>Email</Th><Th>Phone</Th>
                  <Th>Balance</Th><Th>Wallet</Th><Th>Registered</Th><Th></Th>
                </tr>
              </Thead>
              <Tbody>
                {users.map((u) => (
                  <Tr key={u.userId} onClick={() => navigate(`/admin/users/${u.userId}`)}>
                    <Td className="font-medium">{u.name}</Td>
                    <Td className="text-slate-500 text-xs">{u.email}</Td>
                    <Td className="text-slate-500 text-xs">{u.phone}</Td>
                    <Td className="font-medium">{formatINR(u.walletBalance)}</Td>
                    <Td><Badge status={u.walletStatus} /></Td>
                    <Td className="text-xs text-slate-400">{formatDateTimeIST(u.registeredAt)}</Td>
                    <Td>
                      <button
                        onClick={(e) => { e.stopPropagation(); setFreezeTarget(u) }}
                        aria-label={u.walletStatus === 'FROZEN' ? 'Unfreeze wallet' : 'Freeze wallet'}
                        className={`p-1.5 rounded-lg transition-colors ${
                          u.walletStatus === 'FROZEN'
                            ? 'text-blue-600 hover:bg-blue-50'
                            : 'text-slate-400 hover:bg-red-50 hover:text-red-500'
                        }`}
                      >
                        {u.walletStatus === 'FROZEN' ? <Unlock className="w-4 h-4" /> : <Lock className="w-4 h-4" />}
                      </button>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </div>

        {/* Mobile cards */}
        <div className="md:hidden divide-y divide-slate-100">
          {isLoading
            ? Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="p-4 space-y-2 animate-pulse">
                  <div className="h-4 bg-slate-200 rounded w-1/2" />
                  <div className="h-3 bg-slate-200 rounded w-2/3" />
                </div>
              ))
            : users.map((u) => (
                <div
                  key={u.userId}
                  className="p-4 flex items-center gap-3 cursor-pointer hover:bg-slate-50"
                  onClick={() => navigate(`/admin/users/${u.userId}`)}
                >
                  <div className="w-10 h-10 rounded-full bg-primary-100 text-primary-700 font-bold text-sm flex items-center justify-center flex-shrink-0">
                    {u.name[0]?.toUpperCase()}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-slate-800 truncate">{u.name}</p>
                    <p className="text-xs text-slate-500 truncate">{u.email}</p>
                    <p className="text-xs font-medium text-slate-700 mt-0.5">{formatINR(u.walletBalance)}</p>
                  </div>
                  <Badge status={u.walletStatus} />
                </div>
              ))}
        </div>

        {data && data.totalPages > 1 && (
          <div className="px-4 pb-4">
            <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>
        )}
      </Card>

      <FreezeModal
        user={freezeTarget}
        onClose={() => setFreezeTarget(null)}
        onDone={() => setFreezeTarget(null)}
      />
    </div>
  )
}

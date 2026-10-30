import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Flag, X } from 'lucide-react'
import { adminApi } from '../../api/admin.js'
import { formatINR, formatDateTimeIST, formatDateOnly } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import Pagination from '../../components/ui/Pagination.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonRow } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

const STATUS_OPTIONS = ['ALL','SUCCESS','PENDING','FAILED','DECLINED']

function FilterBar({ params, onUpdate, onReset }) {
  return (
    <div className="flex flex-wrap gap-3 items-end p-4 border-b border-slate-100 bg-slate-50/50">
      {[['fromDate','From'],['toDate','To']].map(([key,label]) => (
        <div key={key}>
          <label className="block text-xs font-medium text-slate-500 mb-1">{label}</label>
          <input type="date" value={params[key]||''} onChange={(e)=>onUpdate(key,e.target.value||undefined)}
            className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600"/>
        </div>
      ))}
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Status</label>
        <select value={params.status||'ALL'} onChange={(e)=>onUpdate('status',e.target.value)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-600">
          {STATUS_OPTIONS.map(s=><option key={s} value={s}>{s.charAt(0)+s.slice(1).toLowerCase()}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Min (₹)</label>
        <input type="number" min="0" step="0.01" value={params.minAmount||''} onChange={(e)=>onUpdate('minAmount',e.target.value||undefined)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm w-24 focus:outline-none focus:ring-2 focus:ring-primary-600" placeholder="0"/>
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-500 mb-1">Max (₹)</label>
        <input type="number" min="0" step="0.01" value={params.maxAmount||''} onChange={(e)=>onUpdate('maxAmount',e.target.value||undefined)}
          className="border border-slate-200 rounded-lg px-2 py-1.5 text-sm w-24 focus:outline-none focus:ring-2 focus:ring-primary-600" placeholder="∞"/>
      </div>
      <Button variant="ghost" size="sm" onClick={onReset}><X className="w-3.5 h-3.5"/> Reset</Button>
    </div>
  )
}

export default function AdminTransactionsPage() {
  const navigate = useNavigate()
  const [sp, setSp] = useSearchParams()

  const page = parseInt(sp.get('page')||'0')
  const size = parseInt(sp.get('size')||'20')
  const filters = {
    fromDate:  sp.get('fromDate')  || undefined,
    toDate:    sp.get('toDate')    || undefined,
    status:    sp.get('status')    || 'ALL',
    minAmount: sp.get('minAmount') || undefined,
    maxAmount: sp.get('maxAmount') || undefined,
  }

  function updateParam(key, val) {
    const p = new URLSearchParams(sp)
    val === undefined || val === 'ALL' || val === '' ? p.delete(key) : p.set(key, val)
    p.set('page','0'); setSp(p)
  }
  function resetFilters() { setSp({ page:'0', size:String(size) }) }

  const qp = { ...filters, status: filters.status==='ALL' ? undefined : filters.status, page, size }

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-transactions', qp],
    queryFn: () => adminApi.transactions(qp),
    keepPreviousData: true,
  })

  const txns = data?.content || []

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-slate-900">Transactions</h1>
      <Card className="overflow-hidden">
        <FilterBar params={filters} onUpdate={updateParam} onReset={resetFilters} />
        <Table>
          <Thead>
            <tr>
              <Th>ID</Th><Th>Sender</Th><Th>Receiver</Th><Th>Amount</Th>
              <Th>Status</Th><Th>Flag</Th><Th>Initiated</Th><Th>Settlement</Th>
            </tr>
          </Thead>
          <Tbody>
            {isLoading
              ? Array.from({length:8}).map((_,i)=><SkeletonRow key={i} cols={8}/>)
              : txns.map(t => (
                <Tr
                  key={t.id}
                  onClick={()=>navigate(`/admin/transactions/${t.id}`)}
                  className={t.status==='PENDING' ? 'border-l-2 border-l-amber-400' : ''}
                >
                  <Td className="font-mono text-xs text-slate-500">#{t.id}</Td>
                  <Td className="font-medium">{t.senderName||'—'}</Td>
                  <Td className="font-medium">{t.receiverName}</Td>
                  <Td className="font-semibold">{formatINR(t.amount)}</Td>
                  <Td><Badge status={t.status}/></Td>
                  <Td>{t.flagged && <Flag className="w-3.5 h-3.5 text-red-400" aria-label="Flagged"/>}</Td>
                  <Td className="text-xs text-slate-400 whitespace-nowrap">{formatDateTimeIST(t.initiatedAt)}</Td>
                  <Td className="text-xs text-slate-400">{formatDateOnly(t.settlementDueDate)}</Td>
                </Tr>
              ))
            }
          </Tbody>
        </Table>
        {!isLoading && !error && txns.length===0 && <EmptyState title="No transactions found" description="Adjust your filters."/>}
        {error && <ErrorState message={parseApiError(error).message} onRetry={refetch}/>}
        {data && data.totalPages > 1 && (
          <div className="px-4 pb-4">
            <Pagination
              page={page} totalPages={data.totalPages}
              onPageChange={p=>{const x=new URLSearchParams(sp);x.set('page',String(p));setSp(x)}}
              pageSize={size}
              onPageSizeChange={s=>{const x=new URLSearchParams(sp);x.set('size',String(s));x.set('page','0');setSp(x)}}
            />
          </div>
        )}
      </Card>
    </div>
  )
}

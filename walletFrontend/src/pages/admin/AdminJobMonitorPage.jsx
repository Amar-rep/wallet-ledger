import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Clock, CheckCircle2, AlertCircle, Filter, ChevronDown, Loader2,
} from 'lucide-react'
import { formatDistanceToNow, parseISO } from 'date-fns'
import { adminApi } from '../../api/admin.js'
import { formatDateTimeIST, formatINR } from '../../lib/formatters.js'
import { parseApiError } from '../../lib/errorHelpers.js'
import Card from '../../components/ui/Card.jsx'
import Badge from '../../components/ui/Badge.jsx'
import Button from '../../components/ui/Button.jsx'
import { Table, Thead, Th, Tbody, Tr, Td } from '../../components/ui/Table.jsx'
import { SkeletonCard, SkeletonRow } from '../../components/ui/Skeleton.jsx'
import EmptyState from '../../components/ui/EmptyState.jsx'
import ErrorState from '../../components/ui/ErrorState.jsx'

function JobStatusBadge({ status }) {
  const statusMap = {
    PENDING: 'PENDING',
    RUNNING: 'RUNNING',
    SUCCESS: 'SUCCESS',
    FAILED: 'FAILED',
  }
  const style = {
    SUCCESS: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    RUNNING: 'bg-blue-100 text-blue-700',
    PENDING: 'bg-amber-100 text-amber-700',
  }
  return (
    <Badge className={style[status] || 'bg-slate-100 text-slate-700'}>
      {statusMap[status] || status}
    </Badge>
  )
}

function JobDurationDisplay({ startedAt, endedAt, status }) {
  if (!startedAt) return '—'
  if (!endedAt) return '(running...)'

  const start = parseISO(startedAt)
  const end = parseISO(endedAt)
  const durationMs = end - start
  const durationSec = Math.round(durationMs / 1000)

  if (durationSec < 60) return `${durationSec}s`
  const durationMin = Math.round(durationSec / 60)
  return `${durationMin}m`
}

function JobRow({ job }) {
  const [expanded, setExpanded] = useState(false)
  const hasError = job.errorMessage && job.errorMessage.trim() !== ''

  return (
    <>
      <Tr className={hasError ? 'bg-red-50/30' : ''}>
        <Td className="font-mono text-xs text-slate-500">
          <span className="font-medium text-slate-700">{job.jobName}</span>
          {job.settlementBatch && (
            <div className="text-xs text-slate-500 mt-1.5 space-y-0.5">
              <div>✓ Settled: <span className="font-medium text-green-600">{job.settlementBatch.settledCount}</span></div>
              <div>✗ Failed: <span className={`font-medium ${job.settlementBatch.failedCount > 0 ? 'text-red-600' : 'text-slate-400'}`}>{job.settlementBatch.failedCount}</span></div>
              <div>Total: <span className="font-medium text-slate-700">{formatINR(job.settlementBatch.totalAmountSettled)}</span></div>
            </div>
          )}
        </Td>
        <Td>
          <JobStatusBadge status={job.status} />
        </Td>
        <Td className="text-sm text-slate-600">
          {job.startedAt ? formatDateTimeIST(job.startedAt) : '—'}
        </Td>
        <Td className="text-sm text-slate-600 font-mono">
          <JobDurationDisplay startedAt={job.startedAt} endedAt={job.endedAt} status={job.status} />
        </Td>
        <Td className="text-sm">
          <span className="font-medium text-slate-800">
            {job.recordsProcessed || 0}
          </span>
          <span className="text-slate-400"> record{job.recordsProcessed !== 1 ? 's' : ''}</span>
        </Td>
        <Td>
          {hasError && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setExpanded(!expanded)}
              className="text-red-600 hover:text-red-700"
            >
              <ChevronDown className={`w-4 h-4 transition-transform ${expanded ? 'rotate-180' : ''}`} />
            </Button>
          )}
        </Td>
      </Tr>
      {hasError && expanded && (
        <Tr className="bg-red-50/50 border-t border-red-100">
          <Td colSpan={6}>
            <div className="py-3 px-3">
              <p className="text-xs font-semibold text-red-700 mb-1 uppercase tracking-wide">Error</p>
              <p className="text-sm text-red-600 font-mono bg-white/50 px-2 py-1 rounded border border-red-200 break-words">
                {job.errorMessage}
              </p>
            </div>
          </Td>
        </Tr>
      )}
    </>
  )
}

export default function AdminJobMonitorPage() {
  const [jobNameFilter, setJobNameFilter] = useState('')

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-jobs', jobNameFilter],
    queryFn: () => adminApi.jobs(jobNameFilter || null),
    staleTime: 30_000,
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <h1 className="text-xl font-bold text-slate-900">Job monitor</h1>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => <SkeletonCard key={i} lines={3} />)}
        </div>
        <SkeletonCard lines={8} />
      </div>
    )
  }

  if (error) return <ErrorState message={parseApiError(error).message} onRetry={refetch} />

  // backend returns a plain array (List<JobRunResponse>), not a wrapped object
  const jobs = Array.isArray(data) ? data : []
  const totalRuns = jobs.length
  const successCount = jobs.filter(j => j.status === 'SUCCESS').length
  const failedCount = jobs.filter(j => j.status === 'FAILED').length

  return (
    <div className="space-y-5">
      <h1 className="text-xl font-bold text-slate-900">Job monitor</h1>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Total runs</p>
              <p className="text-2xl font-bold text-slate-900 mt-1">{totalRuns}</p>
            </div>
            <Clock className="w-8 h-8 text-slate-300" />
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Successful</p>
              <p className="text-2xl font-bold text-green-600 mt-1">{successCount}</p>
            </div>
            <CheckCircle2 className="w-8 h-8 text-green-300" />
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Failed</p>
              <p className={`text-2xl font-bold mt-1 ${failedCount > 0 ? 'text-red-600' : 'text-slate-400'}`}>
                {failedCount}
              </p>
            </div>
            <AlertCircle className={`w-8 h-8 ${failedCount > 0 ? 'text-red-300' : 'text-slate-300'}`} />
          </div>
        </Card>
      </div>

      {/* Filter */}
      <Card className="p-4 border border-slate-200">
        <div className="flex items-center gap-2">
          <Filter className="w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Filter by job name..."
            value={jobNameFilter}
            onChange={(e) => setJobNameFilter(e.target.value)}
            className="flex-1 bg-transparent text-sm outline-none placeholder-slate-400 text-slate-900"
          />
          {jobNameFilter && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setJobNameFilter('')}
              className="text-slate-500 hover:text-slate-700"
            >
              Clear
            </Button>
          )}
        </div>
      </Card>

      {/* Table */}
      <Card className="overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
          <h2 className="font-semibold text-slate-800">Latest job runs</h2>
          <span className="text-xs text-slate-500">{jobs.length} total</span>
        </div>
        {jobs.length === 0 ? (
          <EmptyState
            icon={Loader2}
            title="No jobs found"
            description={jobNameFilter ? `No job runs match "${jobNameFilter}".` : 'No job history available.'}
          />
        ) : (
          <Table>
            <Thead>
              <tr>
                <Th>Job</Th>
                <Th>Status</Th>
                <Th>Started at</Th>
                <Th>Duration</Th>
                <Th>Records</Th>
                <Th />
              </tr>
            </Thead>
            <Tbody>
              {jobs.map((job) => (
                <JobRow key={`${job.id}-${job.startedAt}`} job={job} />
              ))}
            </Tbody>
          </Table>
        )}
      </Card>
    </div>
  )
}

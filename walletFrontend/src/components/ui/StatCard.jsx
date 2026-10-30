import { Skeleton } from './Skeleton.jsx'

export default function StatCard({ label, value, subtext, icon: Icon, loading, colorClass = 'text-primary-600' }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 flex items-start gap-4">
      {Icon && (
        <div className={`w-10 h-10 rounded-lg bg-primary-50 flex items-center justify-center flex-shrink-0`}>
          <Icon className={`w-5 h-5 ${colorClass}`} aria-hidden />
        </div>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">{label}</p>
        {loading ? (
          <Skeleton className="h-6 w-2/3 mt-1" />
        ) : (
          <p className="text-2xl font-semibold text-slate-900 mt-0.5 truncate">{value}</p>
        )}
        {subtext && <p className="text-xs text-slate-500 mt-0.5">{subtext}</p>}
      </div>
    </div>
  )
}

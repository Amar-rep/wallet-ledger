const presets = {
  // Transaction status
  PENDING:  'bg-amber-100 text-amber-800',
  SUCCESS:  'bg-green-100 text-green-800',
  FAILED:   'bg-red-100 text-red-800',
  DECLINED: 'bg-orange-100 text-orange-800',
  // Wallet status
  ACTIVE:   'bg-green-100 text-green-800',
  INACTIVE: 'bg-slate-100 text-slate-700',
  FROZEN:   'bg-blue-100 text-blue-800',
  // Fraud alert
  BLOCKED:  'bg-red-100 text-red-800',
  DISMISSED:'bg-slate-100 text-slate-700',
  // Ledger entry
  DEBIT:    'bg-red-50 text-red-700',
  CREDIT:   'bg-green-50 text-green-700',
  REFUND:   'bg-blue-50 text-blue-700',
  // Directions
  SENT:     'bg-red-50 text-red-700',
  RECEIVED: 'bg-green-50 text-green-700',
}

export default function Badge({ status, children, className = '' }) {
  const label = status || children
  const style = presets[label] || 'bg-slate-100 text-slate-700'
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${style} ${className}`}>
      {children || status}
    </span>
  )
}

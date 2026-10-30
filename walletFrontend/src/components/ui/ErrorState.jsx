import { AlertCircle } from 'lucide-react'
import Button from './Button.jsx'

export default function ErrorState({ message = 'Something went wrong', onRetry }) {
  return (
    <div
      role="alert"
      aria-live="assertive"
      className="flex flex-col items-center justify-center py-16 px-4 text-center"
    >
      <div className="w-14 h-14 rounded-full bg-red-50 flex items-center justify-center mb-4">
        <AlertCircle className="w-7 h-7 text-red-500" aria-hidden />
      </div>
      <p className="font-medium text-slate-700 mb-1">Something went wrong</p>
      <p className="text-sm text-slate-500 max-w-xs mb-4">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  )
}

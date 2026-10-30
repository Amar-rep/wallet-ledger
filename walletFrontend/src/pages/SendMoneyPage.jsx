import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useAuth } from '../features/auth/AuthContext.jsx'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Search, User, CheckCircle2, AlertCircle, RefreshCw, Send, ArrowLeft } from 'lucide-react'
import { toast } from 'sonner'
import { v4 as uuidv4 } from 'uuid'
import { addDays, format } from 'date-fns'
import { transfersApi } from '../api/transfers.js'
import { walletApi } from '../api/wallet.js'
import { formatINR, formatDateOnly, maskPhone, getInitials } from '../lib/formatters.js'
import { parseApiError } from '../lib/errorHelpers.js'
import Button from '../components/ui/Button.jsx'
import Input from '../components/ui/Input.jsx'
import Card from '../components/ui/Card.jsx'
import PinInput from '../components/ui/PinInput.jsx'
import Badge from '../components/ui/Badge.jsx'

// ------- Step indicator -------
function Stepper({ step }) {
  const steps = ['Find recipient', 'Amount', 'Confirm']
  return (
    <ol className="flex items-center gap-0 mb-8" aria-label="Send money steps">
      {steps.map((label, i) => {
        const current = i === step
        const done = i < step
        return (
          <li key={i} className="flex items-center flex-1">
            <div className="flex flex-col items-center flex-shrink-0">
              <div
                aria-current={current ? 'step' : undefined}
                className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold transition-colors ${
                  done ? 'bg-primary-600 text-white' : current ? 'bg-primary-600 text-white ring-4 ring-primary-100' : 'bg-slate-200 text-slate-500'
                }`}
              >
                {done ? <CheckCircle2 className="w-4 h-4" /> : i + 1}
              </div>
              <span className={`text-xs mt-1 font-medium ${current ? 'text-primary-600' : 'text-slate-400'}`}>
                {label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={`flex-1 h-0.5 mx-1 mb-4 ${done ? 'bg-primary-600' : 'bg-slate-200'}`} />
            )}
          </li>
        )
      })}
    </ol>
  )
}

// ------- Step 1: Find recipient -------
function StepFindRecipient({ onSelect, currentUserId }) {
  const [query, setQuery] = useState('')
  const [searched, setSearched] = useState(false)

  const { data: result, isFetching, error, refetch } = useQuery({
    queryKey: ['user-search', query],
    queryFn: () => transfersApi.searchUser(query),
    enabled: false,
  })

  async function handleSearch(e) {
    e.preventDefault()
    if (!query.trim()) return
    setSearched(true)
    refetch()
  }

  const isSelf = result?.userId === currentUserId

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-800">Who do you want to pay?</h2>
      <form onSubmit={handleSearch} className="flex gap-2">
        <Input
          placeholder="Phone number or email address"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setSearched(false) }}
          aria-label="Search recipient"
          className="flex-1"
        />
        <Button type="submit" loading={isFetching}>
          <Search className="w-4 h-4" /> Search
        </Button>
      </form>

      {searched && !isFetching && (
        <>
          {error ? (
            <div role="alert" className="flex items-center gap-2 text-sm text-red-600 p-3 bg-red-50 rounded-lg">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              User not found. Check the phone number or email.
            </div>
          ) : isSelf ? (
            <div role="alert" className="text-sm text-amber-700 p-3 bg-amber-50 rounded-lg">
              You cannot send money to yourself.
            </div>
          ) : result ? (
            <div className="border border-slate-200 rounded-xl p-4 flex items-center gap-4 bg-white">
              <div className="w-12 h-12 rounded-full bg-primary-600 text-white text-sm font-bold flex items-center justify-center flex-shrink-0">
                {getInitials(result.name)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-semibold text-slate-800">{result.name}</p>
                <p className="text-sm text-slate-500">{maskPhone(result.phone)}</p>
                <p className="text-xs text-slate-400">{result.email}</p>
              </div>
              <Button onClick={() => onSelect(result)} size="sm">
                Select
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}

// ------- Step 2: Amount -------
const amountSchema = z.object({
  amount: z
    .string()
    .min(1, 'Amount is required')
    .refine((v) => !isNaN(parseFloat(v)) && parseFloat(v) >= 1, 'Minimum ₹1')
    .refine((v) => parseFloat(v) <= 9999999999999.99, 'Amount too large'),
  remark: z.string().max(255).optional(),
})

function StepAmount({ recipient, wallet, onNext, onBack }) {
  const settlementDate = format(addDays(new Date(), 3), 'd MMM yyyy')
  const balance = parseFloat(wallet?.availableBalance || 0)

  const { register, handleSubmit, watch, formState: { errors } } = useForm({
    resolver: zodResolver(amountSchema),
  })

  const amount = parseFloat(watch('amount') || 0)
  const isLarge = amount > 8000
  const exceedsBalance = amount > balance

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <button onClick={onBack} aria-label="Back" className="p-2 rounded-lg hover:bg-slate-100">
          <ArrowLeft className="w-4 h-4 text-slate-500" />
        </button>
        <h2 className="text-lg font-semibold text-slate-800">
          How much to {recipient.name.split(' ')[0]}?
        </h2>
      </div>

      <div className="text-sm text-slate-500">
        Available: <span className="font-semibold text-slate-800">{formatINR(balance)}</span>
      </div>

      <form onSubmit={handleSubmit(onNext)} className="space-y-4">
        <Input
          label="Amount"
          type="number"
          min="1"
          step="0.01"
          prefix="₹"
          placeholder="0.00"
          error={errors.amount?.message || (exceedsBalance ? 'Insufficient balance' : undefined)}
          {...register('amount')}
        />
        <Input
          label="Remark (optional)"
          placeholder="What's this for?"
          maxLength={255}
          {...register('remark')}
        />

        {/* Settlement callout */}
        <div className="bg-primary-50 border border-primary-100 rounded-xl p-4 text-sm text-primary-800">
          <p className="font-medium mb-0.5">ACH-style settlement</p>
          <p className="text-primary-700">
            The amount leaves your wallet <strong>now</strong> and is credited to{' '}
            <strong>{recipient.name}</strong> on <strong>{settlementDate}</strong> (3 business days).
          </p>
        </div>

        {isLarge && (
          <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
            Large transfers above ₹8,000 may be reviewed by our fraud detection system.
          </p>
        )}

        <Button
          type="submit"
          className="w-full"
          size="lg"
          disabled={exceedsBalance}
        >
          Continue
        </Button>
      </form>
    </div>
  )
}

// ------- Step 3: Confirm with PIN -------
function StepConfirmPin({ recipient, amount, remark, onBack, onResult }) {
  const [pin, setPin] = useState('')
  const [pinError, setPinError] = useState(null)
  const requestIdRef = useRef(uuidv4())
  const settlementDate = format(addDays(new Date(), 3), 'd MMM yyyy')

  const mutation = useMutation({
    mutationFn: (payload) => transfersApi.send(payload),
    onSuccess: (data) => {
      onResult({ success: true, data })
    },
    onError: (err) => {
      const { message, status } = parseApiError(err)
      setPinError(null)
      if (message?.toLowerCase().includes('pin')) {
        setPinError('Incorrect PIN. Please try again.')
      } else {
        onResult({ success: false, message, status })
      }
    },
  })

  function handleSubmit(e) {
    e.preventDefault()
    if (pin.length < 4) { setPinError('Enter your 4-digit PIN'); return }
    setPinError(null)
    mutation.mutate({
      requestId: requestIdRef.current,
      receiverUserId: recipient.userId,
      amount: parseFloat(amount).toFixed(2),
      remark: remark || undefined,
      pin,
    })
  }

  function handleRetry() {
    // Reuse same requestId for idempotency
    mutation.reset()
    handleSubmit({ preventDefault: () => {} })
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-3">
        <button onClick={onBack} aria-label="Back" className="p-2 rounded-lg hover:bg-slate-100">
          <ArrowLeft className="w-4 h-4 text-slate-500" />
        </button>
        <h2 className="text-lg font-semibold text-slate-800">Confirm transfer</h2>
      </div>

      {/* Review summary */}
      <Card className="divide-y divide-slate-100">
        {[
          ['To', recipient.name],
          ['Amount', formatINR(amount)],
          ['Remark', remark || '—'],
          ['Settlement date', settlementDate],
        ].map(([k, v]) => (
          <div key={k} className="flex justify-between px-4 py-3 text-sm">
            <span className="text-slate-500">{k}</span>
            <span className="font-medium text-slate-800">{v}</span>
          </div>
        ))}
      </Card>

      <form onSubmit={handleSubmit} className="space-y-4">
        <PinInput
          value={pin}
          onChange={setPin}
          error={pinError}
          disabled={mutation.isPending}
        />
        <Button
          type="submit"
          className="w-full"
          size="lg"
          loading={mutation.isPending}
          disabled={pin.length < 4}
        >
          Send {formatINR(amount)}
        </Button>
        {mutation.isError && !pinError && (
          <button
            type="button"
            onClick={handleRetry}
            className="w-full flex items-center justify-center gap-2 text-sm text-primary-600 hover:underline"
          >
            <RefreshCw className="w-4 h-4" /> Retry same transfer
          </button>
        )}
      </form>
    </div>
  )
}

// ------- Result screen -------
function ResultScreen({ result, onSendAnother }) {
  const navigate = useNavigate()
  if (!result) return null

  if (result.success) {
    const { data } = result
    return (
      <div className="flex flex-col items-center text-center py-6 space-y-4">
        <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center">
          <CheckCircle2 className="w-8 h-8 text-green-600" />
        </div>
        <div>
          <p className="text-2xl font-bold text-slate-900">{formatINR(data.amount)}</p>
          <p className="text-slate-500 mt-1">sent to <span className="font-medium text-slate-700">{data.receiverName}</span></p>
        </div>
        <div className="flex items-center gap-2">
          <Badge status="PENDING" /> Settlement on {formatDateOnly(data.settlementDueDate)}
        </div>
        <div className="flex gap-3 pt-2">
          <Button variant="secondary" onClick={() => navigate(`/transactions/${data.transactionId}`)}>
            View transaction
          </Button>
          <Button onClick={onSendAnother}>
            <Send className="w-4 h-4" /> Send another
          </Button>
        </div>
      </div>
    )
  }

  // Failure
  const hints = {
    423: 'Your account is locked. Contact support.',
    403: 'Your wallet is inactive or frozen.',
  }
  const hint = hints[result.status] || null

  return (
    <div className="flex flex-col items-center text-center py-6 space-y-4">
      <div className="w-16 h-16 rounded-full bg-red-50 flex items-center justify-center">
        <AlertCircle className="w-8 h-8 text-red-500" />
      </div>
      <p className="text-lg font-semibold text-slate-900">Transfer failed</p>
      <p className="text-sm text-slate-500 max-w-xs">{result.message}</p>
      {hint && (
        <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">{hint}</p>
      )}
      <Button onClick={onSendAnother}>Try again</Button>
    </div>
  )
}

// ------- Main page -------
export default function SendMoneyPage() {
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [recipient, setRecipient] = useState(null)
  const [amountData, setAmountData] = useState(null)
  const [result, setResult] = useState(null)

  const { data: wallet } = useQuery({ queryKey: ['wallet'], queryFn: walletApi.get })

  const walletBlocked = wallet?.status === 'INACTIVE' || wallet?.status === 'FROZEN'

  if (result) {
    return (
      <div className="max-w-md mx-auto">
        <Card className="p-6">
          <ResultScreen result={result} onSendAnother={() => { setResult(null); setStep(0); setRecipient(null); setAmountData(null) }} />
        </Card>
      </div>
    )
  }

  if (walletBlocked) {
    return (
      <div className="max-w-md mx-auto">
        <Card className="p-8 flex flex-col items-center text-center gap-4">
          <AlertCircle className="w-12 h-12 text-amber-500" />
          <p className="font-semibold text-slate-800">
            Your wallet is {wallet.status.toLowerCase()}
          </p>
          <p className="text-sm text-slate-500">Transfers are disabled until your wallet is active.</p>
          {wallet.status === 'INACTIVE' && (
            <Button variant="secondary" onClick={() => window.location.href = '/wallet/settings'}>
              Reactivate wallet
            </Button>
          )}
        </Card>
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto">
      <Card className="p-6">
        <Stepper step={step} />
        {step === 0 && (
          <StepFindRecipient
            currentUserId={user?.id}
            onSelect={(r) => { setRecipient(r); setStep(1) }}
          />
        )}
        {step === 1 && (
          <StepAmount
            recipient={recipient}
            wallet={wallet}
            onBack={() => setStep(0)}
            onNext={(d) => { setAmountData(d); setStep(2) }}
          />
        )}
        {step === 2 && (
          <StepConfirmPin
            recipient={recipient}
            amount={amountData.amount}
            remark={amountData.remark}
            onBack={() => setStep(1)}
            onResult={setResult}
          />
        )}
      </Card>
    </div>
  )
}



import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, ShieldCheck, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../../features/auth/AuthContext.jsx'
import { parseApiError, applyFieldErrors } from '../../lib/errorHelpers.js'
import Input from '../../components/ui/Input.jsx'
import Button from '../../components/ui/Button.jsx'
import PinInput from '../../components/ui/PinInput.jsx'
import Card from '../../components/ui/Card.jsx'

const schema = z
  .object({
    name: z.string().min(2, 'Full name is required').max(100),
    email: z.string().email('Enter a valid email').max(150),
    phone: z.string().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit Indian mobile number'),
    password: z.string().min(8, 'Password must be at least 8 characters').max(64),
    confirmPassword: z.string(),
    pin: z.string().length(4, 'PIN must be exactly 4 digits').regex(/^\d{4}$/),
    confirmPin: z.string().length(4, 'PIN must be exactly 4 digits'),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })
  .refine((d) => d.pin === d.confirmPin, {
    message: 'PINs do not match',
    path: ['confirmPin'],
  })

function PasswordStrengthMeter({ password }) {
  const checks = [
    password?.length >= 8,
    /[A-Z]/.test(password || ''),
    /[0-9]/.test(password || ''),
    /[^A-Za-z0-9]/.test(password || ''),
  ]
  const strength = checks.filter(Boolean).length
  const colors = ['bg-red-400', 'bg-orange-400', 'bg-amber-400', 'bg-green-500']
  const labels = ['Weak', 'Fair', 'Good', 'Strong']

  if (!password) return null
  return (
    <div className="mt-1.5">
      <div className="flex gap-1">
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className={`h-1.5 flex-1 rounded-full transition-colors ${
              i < strength ? colors[strength - 1] : 'bg-slate-200'
            }`}
          />
        ))}
      </div>
      <p className="text-xs mt-1 text-slate-500">
        Strength: <span className="font-medium">{labels[strength - 1] || 'Very weak'}</span>
      </p>
    </div>
  )
}

function SuccessBanner({ name, email, onCreateAnother }) {
  return (
    <Card className="p-8 text-center space-y-4">
      <div className="flex justify-center">
        <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center">
          <CheckCircle2 className="w-8 h-8 text-green-600" />
        </div>
      </div>
      <div>
        <h3 className="text-lg font-semibold text-slate-900">Admin account created</h3>
        <p className="text-sm text-slate-500 mt-1">
          <span className="font-medium text-slate-700">{name}</span> ({email}) can now log in
          with admin privileges.
        </p>
      </div>
      <div className="pt-2">
        <Button onClick={onCreateAnother} variant="secondary">
          Create another admin
        </Button>
      </div>
    </Card>
  )
}

export default function AdminRegisterPage() {
  const { registerAdmin } = useAuth()
  const [showPwd, setShowPwd] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [serverError, setServerError] = useState(null)
  const [created, setCreated] = useState(null) // { name, email }

  const {
    register,
    handleSubmit,
    watch,
    setError,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { pin: '', confirmPin: '' },
  })

  const password = watch('password', '')
  const pin = watch('pin', '')
  const confirmPin = watch('confirmPin', '')

  async function onSubmit(data) {
    setServerError(null)
    try {
      await registerAdmin({
        name: data.name,
        email: data.email,
        phone: data.phone,
        password: data.password,
        pin: data.pin,
      })
      toast.success(`Admin account created for ${data.name}`)
      setCreated({ name: data.name, email: data.email })
    } catch (err) {
      const { message, fieldErrors, status } = parseApiError(err)
      if (status === 409) {
        setServerError(message)
      } else {
        applyFieldErrors(setError, fieldErrors)
        setServerError(fieldErrors && Object.keys(fieldErrors).length ? null : message)
      }
    }
  }

  function handleCreateAnother() {
    setCreated(null)
    setServerError(null)
    reset()
  }

  if (created) {
    return (
      <div className="space-y-5 max-w-lg">
        <h1 className="text-xl font-bold text-slate-900">Create admin account</h1>
        <SuccessBanner
          name={created.name}
          email={created.email}
          onCreateAnother={handleCreateAnother}
        />
      </div>
    )
  }

  return (
    <div className="space-y-5 max-w-lg">
      <div>
        <h1 className="text-xl font-bold text-slate-900">Create admin account</h1>
        <p className="text-sm text-slate-500 mt-1">
          New admins can log in immediately and have full access to the admin panel.
          This action is logged in the audit trail.
        </p>
      </div>

      {/* Security notice */}
      <div className="flex items-start gap-3 px-4 py-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
        <ShieldCheck className="w-4 h-4 flex-shrink-0 mt-0.5" />
        <span>
          Admin accounts have unrestricted access to all user data, transactions, and system
          controls. Only create accounts for trusted personnel.
        </span>
      </div>

      <Card className="p-6">
        {serverError && (
          <div
            role="alert"
            className="mb-5 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700"
          >
            {serverError}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
          {/* Personal info */}
          <Input
            label="Full name"
            placeholder="Admin Name"
            error={errors.name?.message}
            autoComplete="off"
            {...register('name')}
          />
          <Input
            label="Email address"
            type="email"
            placeholder="admin@example.com"
            error={errors.email?.message}
            autoComplete="off"
            {...register('email')}
          />
          <Input
            label="Mobile number"
            type="tel"
            prefix="+91"
            placeholder="9876543210"
            maxLength={10}
            error={errors.phone?.message}
            autoComplete="off"
            {...register('phone')}
          />

          <div className="border-t border-slate-100 pt-4 space-y-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">
              Login credentials
            </p>

            <div>
              <Input
                label="Password"
                type={showPwd ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                error={errors.password?.message}
                autoComplete="new-password"
                suffix={
                  <button
                    type="button"
                    onClick={() => setShowPwd((v) => !v)}
                    aria-label={showPwd ? 'Hide password' : 'Show password'}
                  >
                    {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                }
                {...register('password')}
              />
              <PasswordStrengthMeter password={password} />
            </div>

            <Input
              label="Confirm password"
              type={showConfirm ? 'text' : 'password'}
              error={errors.confirmPassword?.message}
              autoComplete="new-password"
              suffix={
                <button
                  type="button"
                  onClick={() => setShowConfirm((v) => !v)}
                  aria-label={showConfirm ? 'Hide password' : 'Show password'}
                >
                  {showConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              }
              {...register('confirmPassword')}
            />
          </div>

          <div className="border-t border-slate-100 pt-4 space-y-4">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">
              Transaction PIN
            </p>
            <p className="text-xs text-slate-500 -mt-2">
              Required for sending money. Admin accounts can still initiate transfers from their
              own wallet.
            </p>

            <PinInput
              label="Transaction PIN (4 digits)"
              value={pin}
              onChange={(v) => setValue('pin', v, { shouldValidate: true })}
              error={errors.pin?.message}
            />

            <PinInput
              label="Confirm PIN"
              value={confirmPin}
              onChange={(v) => setValue('confirmPin', v, { shouldValidate: true })}
              error={errors.confirmPin?.message}
            />
          </div>

          <div className="pt-2">
            <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
              <ShieldCheck className="w-4 h-4" />
              Create admin account
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}

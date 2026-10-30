import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, Shield } from 'lucide-react'
import { toast } from 'sonner'
import { useAuth } from '../features/auth/AuthContext.jsx'
import { parseApiError, applyFieldErrors } from '../lib/errorHelpers.js'
import Input from '../components/ui/Input.jsx'
import Button from '../components/ui/Button.jsx'
import PinInput from '../components/ui/PinInput.jsx'

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
    password.length >= 8,
    /[A-Z]/.test(password),
    /[0-9]/.test(password),
    /[^A-Za-z0-9]/.test(password),
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
            className={`h-1.5 flex-1 rounded-full transition-colors ${i < strength ? colors[strength - 1] : 'bg-slate-200'}`}
          />
        ))}
      </div>
      <p className="text-xs mt-1 text-slate-500">
        Strength: <span className="font-medium">{labels[strength - 1] || 'Very weak'}</span>
      </p>
    </div>
  )
}

export default function RegisterPage() {
  const { register: authRegister } = useAuth()
  const navigate = useNavigate()
  const [showPwd, setShowPwd] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const [serverError, setServerError] = useState(null)

  const {
  register,
  handleSubmit,
  watch,
  setError,
  setValue,
  formState: { errors, isSubmitting },
} = useForm({
  resolver: zodResolver(schema),
  defaultValues: { pin: '', confirmPin: '' },
})

const pin = watch('pin')
const confirmPin = watch('confirmPin')

  const password = watch('password', '')

  async function onSubmit(data) {
    setServerError(null)
    try {
      await authRegister({
        name: data.name,
        email: data.email,
        phone: data.phone,
        password: data.password,
        pin: data.pin,
      })
      toast.success('Wallet created! Please sign in.')
      navigate('/login')
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

  return (
    <div className="min-h-screen flex">
      {/* Brand panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-primary-600 to-primary-800 flex-col justify-center items-center p-12 text-white">
        <div className="w-16 h-16 rounded-2xl bg-white/20 backdrop-blur flex items-center justify-center mb-6">
          <Shield className="w-8 h-8 text-white" />
        </div>
        <h1 className="text-4xl font-bold mb-3">PayLedger</h1>
        <p className="text-primary-200 text-lg text-center max-w-xs">
          Secure peer-to-peer payments with ACH-style settlement. Send money instantly, settle in 3 days.
        </p>
        <div className="mt-10 space-y-4 text-sm">
          {['Instant debit, 3-day settlement', 'Daily ₹50,000 limit', 'Fraud detection built-in'].map((f) => (
            <div key={f} className="flex items-center gap-2 text-primary-100">
              <div className="w-1.5 h-1.5 rounded-full bg-primary-300" />
              {f}
            </div>
          ))}
        </div>
      </div>

      {/* Form panel */}
      <div className="flex-1 flex flex-col justify-center items-center p-6 overflow-y-auto">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-2 mb-6 lg:hidden">
            <Shield className="w-6 h-6 text-primary-600" />
            <span className="text-xl font-bold text-slate-900">PayLedger</span>
          </div>
          <h2 className="text-2xl font-bold text-slate-900 mb-1">Create your account</h2>
          <p className="text-slate-500 text-sm mb-6">
            A wallet is created automatically when you register.
          </p>

          {serverError && (
            <div role="alert" className="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
              {serverError}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <Input
              label="Full name"
              placeholder="Rahul Sharma"
              error={errors.name?.message}
              {...register('name')}
            />
            <Input
              label="Email address"
              type="email"
              placeholder="rahul@example.com"
              error={errors.email?.message}
              {...register('email')}
            />
            <Input
              label="Mobile number"
              type="tel"
              prefix="+91"
              placeholder="9876543210"
              maxLength={10}
              error={errors.phone?.message}
              {...register('phone')}
            />
            <div>
              <Input
                label="Password"
                type={showPwd ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                error={errors.password?.message}
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

            {/* PIN section */}
            <div className="pt-2 border-t border-slate-100">
              <p className="text-xs text-slate-500 mb-3">
                Your transaction PIN is separate from your password and is required every time you send money.
              </p>
             
           <PinInput
  label="Transaction PIN (4 digits)"
  value={pin}
  onChange={(v) => setValue('pin', v, { shouldValidate: true })}
  error={errors.pin?.message}
/>
            </div>
            <div>
              
             
<PinInput
  label="Confirm PIN"
  value={confirmPin}
  onChange={(v) => setValue('confirmPin', v, { shouldValidate: true })}
  error={errors.confirmPin?.message}
/>
            </div>

            <Button type="submit" loading={isSubmitting} className="w-full mt-2" size="lg">
              Create account
            </Button>
          </form>

          <p className="text-center text-sm text-slate-500 mt-5">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-600 font-medium hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

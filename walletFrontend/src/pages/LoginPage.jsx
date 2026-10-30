import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, Shield, Lock } from 'lucide-react'
import { useAuth } from '../features/auth/AuthContext.jsx'
import { parseApiError } from '../lib/errorHelpers.js'
import Input from '../components/ui/Input.jsx'
import Button from '../components/ui/Button.jsx'

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [showPwd, setShowPwd] = useState(false)
  const [serverError, setServerError] = useState(null)
  const [isLocked, setIsLocked] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(schema) })

  async function onSubmit(data) {
    setServerError(null)
    setIsLocked(false)
    try {
      const user = await login(data)
      navigate(user.role === 'ADMIN' ? '/admin' : '/', { replace: true })
    } catch (err) {
      const { message, status } = parseApiError(err)
      if (status === 423) {
        setIsLocked(true)
        setServerError(message)
      } else {
        setServerError(status === 401 ? 'Invalid email or password.' : message)
      }
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-12 h-12 rounded-xl bg-primary-600 flex items-center justify-center mb-3">
            <Shield className="w-6 h-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900">PayLedger</h1>
          <p className="text-slate-500 text-sm mt-1">Sign in to your wallet</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          {/* Locked banner */}
          {isLocked && (
            <div
              role="alert"
              aria-live="assertive"
              className="flex items-start gap-3 mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg"
            >
              <Lock className="w-4 h-4 text-red-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-red-700">{serverError}</p>
            </div>
          )}

          {/* Generic error */}
          {serverError && !isLocked && (
            <div role="alert" aria-live="polite" className="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
              {serverError}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            <Input
              label="Email address"
              type="email"
              placeholder="rahul@example.com"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
            <Input
              label="Password"
              type={showPwd ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Your password"
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
            <Button type="submit" loading={isSubmitting} className="w-full" size="lg">
              Sign in
            </Button>
          </form>
        </div>

        <p className="text-center text-sm text-slate-500 mt-5">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary-600 font-medium hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  )
}

import { useState } from 'react'
import styles from './FormInput.module.css'

export default function FormInput({
  label,
  id,
  type = 'text',
  error,
  hint,
  ...props
}) {
  const [showPwd, setShowPwd] = useState(false)
  const isPassword = type === 'password'
  const inputType = isPassword ? (showPwd ? 'text' : 'password') : type

  return (
    <div className={styles.group}>
      {label && <label htmlFor={id} className={styles.label}>{label}</label>}
      <div className={styles.inputWrap}>
        <input
          id={id}
          name={id}
          type={inputType}
          className={[styles.input, error ? styles.hasError : ''].join(' ')}
          autoComplete={isPassword ? 'current-password' : undefined}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            className={styles.eyeBtn}
            onClick={() => setShowPwd(v => !v)}
            aria-label={showPwd ? 'Hide password' : 'Show password'}
          >
            {showPwd ? '🙈' : '👁️'}
          </button>
        )}
      </div>
      {error && <span className={styles.error}>{error}</span>}
      {!error && hint && <span className={styles.hint}>{hint}</span>}
    </div>
  )
}

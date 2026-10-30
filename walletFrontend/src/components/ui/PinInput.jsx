import { useRef, forwardRef, useImperativeHandle } from 'react'

/**
 * 4-digit PIN input with masked boxes and auto-advance.
 * Usage: <PinInput value={pin} onChange={setPin} error={...} />
 */
const PinInput = forwardRef(function PinInput({ value = '', onChange, error, label = 'Transaction PIN', disabled }, ref) {
  const inputs = useRef([])

  useImperativeHandle(ref, () => ({
    clear: () => {
      onChange('')
      inputs.current[0]?.focus()
    },
  }))

  const digits = value.split('').concat(Array(4).fill('')).slice(0, 4)

  function handleChange(e, idx) {
    const char = e.target.value.replace(/\D/g, '').slice(-1)
    const next = [...digits]
    next[idx] = char
    const newVal = next.join('')
    onChange(newVal)
    if (char && idx < 3) inputs.current[idx + 1]?.focus()
  }

  function handleKeyDown(e, idx) {
    if (e.key === 'Backspace') {
      if (digits[idx]) {
        const next = [...digits]
        next[idx] = ''
        onChange(next.join(''))
      } else if (idx > 0) {
        inputs.current[idx - 1]?.focus()
      }
    }
    if (e.key === 'ArrowLeft' && idx > 0) inputs.current[idx - 1]?.focus()
    if (e.key === 'ArrowRight' && idx < 3) inputs.current[idx + 1]?.focus()
  }

  function handlePaste(e) {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 4)
    onChange(pasted.padEnd(4, '').slice(0, 4))
    inputs.current[Math.min(pasted.length, 3)]?.focus()
    e.preventDefault()
  }

  return (
    <div>
      {label && (
        <label className="block text-sm font-medium text-slate-700 mb-2">{label}</label>
      )}
      <div className="flex gap-3" role="group" aria-label={label}>
        {digits.map((d, i) => (
          <input
            key={i}
            ref={(el) => (inputs.current[i] = el)}
            type="password"
            inputMode="numeric"
            maxLength={1}
            value={d}
            onChange={(e) => handleChange(e, i)}
            onKeyDown={(e) => handleKeyDown(e, i)}
            onPaste={handlePaste}
            disabled={disabled}
            aria-label={`PIN digit ${i + 1}`}
            className={`w-12 h-12 text-center text-lg font-semibold rounded-xl border-2 bg-white transition-colors focus:outline-none focus:ring-2 focus:ring-primary-600 focus:border-primary-600 disabled:opacity-50 ${error ? 'border-red-400' : d ? 'border-primary-400' : 'border-slate-300'}`}
          />
        ))}
      </div>
      {error && (
        <p role="alert" className="mt-1 text-xs text-red-600">{error}</p>
      )}
    </div>
  )
})

export default PinInput

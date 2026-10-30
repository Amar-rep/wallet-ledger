/**
 * Extract a human-readable message and field errors from an Axios error.
 * Backend shape: { timestamp, status, error, message, path, fieldErrors }
 */
export function parseApiError(err) {
  const data = err?.response?.data
  const status = err?.response?.status
  if (!data) return { message: err?.message || 'Something went wrong', fieldErrors: {}, status }
  return {
    message: data.message || `HTTP ${status}`,
    fieldErrors: data.fieldErrors || {},
    status,
  }
}

/**
 * Apply backend fieldErrors onto a react-hook-form setError function.
 */
export function applyFieldErrors(setError, fieldErrors) {
  Object.entries(fieldErrors || {}).forEach(([field, message]) => {
    setError(field, { type: 'server', message })
  })
}

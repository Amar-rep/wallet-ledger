import { addDays } from 'date-fns'

const IST_TZ = 'Asia/Kolkata'

export function formatINR(amount) {
  const num = typeof amount === 'string' ? parseFloat(amount) : Number(amount)
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(isNaN(num) ? 0 : num)
}

export function formatDateTimeIST(isoString) {
  if (!isoString) return '—'
  try {
    const date = new Date(isoString)
    // Format in IST using Intl
    return new Intl.DateTimeFormat('en-IN', {
      timeZone: IST_TZ,
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }).format(date)
  } catch {
    return isoString
  }
}

export function formatDateOnly(localDateStr) {
  if (!localDateStr) return '—'
  // localDateStr is "YYYY-MM-DD" from LocalDate
  const [y, m, d] = localDateStr.split('-')
  const date = new Date(Number(y), Number(m) - 1, Number(d))
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

export function getInitials(name = '') {
  return name
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2)
}

export function maskPhone(phone = '') {
  if (phone.length < 4) return phone
  return phone.slice(0, 2) + '••••••' + phone.slice(-2)
}

export const DAILY_LIMIT = 50000

import styles from './AlertBanner.module.css'

/**
 * @param {'error'|'success'|'warning'} type
 */
export default function AlertBanner({ type = 'error', message }) {
  if (!message) return null
  return (
    <div className={[styles.banner, styles[type]].join(' ')} role="alert">
      <span className={styles.icon}>
        {type === 'error' && '✕'}
        {type === 'success' && '✓'}
        {type === 'warning' && '⚠'}
      </span>
      <p className={styles.text}>{message}</p>
    </div>
  )
}

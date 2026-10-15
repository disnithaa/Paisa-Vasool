import type { Money } from '../api/types'

const MONEY_INPUT = /^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/
const MONEY_VALUE = /^(-?)(0|[1-9]\d*)(?:\.(\d{1,2}))?$/

export function parseInrToPaise(value: string | number): bigint | null {
  const match = MONEY_VALUE.exec(String(value))
  if (!match) return null
  const [, sign, whole, fraction = ''] = match
  const paise = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0') || '0')
  return sign ? -paise : paise
}

export function parsePositiveInrToPaise(value: string): bigint | null {
  const normalized = value.trim()
  if (!MONEY_INPUT.test(normalized)) return null
  const [whole, fraction = ''] = normalized.split('.')
  const paise = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0') || '0')
  return paise > 0n ? paise : null
}

export function formatInr(value: Money): string {
  const match = MONEY_VALUE.exec(String(value))
  if (!match) return '₹—'

  const [, sign, wholeText, fraction = ''] = match
  const whole = BigInt(wholeText)
  const formatter = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })
  const signedWhole = sign ? -whole : whole
  const formattedWhole = formatter.format(signedWhole)
  const decimalSeparator = new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 1,
  }).formatToParts(1.1).find((part) => part.type === 'decimal')?.value ?? '.'
  const prefixSign = sign && whole === 0n ? '-' : ''
  return `${prefixSign}${formattedWhole}${decimalSeparator}${fraction.padEnd(2, '0')}`
}

import { describe, expect, it } from 'vitest'
import { formatInr, parseInrToPaise, parsePositiveInrToPaise } from './money'

describe('INR display and form validation', () => {
  it('formats amounts using exact paise and Indian digit grouping', () => {
    expect(formatInr('12345678.09')).toBe('₹1,23,45,678.09')
    expect(formatInr(250)).toBe('₹250.00')
    expect(formatInr('-0.25')).toBe('-₹0.25')
  })

  it('validates positive amounts and preserves paise with integers', () => {
    expect(parsePositiveInrToPaise('0.01')).toBe(1n)
    expect(parsePositiveInrToPaise('9999999999.99')).toBe(999999999999n)
    expect(parsePositiveInrToPaise('0')).toBeNull()
    expect(parsePositiveInrToPaise('-1.00')).toBeNull()
    expect(parsePositiveInrToPaise('1.001')).toBeNull()
    expect(parsePositiveInrToPaise('10000000000.00')).toBeNull()
  })

  it('parses signed backend balances without floating-point comparisons', () => {
    expect(parseInrToPaise('-0.01')).toBe(-1n)
    expect(parseInrToPaise('0.00')).toBe(0n)
    expect(parseInrToPaise('invalid')).toBeNull()
  })
})

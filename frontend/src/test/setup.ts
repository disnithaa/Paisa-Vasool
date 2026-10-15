import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setApiCredentials } from '../api/client'

afterEach(() => {
  cleanup()
  setApiCredentials(null)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

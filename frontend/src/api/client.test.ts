import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, setApiCredentials } from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Java API client', () => {
  afterEach(() => {
    setApiCredentials(null)
    vi.unstubAllGlobals()
  })

  it('sends Basic authentication to the group discovery endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([
      { id: 'group-1', name: 'Trip', createdAt: '2026-10-14T10:00:00Z' },
    ]))
    vi.stubGlobal('fetch', fetchMock)
    setApiCredentials({ email: 'dev@example.test', password: 'local-secret' })

    const groups = await api.getGroups()

    expect(groups).toHaveLength(1)
    const [url, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init?.headers)
    expect(url).toBe('/api/groups')
    expect(headers.get('Authorization')).toBe(`Basic ${btoa('dev@example.test:local-secret')}`)
    expect(headers.get('Accept')).toBe('application/json')
  })

  it('rejects malformed JSON and a response with an unexpected shape', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response('{broken', { status: 200 }))
      .mockResolvedValueOnce(jsonResponse({ groups: [] })))

    await expect(api.getGroups()).rejects.toMatchObject({
      name: 'ApiError',
      message: 'The API returned malformed JSON.',
    })
    await expect(api.getGroups()).rejects.toMatchObject({
      name: 'ApiError',
      message: 'The API response did not match the expected data format.',
    })
  })

  it('surfaces structured validation errors from Problem Details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      title: 'Bad Request',
      detail: 'Request validation failed',
      status: 400,
      violations: { name: 'must not be blank' },
    }, 400)))

    await expect(api.getGroups()).rejects.toEqual(expect.objectContaining({
      message: 'must not be blank',
      status: 400,
      violations: { name: 'must not be blank' },
    } satisfies Partial<ApiError>))
  })

  it('sends monetary input as a decimal string without browser-side arithmetic', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      id: 'purchase-1',
      groupId: 'group-1',
      createdById: 'user-1',
      createdByEmail: 'dev@example.test',
      title: 'Tickets',
      estimatedAmount: 12.3,
      status: 'DRAFT',
      approvedBy: null,
      approvedAt: null,
      cancelledBy: null,
      cancelledAt: null,
      completedBy: null,
      completedAt: null,
      completionType: null,
      createdAt: '2026-10-14T10:00:00Z',
    }))
    vi.stubGlobal('fetch', fetchMock)

    await api.createPurchase('group-1', 'Tickets', '12.30')

    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ title: 'Tickets', estimatedAmount: '12.30' }),
    }))
  })
})

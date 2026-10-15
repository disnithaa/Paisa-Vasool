import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Offer, Optimization, Purchase } from './api/types'

const groupId = 'group-1'
const purchaseId = 'purchase-1'
const offerId = 'offer-1'

const group = { id: groupId, name: 'Mountain weekend', createdAt: '2026-10-14T10:00:00Z' }
const member = {
  id: 'member-1',
  userId: 'user-1',
  name: 'Dev User',
  email: 'dev@example.test',
  role: 'OWNER' as const,
  status: 'ACTIVE' as const,
  joinedAt: '2026-10-14T10:00:00Z',
}
const offer: Offer = {
  id: offerId,
  merchantName: 'Sample Travel',
  title: 'Ten percent off',
  description: 'A demonstration offer',
  offerType: 'PERCENTAGE_DISCOUNT',
  discountPercentage: 10,
  discountAmount: null,
  minimumSpend: 0,
  maximumDiscount: 50,
  validFrom: '2026-10-01T00:00:00Z',
  validUntil: '2026-11-01T00:00:00Z',
  active: true,
  sample: true,
  terms: 'Fictional sample only',
}

function makePurchase(status: Purchase['status'] = 'DRAFT'): Purchase {
  return {
    id: purchaseId,
    groupId,
    createdById: 'user-1',
    createdByEmail: member.email,
    title: 'Train tickets',
    estimatedAmount: 250,
    status,
    approvedBy: null,
    approvedAt: null,
    cancelledBy: null,
    cancelledAt: null,
    completedBy: null,
    completedAt: null,
    completionType: null,
    createdAt: '2026-10-14T10:00:00Z',
  }
}

const recommendation: Optimization = {
  purchaseId,
  status: 'OPTIMAL',
  currency: 'INR',
  baseAmount: 250,
  selectedOfferId: offerId,
  selectedOffer: offer,
  discountAmount: 25,
  finalPayableAmount: 225,
  savingsAmount: 25,
  explanation: 'Selected the lowest payable eligible offer.',
  solver: { algorithm: 'ortools-cp-sat', solverStatus: 'OPTIMAL', baselineAgrees: true },
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function groupDataResponse(url: string, currentPurchase: Purchase, members = [member]): Response {
  if (url.endsWith('/members')) return jsonResponse(members)
  if (url.endsWith('/offers')) return jsonResponse([offer])
  if (url.endsWith('/purchases')) return jsonResponse([currentPurchase])
  if (url.endsWith('/expenses')) return jsonResponse([])
  if (url.endsWith('/balances')) {
    return jsonResponse({
      groupId,
      currency: 'INR',
      balanceConvention: 'Positive means receivable; negative means owed',
      totalBalance: 0,
      members: members.map((person) => ({
        member: { membershipId: person.id, userId: person.userId, name: person.name, email: person.email },
        membershipStatus: person.status,
        balance: 0,
      })),
    })
  }
  if (url.endsWith('/settlements')) return jsonResponse([])
  return jsonResponse({ detail: `Unexpected GET ${url}` }, 404)
}

describe('Paisa Vasool workspace', () => {
  it('shows loading feedback and an empty dashboard for a user with no groups', async () => {
    const user = userEvent.setup()
    let finishGroups: ((response: Response) => void) | undefined
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/groups')) {
        return new Promise<Response>((resolve) => { finishGroups = resolve })
      }
      return Promise.resolve(jsonResponse([]))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'dev@example.test')
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))
    expect(screen.getByRole('button', { name: 'Connecting…' }).hasAttribute('disabled')).toBe(true)

    finishGroups?.(jsonResponse([]))
    expect(await screen.findByText(/Create a group to start planning/)).toBeTruthy()
  })

  it('reports API authentication errors without pretending to sign in', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      title: 'Unauthorized',
      detail: 'Authentication is required',
      status: 401,
    }, 401)))
    render(<App />)
    await user.type(screen.getByLabelText('Email'), 'wrong@example.test')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))

    expect((await screen.findByRole('alert')).textContent).toContain('Authentication failed')
    expect(screen.getByRole('heading', { name: 'Welcome back.' })).toBeTruthy()
  })

  it('optimizes a draft, proposes the selected offer, and refreshes the workflow status', async () => {
    const user = userEvent.setup()
    let status: Purchase['status'] = 'DRAFT'
    let proposalCreated = false
    let releaseOptimization: ((response: Response) => void) | undefined
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.endsWith('/api/groups')) return Promise.resolve(jsonResponse([group]))
      if (url.includes(`/api/groups/${groupId}/purchases/${purchaseId}/optimization`)) {
        return new Promise<Response>((resolve) => { releaseOptimization = resolve })
      }
      if (url.endsWith(`/api/groups/${groupId}/purchases/${purchaseId}/proposal`) && method === 'POST') {
        proposalCreated = true
        status = 'PENDING_APPROVAL'
        return Promise.resolve(jsonResponse({
          purchaseId,
          groupId,
          createdById: member.userId,
          createdByEmail: member.email,
          title: 'Train tickets',
          estimatedAmount: 250,
          status,
          offer: { ...offer, offerId: offer.id },
          appliedDiscountAmount: 25,
          expectedFinalAmount: 225,
          proposedAt: '2026-10-14T10:01:00Z',
        }))
      }
      if (url.includes(`/api/groups/${groupId}/purchases/${purchaseId}/proposal`) && method === 'GET') {
        return Promise.resolve(jsonResponse({
          purchaseId,
          groupId,
          createdById: member.userId,
          createdByEmail: member.email,
          title: 'Train tickets',
          estimatedAmount: 250,
          status,
          offer: { ...offer, offerId: offer.id },
          appliedDiscountAmount: 25,
          expectedFinalAmount: 225,
          proposedAt: '2026-10-14T10:01:00Z',
        }))
      }
      if (url.endsWith(`/api/groups/${groupId}/purchases/${purchaseId}/approve`)) {
        status = 'APPROVED'
        return Promise.resolve(jsonResponse(makePurchase(status)))
      }
      if (url.endsWith(`/api/groups/${groupId}/purchases`)) return Promise.resolve(jsonResponse([makePurchase(status)]))
      if (url.includes(`/api/groups/${groupId}/`)) return Promise.resolve(groupDataResponse(url, makePurchase(status)))
      return Promise.resolve(jsonResponse({ detail: `Unexpected request ${method} ${url}` }, 404))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    await user.type(screen.getByLabelText('Email'), member.email)
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))
    expect(await screen.findByRole('heading', { name: 'Mountain weekend' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Purchase plans' }))
    await screen.findByRole('heading', { name: 'Train tickets' })

    const optimizeButton = screen.getByRole('button', { name: 'Find best offer' })
    await user.click(optimizeButton)
    expect(await screen.findByRole('button', { name: 'Optimizing…' })).toBeTruthy()
    await user.click(optimizeButton)
    const optimizationCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('/optimization'),
    )
    expect(optimizationCalls).toHaveLength(1)

    releaseOptimization?.(jsonResponse(recommendation))
    expect(await screen.findByText('Estimated payable')).toBeTruthy()
    expect(screen.getByText('Selected the lowest payable eligible offer.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Submit this offer for approval' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Submit this offer for approval' }))
    expect(await screen.findByText('PENDING APPROVAL')).toBeTruthy()
    expect(proposalCreated).toBe(true)

    await user.click(screen.getByRole('button', { name: 'View proposal details' }))
    expect(await screen.findByText(/Sample Travel · Ten percent off/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Approve' }))
    expect(await screen.findByText('APPROVED')).toBeTruthy()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/approve'))).toBe(true)
  })

  it('shows the baseline when no offer is selected and does not show a proposal action', async () => {
    const user = userEvent.setup()
    const noOfferRecommendation: Optimization = {
      ...recommendation,
      selectedOffer: null,
      selectedOfferId: null,
      discountAmount: 0,
      finalPayableAmount: 250,
      savingsAmount: 0,
      explanation: 'No beneficial eligible offer was found.',
    }
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/api/groups')) return Promise.resolve(jsonResponse([group]))
      if (url.endsWith('/optimization')) return Promise.resolve(jsonResponse(noOfferRecommendation))
      if (url.includes(`/api/groups/${groupId}/`)) return Promise.resolve(groupDataResponse(url, makePurchase()))
      return Promise.resolve(jsonResponse({ detail: 'Not found' }, 404))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await user.type(screen.getByLabelText('Email'), member.email)
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))
    await screen.findByRole('heading', { name: 'Mountain weekend' })
    await user.click(screen.getByRole('button', { name: 'Purchase plans' }))
    await screen.findByRole('heading', { name: 'Train tickets' })
    await user.click(screen.getByRole('button', { name: 'Find best offer' }))

    expect(await screen.findByText('Baseline — no offer selected')).toBeTruthy()
    expect(screen.getByText('No beneficial eligible offer was found.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Submit this offer for approval' })).toBeNull()
  })

  it('shows an optimizer service failure without presenting a recommendation', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/api/groups')) return Promise.resolve(jsonResponse([group]))
      if (url.endsWith('/optimization')) {
        return Promise.resolve(jsonResponse({
          title: 'Service Unavailable',
          detail: 'A valid optimization recommendation is temporarily unavailable',
          status: 503,
        }, 503))
      }
      if (url.includes(`/api/groups/${groupId}/`)) return Promise.resolve(groupDataResponse(url, makePurchase()))
      return Promise.resolve(jsonResponse([]))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await user.type(screen.getByLabelText('Email'), member.email)
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))
    await screen.findByRole('heading', { name: 'Mountain weekend' })
    await user.click(screen.getByRole('button', { name: 'Purchase plans' }))
    await screen.findByRole('heading', { name: 'Train tickets' })
    await user.click(screen.getByRole('button', { name: 'Find best offer' }))

    expect((await screen.findByRole('status')).textContent).toContain('The optimizer is unavailable')
    expect(screen.queryByText('Baseline — no offer selected')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Submit this offer for approval' })).toBeNull()
  })

  it('validates expense allocations before sending any ledger mutation', async () => {
    const user = userEvent.setup()
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/api/groups')) return Promise.resolve(jsonResponse([group]))
      if (url.includes(`/api/groups/${groupId}/`)) return Promise.resolve(groupDataResponse(url, makePurchase()))
      if (init?.method === 'POST') return Promise.resolve(jsonResponse({ detail: 'Unexpected post' }, 404))
      return Promise.resolve(jsonResponse([]))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    await user.type(screen.getByLabelText('Email'), member.email)
    await user.type(screen.getByLabelText('Password'), 'secret')
    await user.click(screen.getByRole('button', { name: 'Continue securely' }))
    await screen.findByRole('heading', { name: 'Mountain weekend' })
    await user.click(screen.getByRole('button', { name: 'Expenses & balances' }))
    await screen.findByRole('heading', { name: 'Expenses and balances' })

    await user.type(screen.getByLabelText('Description'), 'Lunch')
    await user.type(screen.getByLabelText('Total amount (INR)'), '10.00')
    await user.selectOptions(screen.getByLabelText('Paid by'), member.id)
    await user.selectOptions(screen.getByLabelText('Member'), member.id)
    await user.type(screen.getByLabelText('Share (INR)'), '9.99')
    await user.click(screen.getByRole('button', { name: 'Record expense' }))

    expect(screen.getByRole('alert').textContent).toContain('Allocations must add up exactly')
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/expenses') && init?.method === 'POST')).toBe(false)
  })
})

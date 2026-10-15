import type {
  Expense,
  Group,
  GroupBalances,
  Member,
  Offer,
  OfferEligibility,
  Optimization,
  ProblemDetails,
  Purchase,
  PurchaseProposal,
  Settlement,
} from './types'

interface Credentials {
  email: string
  password: string
}

export class ApiError extends Error {
  readonly status: number
  readonly violations?: Record<string, string>

  constructor(
    message: string,
    status: number,
    violations?: Record<string, string>,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.violations = violations
  }
}

let credentials: Credentials | null = null

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const baseUrl = (configuredBaseUrl || '/api').replace(/\/+$/, '')

export function setApiCredentials(next: Credentials | null): void {
  credentials = next
}

function basicAuthorization({ email, password }: Credentials): string {
  const bytes = new TextEncoder().encode(`${email}:${password}`)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return `Basic ${btoa(binary)}`
}

async function request<T>(
  path: string,
  validate: (value: unknown) => value is T,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  if (credentials) headers.set('Authorization', basicAuthorization(credentials))

  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, headers })
  } catch {
    throw new ApiError('Cannot reach the Paisa Vasool API. Check that the backend is running.', 0)
  }

  const text = await response.text()
  let body: unknown = null
  if (text.length > 0) {
    try {
      body = JSON.parse(text) as unknown
    } catch {
      throw new ApiError(
        response.ok ? 'The API returned malformed JSON.' : `The API returned HTTP ${response.status}.`,
        response.status,
      )
    }
  }

  if (!response.ok) {
    const problem = isRecord(body) ? (body as ProblemDetails) : {}
    const validationMessage = problem.violations
      ? Object.values(problem.violations).join(' ')
      : undefined
    throw new ApiError(
      validationMessage || problem.detail || problem.title || `The API returned HTTP ${response.status}.`,
      response.status,
      problem.violations,
    )
  }
  if (!validate(body)) throw new ApiError('The API response did not match the expected data format.', response.status)
  return body
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasString(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'string'
}

function hasMoney(value: Record<string, unknown>, key: string): boolean {
  return typeof value[key] === 'string' || typeof value[key] === 'number'
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function hasNullableMoney(value: Record<string, unknown>, key: string): boolean {
  return value[key] === null || hasMoney(value, key)
}

function isActor(value: unknown): boolean {
  return value === null
    || (isRecord(value) && ['membershipId', 'userId', 'name', 'email'].every((key) => hasString(value, key)))
}

const isGroup = (value: unknown): value is Group =>
  isRecord(value) && ['id', 'name', 'createdAt'].every((key) => hasString(value, key))
const isMember = (value: unknown): value is Member =>
  isRecord(value) && ['id', 'userId', 'name', 'email', 'role', 'status', 'joinedAt'].every((key) => hasString(value, key))
const isOffer = (value: unknown): value is Offer =>
  isRecord(value)
  && ['id', 'merchantName', 'title', 'offerType', 'validFrom', 'validUntil'].every((key) => hasString(value, key))
  && isNullableString(value.description)
  && isNullableString(value.terms)
  && hasMoney(value, 'minimumSpend')
  && hasNullableMoney(value, 'discountPercentage')
  && hasNullableMoney(value, 'discountAmount')
  && hasNullableMoney(value, 'maximumDiscount')
  && typeof value.active === 'boolean'
  && typeof value.sample === 'boolean'
const isPurchase = (value: unknown): value is Purchase =>
  isRecord(value)
  && ['id', 'groupId', 'createdById', 'createdByEmail', 'title', 'status', 'createdAt'].every((key) => hasString(value, key))
  && hasMoney(value, 'estimatedAmount')
  && isActor(value.approvedBy)
  && isNullableString(value.approvedAt)
  && isActor(value.cancelledBy)
  && isNullableString(value.cancelledAt)
  && isActor(value.completedBy)
  && isNullableString(value.completedAt)
  && isNullableString(value.completionType)
function isLedgerMember(value: unknown): boolean {
  return isRecord(value)
    && ['membershipId', 'userId', 'name', 'email'].every((key) => hasString(value, key))
}
const isExpense = (value: unknown): value is Expense =>
  isRecord(value)
  && ['id', 'groupId', 'description', 'incurredAt', 'createdAt'].every((key) => hasString(value, key))
  && hasMoney(value, 'totalAmount')
  && isNullableString(value.purchaseId)
  && isLedgerMember(value.paidBy)
  && isLedgerMember(value.createdBy)
  && Array.isArray(value.allocations)
  && value.allocations.every((allocation) =>
    isRecord(allocation) && isLedgerMember(allocation.member) && hasMoney(allocation, 'amount'),
  )
const isSettlement = (value: unknown): value is Settlement =>
  isRecord(value)
  && ['id', 'groupId', 'settledAt', 'createdAt'].every((key) => hasString(value, key))
  && hasMoney(value, 'amount')
  && isLedgerMember(value.fromMember)
  && isLedgerMember(value.toMember)
  && isLedgerMember(value.createdBy)

function isArrayOf<T>(value: unknown, guard: (item: unknown) => item is T): value is T[] {
  return Array.isArray(value) && value.every(guard)
}

const isOptimization = (value: unknown): value is Optimization =>
  isRecord(value)
  && hasString(value, 'purchaseId')
  && hasString(value, 'status')
  && hasString(value, 'currency')
  && hasString(value, 'explanation')
  && hasMoney(value, 'baseAmount')
  && hasMoney(value, 'discountAmount')
  && hasMoney(value, 'finalPayableAmount')
  && hasMoney(value, 'savingsAmount')
  && (value.selectedOfferId === null || typeof value.selectedOfferId === 'string')
  && (value.selectedOffer === null || isOffer(value.selectedOffer))
  && value.solver !== null
  && isRecord(value.solver)
  && hasString(value.solver, 'algorithm')
  && hasString(value.solver, 'solverStatus')
  && typeof value.solver.baselineAgrees === 'boolean'

function isProposal(value: unknown): value is PurchaseProposal {
  if (!isRecord(value) || !isRecord(value.offer)) return false
  const offer = value.offer
  return ['purchaseId', 'groupId', 'createdById', 'createdByEmail', 'title', 'status', 'proposedAt']
      .every((key) => hasString(value, key))
    && hasMoney(value, 'estimatedAmount')
    && hasMoney(value, 'appliedDiscountAmount')
    && hasMoney(value, 'expectedFinalAmount')
    && ['offerId', 'merchantName', 'title', 'offerType', 'validFrom', 'validUntil']
      .every((key) => hasString(offer, key))
    && hasMoney(offer, 'minimumSpend')
    && hasNullableMoney(offer, 'discountPercentage')
    && hasNullableMoney(offer, 'discountAmount')
    && hasNullableMoney(offer, 'maximumDiscount')
    && isNullableString(offer.description)
    && isNullableString(offer.terms)
}

const isOfferEligibility = (value: unknown): value is OfferEligibility =>
  isRecord(value) && isOffer(value.offer) && typeof value.eligible === 'boolean'

const isGroupBalances = (value: unknown): value is GroupBalances =>
  isRecord(value)
  && hasString(value, 'groupId')
  && hasString(value, 'currency')
  && hasString(value, 'balanceConvention')
  && hasMoney(value, 'totalBalance')
  && Array.isArray(value.members)
  && value.members.every((member) =>
    isRecord(member)
    && hasMoney(member, 'balance')
    && typeof member.membershipStatus === 'string'
    && isLedgerMember(member.member),
  )

const isMemberArray = (value: unknown): value is Member[] => isArrayOf(value, isMember)
const isOfferArray = (value: unknown): value is Offer[] => isArrayOf(value, isOffer)
const isPurchaseArray = (value: unknown): value is Purchase[] => isArrayOf(value, isPurchase)
const isExpenseArray = (value: unknown): value is Expense[] => isArrayOf(value, isExpense)
const isEligibilityArray = (value: unknown): value is OfferEligibility[] => isArrayOf(value, isOfferEligibility)

const json = (body: unknown): string => JSON.stringify(body)

export const api = {
  getGroups: () => request('/groups', (value): value is Group[] => isArrayOf(value, isGroup)),
  createGroup: (name: string) =>
    request('/groups', isGroup, { method: 'POST', body: json({ name }) }),
  getMembers: (groupId: string) =>
    request(`/groups/${groupId}/members`, isMemberArray),
  addMember: (groupId: string, name: string, email: string) =>
    request(`/groups/${groupId}/members`, isMember, {
      method: 'POST',
      body: json({ name, email }),
    }),
  getOffers: (groupId: string) =>
    request(`/groups/${groupId}/offers`, isOfferArray),
  getPurchases: (groupId: string) =>
    request(`/groups/${groupId}/purchases`, isPurchaseArray),
  createPurchase: (groupId: string, title: string, estimatedAmount: string) =>
    request(`/groups/${groupId}/purchases`, isPurchase, {
      method: 'POST',
      body: json({ title, estimatedAmount }),
    }),
  getEligibleOffers: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/eligible-offers`, isEligibilityArray),
  getProposal: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/proposal`, isProposal),
  optimizePurchase: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/optimization`, isOptimization, {
      method: 'POST',
    }),
  proposeOffer: (groupId: string, purchaseId: string, offerId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/proposal`, isProposal, {
      method: 'POST',
      body: json({ offerId }),
    }),
  approvePurchase: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/approve`, isPurchase, { method: 'POST' }),
  cancelPurchase: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/cancel`, isPurchase, { method: 'POST' }),
  completePurchaseSimulated: (groupId: string, purchaseId: string) =>
    request(`/groups/${groupId}/purchases/${purchaseId}/complete-simulated`, isPurchase, { method: 'POST' }),
  getExpenses: (groupId: string) =>
    request(`/groups/${groupId}/expenses`, isExpenseArray),
  createExpense: (
    groupId: string,
    body: {
      description: string
      totalAmount: string
      paidByMembershipId: string
      allocations: { membershipId: string; amount: string }[]
    },
  ) =>
    request(`/groups/${groupId}/expenses`, isExpense, { method: 'POST', body: json(body) }),
  getBalances: (groupId: string) =>
    request(`/groups/${groupId}/balances`, isGroupBalances),
  getSettlements: (groupId: string) =>
    request(`/groups/${groupId}/settlements`, (value): value is Settlement[] => isArrayOf(value, isSettlement)),
  createSettlement: (
    groupId: string,
    body: { fromMembershipId: string; toMembershipId: string; amount: string },
  ) =>
    request(`/groups/${groupId}/settlements`, isSettlement, { method: 'POST', body: json(body) }),
}

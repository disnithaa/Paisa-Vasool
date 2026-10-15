export type Money = string | number

export interface Group {
  id: string
  name: string
  createdAt: string
}

export interface Member {
  id: string
  userId: string
  name: string
  email: string
  role: 'OWNER' | 'MEMBER'
  status: 'ACTIVE' | 'LEFT'
  joinedAt: string
}

export interface Offer {
  id: string
  merchantName: string
  title: string
  description: string
  offerType: 'PERCENTAGE_DISCOUNT' | 'FIXED_DISCOUNT'
  discountPercentage: Money | null
  discountAmount: Money | null
  minimumSpend: Money
  maximumDiscount: Money | null
  validFrom: string
  validUntil: string
  active: boolean
  sample: boolean
  terms: string
}

export interface OfferEligibility {
  offer: Offer
  eligible: boolean
  ineligibilityReason: string | null
  discountAmount: Money | null
  finalAmount: Money | null
}

export type PurchaseStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'CANCELLED'
  | 'COMPLETED'

export interface Purchase {
  id: string
  groupId: string
  createdById: string
  createdByEmail: string
  title: string
  estimatedAmount: Money
  status: PurchaseStatus
  approvedBy: PurchaseActor | null
  approvedAt: string | null
  cancelledBy: PurchaseActor | null
  cancelledAt: string | null
  completedBy: PurchaseActor | null
  completedAt: string | null
  completionType: 'SIMULATED' | null
  createdAt: string
}

export interface PurchaseActor {
  membershipId: string
  userId: string
  name: string
  email: string
}

export interface PurchaseProposal {
  purchaseId: string
  groupId: string
  createdById: string
  createdByEmail: string
  title: string
  estimatedAmount: Money
  status: PurchaseStatus
  offer: {
    offerId: string
    merchantName: string
    title: string
    description: string
    offerType: Offer['offerType']
    discountPercentage: Money | null
    discountAmount: Money | null
    minimumSpend: Money
    maximumDiscount: Money | null
    validFrom: string
    validUntil: string
    terms: string
  }
  appliedDiscountAmount: Money
  expectedFinalAmount: Money
  proposedAt: string
}

export interface OptimizationSolver {
  algorithm: string
  solverStatus: string
  baselineAgrees: boolean
}

export interface Optimization {
  purchaseId: string
  status: string
  currency: string
  baseAmount: Money
  selectedOfferId: string | null
  selectedOffer: Offer | null
  discountAmount: Money
  finalPayableAmount: Money
  savingsAmount: Money
  explanation: string
  solver: OptimizationSolver
}

export interface LedgerMember {
  membershipId: string
  userId: string
  name: string
  email: string
}

export interface ExpenseAllocation {
  member: LedgerMember
  amount: Money
}

export interface Expense {
  id: string
  groupId: string
  purchaseId: string | null
  description: string
  totalAmount: Money
  paidBy: LedgerMember
  createdBy: LedgerMember
  incurredAt: string
  createdAt: string
  allocations: ExpenseAllocation[]
}

export interface MemberBalance {
  member: LedgerMember
  membershipStatus: 'ACTIVE' | 'LEFT'
  balance: Money
}

export interface GroupBalances {
  groupId: string
  currency: string
  balanceConvention: string
  totalBalance: Money
  members: MemberBalance[]
}

export interface Settlement {
  id: string
  groupId: string
  fromMember: LedgerMember
  toMember: LedgerMember
  amount: Money
  createdBy: LedgerMember
  settledAt: string
  createdAt: string
}

export interface ProblemDetails {
  title?: string
  detail?: string
  status?: number
  violations?: Record<string, string>
}

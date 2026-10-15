import { useState, type FormEvent } from 'react'
import type {
  OfferEligibility,
  Optimization,
  Purchase,
  PurchaseProposal,
} from '../api/types'
import { EmptyState, MoneyValue, SectionHeading, StatusBadge } from '../components/ui'
import { parsePositiveInrToPaise } from '../utils/money'

export function PurchasesView({
  groupId,
  purchases,
  busy,
  optimizations,
  onCreate,
  onOptimize,
  onEligibleOffers,
  onProposal,
  onApprove,
  onCancel,
  onComplete,
  onGetProposal,
}: {
  groupId: string
  purchases: Purchase[]
  busy: string
  optimizations: Record<string, Optimization>
  onCreate: (title: string, amount: string) => Promise<boolean>
  onOptimize: (purchaseId: string) => Promise<Optimization | undefined>
  onEligibleOffers: (purchaseId: string) => Promise<OfferEligibility[] | undefined>
  onProposal: (purchaseId: string, offerId: string) => Promise<void>
  onApprove: (purchaseId: string) => Promise<void>
  onCancel: (purchaseId: string) => Promise<void>
  onComplete: (purchaseId: string) => Promise<void>
  onGetProposal: (purchaseId: string) => Promise<PurchaseProposal | undefined>
}) {
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [formError, setFormError] = useState('')
  const [eligibility, setEligibility] = useState<Record<string, OfferEligibility[]>>({})
  const [proposals, setProposals] = useState<Record<string, PurchaseProposal>>({})

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!title.trim()) return setFormError('Enter a purchase title.')
    if (parsePositiveInrToPaise(amount) === null) {
      return setFormError('Enter a positive INR amount with no more than two decimal places.')
    }
    setFormError('')
    if (await onCreate(title.trim(), amount.trim())) {
      setTitle('')
      setAmount('')
    }
  }

  async function loadEligibleOffers(purchaseId: string) {
    const result = await onEligibleOffers(purchaseId)
    if (result) setEligibility((current) => ({ ...current, [purchaseId]: result }))
  }

  async function optimize(purchaseId: string) {
    await onOptimize(purchaseId)
  }

  async function showProposal(purchaseId: string) {
    const proposal = await onGetProposal(purchaseId)
    if (proposal) setProposals((current) => ({ ...current, [purchaseId]: proposal }))
  }

  return (
    <section className="view-stack">
      <SectionHeading eyebrow="PLAN BEFORE YOU SPEND" title="Purchase plans" />
      <div className="panel">
        <h3>Create a draft purchase</h3>
        <p className="note">This creates a planning estimate only. It does not record an expense or payment.</p>
        <form className="form-row purchase-create" onSubmit={submit}>
          <label className="field-grow" htmlFor="purchase-title">Purchase title
            <input id="purchase-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required />
          </label>
          <label htmlFor="purchase-amount">Estimated amount (INR)
            <input id="purchase-amount" inputMode="decimal" placeholder="0.00" value={amount} onChange={(event) => setAmount(event.target.value)} required />
          </label>
          <button className="button button-primary" disabled={!groupId || busy !== ''}>{busy === 'create-purchase' ? 'Creating…' : 'Create draft'}</button>
        </form>
        {formError && <p role="alert" className="error-message">{formError}</p>}
      </div>

      {purchases.length === 0
        ? <div className="panel"><EmptyState>No purchases in this group yet.</EmptyState></div>
        : <div className="purchase-list">
          {purchases.map((purchase) => {
            const recommendation = optimizations[purchase.id]
            const eligibleOffers = eligibility[purchase.id]
            const proposal = proposals[purchase.id]
            return (
              <article className="panel purchase-card" key={purchase.id}>
                <div className="purchase-card-heading">
                  <div><p className="eyebrow">{purchase.createdByEmail}</p><h3>{purchase.title}</h3></div>
                  <StatusBadge status={purchase.status} />
                </div>
                <div className="purchase-price"><span>Estimated purchase</span><strong><MoneyValue amount={purchase.estimatedAmount} /></strong></div>

                {purchase.status === 'DRAFT' && (
                  <div className="button-row">
                    <button className="button button-primary" onClick={() => void optimize(purchase.id)} disabled={busy !== ''}>
                      {busy === `optimize-${purchase.id}` ? 'Optimizing…' : 'Find best offer'}
                    </button>
                    <button className="button button-secondary" onClick={() => void loadEligibleOffers(purchase.id)} disabled={busy !== ''}>
                      Check eligible offers
                    </button>
                    <button className="button button-quiet" onClick={() => void onCancel(purchase.id)} disabled={busy !== ''}>Cancel draft</button>
                  </div>
                )}

                {recommendation && (
                  <div className="recommendation" aria-live="polite">
                    <div className="recommendation-heading">
                      <div><span className="recommendation-tag">OPTIMIZATION RECOMMENDATION</span><h4>{recommendation.selectedOffer ? recommendation.selectedOffer.title : 'Baseline — no offer selected'}</h4></div>
                      <span className="saving-amount">Save <MoneyValue amount={recommendation.savingsAmount} /></span>
                    </div>
                    <div className="price-breakdown">
                      <span>Original <strong><MoneyValue amount={recommendation.baseAmount} /></strong></span>
                      <span>Discount <strong>−<MoneyValue amount={recommendation.discountAmount} /></strong></span>
                      <span>Estimated payable <strong><MoneyValue amount={recommendation.finalPayableAmount} /></strong></span>
                    </div>
                    <p>{recommendation.explanation}</p>
                    <p className="note">{recommendation.solver.algorithm} · {recommendation.solver.solverStatus}</p>
                    {recommendation.selectedOfferId && (
                      <button className="button button-secondary" onClick={() => void onProposal(purchase.id, recommendation.selectedOfferId!)} disabled={busy !== ''}>
                        {busy === `proposal-${purchase.id}` ? 'Submitting…' : 'Submit this offer for approval'}
                      </button>
                    )}
                    {!recommendation.selectedOfferId && <p className="note">The baseline is the lowest valid option. No offer proposal was created.</p>}
                  </div>
                )}

                {eligibleOffers && (
                  <div className="eligibility-list">
                    <h4>Offer eligibility at current purchase amount</h4>
                    {eligibleOffers.length === 0
                      ? <EmptyState>No catalog candidates were returned.</EmptyState>
                      : eligibleOffers.map((candidate) => (
                        <div className="eligibility-row" key={candidate.offer.id}>
                          <div><strong>{candidate.offer.title}</strong><small>{candidate.offer.merchantName}</small></div>
                          <div className="eligibility-result">
                            {candidate.eligible
                              ? <><span>Eligible · discount <MoneyValue amount={candidate.discountAmount ?? '0.00'} /> · payable <MoneyValue amount={candidate.finalAmount ?? purchase.estimatedAmount} /></span><button className="text-button" onClick={() => void onProposal(purchase.id, candidate.offer.id)} disabled={busy !== ''}>Propose offer</button></>
                              : <span>Not eligible · {candidate.ineligibilityReason?.replaceAll('_', ' ').toLowerCase()}</span>}
                          </div>
                        </div>
                      ))}
                  </div>
                )}

                {purchase.status !== 'DRAFT' && purchase.status !== 'CANCELLED' && (
                  <button className="text-button" onClick={() => void showProposal(purchase.id)} disabled={busy !== ''}>
                    {proposal ? 'Refresh proposal details' : 'View proposal details'}
                  </button>
                )}
                {proposal && (
                  <div className="proposal-summary">
                    <strong>{proposal.offer.merchantName} · {proposal.offer.title}</strong>
                    <span>Discount <MoneyValue amount={proposal.appliedDiscountAmount} /> · expected payable <MoneyValue amount={proposal.expectedFinalAmount} /></span>
                  </div>
                )}

                <div className="button-row workflow-actions">
                  {purchase.status === 'PENDING_APPROVAL' && <>
                    <button className="button button-primary" onClick={() => void onApprove(purchase.id)} disabled={busy !== ''}>{busy === `approve-${purchase.id}` ? 'Approving…' : 'Approve'}</button>
                    <button className="button button-quiet" onClick={() => void onCancel(purchase.id)} disabled={busy !== ''}>Cancel pending purchase</button>
                  </>}
                  {purchase.status === 'APPROVED' && <>
                    <button className="button button-primary" onClick={() => void onComplete(purchase.id)} disabled={busy !== ''}>{busy === `complete-${purchase.id}` ? 'Recording…' : 'Mark simulated complete'}</button>
                    <button className="button button-quiet" onClick={() => void onCancel(purchase.id)} disabled={busy !== ''}>Cancel approved purchase</button>
                  </>}
                  {purchase.status === 'COMPLETED' && <span className="note">Completed as simulated. This is not proof of spending.</span>}
                </div>
              </article>
            )
          })}
        </div>}
    </section>
  )
}

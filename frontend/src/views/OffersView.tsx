import type { Offer } from '../api/types'
import { EmptyState, MoneyValue, SectionHeading } from '../components/ui'
import { formatInr } from '../utils/money'

function discountLabel(offer: Offer): string {
  if (offer.offerType === 'PERCENTAGE_DISCOUNT') {
    return `${offer.discountPercentage}% off${offer.maximumDiscount !== null ? ` · up to ${formatInr(offer.maximumDiscount)}` : ''}`
  }
  return `${offer.discountAmount !== null ? formatInr(offer.discountAmount) : 'Fixed discount'} off`
}

export function OffersView({ offers }: { offers: Offer[] }) {
  return (
    <section className="view-stack">
      <SectionHeading eyebrow="CURATED CATALOG" title="Offers" />
      <p className="note">The catalog endpoint returns offers that are active and within their validity window. Final eligibility is checked against each purchase by Java.</p>
      {offers.length === 0
        ? <div className="panel"><EmptyState>No active offers are available from the backend right now.</EmptyState></div>
        : <div className="offer-grid">
          {offers.map((offer) => (
            <article className="offer-card" key={offer.id}>
              <div className="offer-topline"><span>{offer.merchantName}</span>{offer.sample && <span className="sample-label">SAMPLE</span>}</div>
              <h3>{offer.title}</h3>
              {offer.description && <p>{offer.description}</p>}
              <strong className="offer-discount">{discountLabel(offer)}</strong>
              <dl className="offer-facts">
                <div><dt>Minimum spend</dt><dd><MoneyValue amount={offer.minimumSpend} /></dd></div>
                {offer.maximumDiscount !== null && <div><dt>Maximum discount</dt><dd><MoneyValue amount={offer.maximumDiscount} /></dd></div>}
                <div><dt>Valid until</dt><dd>{new Date(offer.validUntil).toLocaleDateString()}</dd></div>
              </dl>
              {offer.terms && <details><summary>Terms and conditions</summary><p>{offer.terms}</p></details>}
            </article>
          ))}
        </div>}
    </section>
  )
}

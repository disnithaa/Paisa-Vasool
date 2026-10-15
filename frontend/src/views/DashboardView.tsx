import type { Group, GroupBalances, Purchase } from '../api/types'
import { EmptyState, MoneyValue, SectionHeading, StatusBadge } from '../components/ui'
import { parseInrToPaise } from '../utils/money'

export function DashboardView({
  group,
  purchases,
  balances,
  dataAvailable,
}: {
  group: Group | null
  purchases: Purchase[]
  balances: GroupBalances | null
  dataAvailable: boolean
}) {
  if (!group) {
    return (
      <section className="panel">
        <SectionHeading eyebrow="YOUR OVERVIEW" title="Dashboard" />
        <EmptyState>Create a group to start planning purchases and tracking a shared ledger.</EmptyState>
      </section>
    )
  }

  const outstanding = balances?.members.filter((entry) => {
    const balance = parseInrToPaise(entry.balance)
    return balance !== null && balance !== 0n
  }) ?? []

  return (
    <section className="view-stack">
      <SectionHeading eyebrow="YOUR OVERVIEW" title={group.name}>
        <span className="quiet-label">{dataAvailable ? `${purchases.length} purchase${purchases.length === 1 ? '' : 's'}` : 'Group data loading'}</span>
      </SectionHeading>
      <div className="metric-grid">
        <article className="metric-card"><span>Purchases</span><strong>{dataAvailable ? purchases.length : '—'}</strong><small>From this group’s API records</small></article>
        <article className="metric-card"><span>Group ledger total</span><strong>{balances ? <MoneyValue amount={balances.totalBalance} /> : '—'}</strong><small>{balances?.currency ?? 'INR'} · backend-calculated</small></article>
        <article className="metric-card"><span>Open balances</span><strong>{balances ? outstanding.length : '—'}</strong><small>Members with a non-zero balance</small></article>
      </div>
      <div className="panel">
        <h3>Recent purchase plans</h3>
        {!dataAvailable
          ? <EmptyState>Purchase data is not available yet.</EmptyState>
          : purchases.length === 0
            ? <EmptyState>No purchases have been created in this group.</EmptyState>
          : <ul className="data-list">
            {purchases.slice(0, 5).map((purchase) => (
              <li key={purchase.id}>
                <div><strong>{purchase.title}</strong><small>{purchase.createdByEmail}</small></div>
                <div className="list-end"><MoneyValue amount={purchase.estimatedAmount} /><StatusBadge status={purchase.status} /></div>
              </li>
            ))}
          </ul>}
      </div>
      <div className="panel">
        <h3>Outstanding group balances</h3>
        {!dataAvailable || !balances || outstanding.length === 0
          ? <EmptyState>{!dataAvailable ? 'Ledger balance data is not available yet.' : balances ? 'Everyone is settled in this ledger.' : 'Ledger balances are not available yet.'}</EmptyState>
          : <ul className="data-list">
            {outstanding.map((entry) => (
              <li key={entry.member.membershipId}>
                <div><strong>{entry.member.name}</strong><small>{entry.member.email}</small></div>
                <div className={(parseInrToPaise(entry.balance) ?? 0n) < 0n ? 'amount-negative' : 'amount-positive'}>
                  <MoneyValue amount={entry.balance} />
                  <small>{(parseInrToPaise(entry.balance) ?? 0n) < 0n ? 'owes' : 'should receive'}</small>
                </div>
              </li>
            ))}
          </ul>}
        <p className="note">Balances are read from the Java ledger; recommendations and simulated purchases do not change them.</p>
      </div>
    </section>
  )
}

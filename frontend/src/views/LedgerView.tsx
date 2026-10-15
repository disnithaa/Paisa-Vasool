import { useMemo, useState, type FormEvent } from 'react'
import type { Expense, GroupBalances, Member, Settlement } from '../api/types'
import { EmptyState, MoneyValue, SectionHeading } from '../components/ui'
import { formatInr, parseInrToPaise, parsePositiveInrToPaise } from '../utils/money'

interface AllocationInput {
  membershipId: string
  amount: string
}

export function LedgerView({
  members,
  expenses,
  balances,
  settlements,
  busy,
  onCreateExpense,
  onCreateSettlement,
}: {
  members: Member[]
  expenses: Expense[]
  balances: GroupBalances | null
  settlements: Settlement[]
  busy: string
  onCreateExpense: (body: {
    description: string
    totalAmount: string
    paidByMembershipId: string
    allocations: { membershipId: string; amount: string }[]
  }) => Promise<boolean>
  onCreateSettlement: (body: {
    fromMembershipId: string
    toMembershipId: string
    amount: string
  }) => Promise<boolean>
}) {
  const activeMembers = members.filter((member) => member.status === 'ACTIVE')
  const [description, setDescription] = useState('')
  const [totalAmount, setTotalAmount] = useState('')
  const [paidBy, setPaidBy] = useState('')
  const [allocations, setAllocations] = useState<AllocationInput[]>([{ membershipId: '', amount: '' }])
  const [expenseError, setExpenseError] = useState('')
  const [fromMember, setFromMember] = useState('')
  const [toMember, setToMember] = useState('')
  const [settlementAmount, setSettlementAmount] = useState('')
  const [settlementError, setSettlementError] = useState('')

  const debtors = useMemo(() => balances?.members.filter(
    (entry) => entry.membershipStatus === 'ACTIVE' && (parseInrToPaise(entry.balance) ?? 0n) < 0n,
  ) ?? [], [balances])
  const creditors = useMemo(() => balances?.members.filter(
    (entry) => entry.membershipStatus === 'ACTIVE' && (parseInrToPaise(entry.balance) ?? 0n) > 0n,
  ) ?? [], [balances])

  async function submitExpense(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const totalPaise = parsePositiveInrToPaise(totalAmount)
    const allocationAmounts = allocations.map((allocation) => parsePositiveInrToPaise(allocation.amount))
    if (!description.trim() || !paidBy || totalPaise === null || allocations.length === 0
      || allocations.some((allocation, index) => !allocation.membershipId || allocationAmounts[index] === null)) {
      return setExpenseError('Enter a description, positive amount, payer, and valid positive allocations.')
    }
    if (new Set(allocations.map((allocation) => allocation.membershipId)).size !== allocations.length) {
      return setExpenseError('Each member may appear only once in the allocations.')
    }
    const allocatedPaise = allocationAmounts.reduce<bigint>((sum, amount) => sum + (amount ?? 0n), 0n)
    if (allocatedPaise !== totalPaise) {
      return setExpenseError('Allocations must add up exactly to the total expense.')
    }
    setExpenseError('')
    const created = await onCreateExpense({
      description: description.trim(),
      totalAmount: totalAmount.trim(),
      paidByMembershipId: paidBy,
      allocations: allocations.map((allocation) => ({
        membershipId: allocation.membershipId,
        amount: allocation.amount.trim(),
      })),
    })
    if (created) {
      setDescription('')
      setTotalAmount('')
      setAllocations([{ membershipId: '', amount: '' }])
    }
  }

  async function submitSettlement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!fromMember || !toMember || fromMember === toMember || parsePositiveInrToPaise(settlementAmount) === null) {
      return setSettlementError('Choose different debtor and creditor members and enter a positive amount.')
    }
    setSettlementError('')
    const created = await onCreateSettlement({
      fromMembershipId: fromMember,
      toMembershipId: toMember,
      amount: settlementAmount.trim(),
    })
    if (created) setSettlementAmount('')
  }

  return (
    <section className="view-stack">
      <SectionHeading eyebrow="VIRTUAL LEDGER" title="Expenses and balances" />
      <div className="panel">
        <h3>Member balances · {balances?.currency ?? 'INR'}</h3>
        <p className="note">{balances?.balanceConvention ?? 'Balances are calculated by the backend from ledger entries.'}</p>
        {!balances || balances.members.length === 0
          ? <EmptyState>No balance data is available for this group.</EmptyState>
          : <ul className="data-list">
            {balances.members.map((entry) => {
              const balance = parseInrToPaise(entry.balance) ?? 0n
              return (
                <li key={entry.member.membershipId}>
                  <div><strong>{entry.member.name}</strong><small>{entry.membershipStatus} · {entry.member.email}</small></div>
                  <div className={balance < 0n ? 'amount-negative' : balance > 0n ? 'amount-positive' : ''}>
                    <strong><MoneyValue amount={entry.balance} /></strong>
                    <small>{balance < 0n ? 'owes' : balance > 0n ? 'should receive' : 'settled'}</small>
                  </div>
                </li>
              )
            })}
          </ul>}
      </div>

      <div className="two-column">
        <div className="panel">
          <h3>Expense history</h3>
          {expenses.length === 0
            ? <EmptyState>No expenses have been recorded.</EmptyState>
            : <ul className="data-list">
              {expenses.map((expense) => (
                <li className="expense-record" key={expense.id}>
                  <div><strong>{expense.description}</strong><small>Paid by {expense.paidBy.name} · {new Date(expense.incurredAt).toLocaleDateString()}</small>
                    <small>Allocations: {expense.allocations.map((allocation) => `${allocation.member.name} ${formatInr(allocation.amount)}`).join(' · ')}</small>
                  </div>
                  <strong><MoneyValue amount={expense.totalAmount} /></strong>
                </li>
              ))}
            </ul>}
        </div>
        <div className="panel">
          <h3>Simulated settlement history</h3>
          {settlements.length === 0
            ? <EmptyState>No settlements have been recorded.</EmptyState>
            : <ul className="data-list">
              {settlements.map((settlement) => (
                <li key={settlement.id}>
                  <div><strong>{settlement.fromMember.name} → {settlement.toMember.name}</strong><small>{new Date(settlement.settledAt).toLocaleDateString()} · simulated ledger entry</small></div>
                  <strong><MoneyValue amount={settlement.amount} /></strong>
                </li>
              ))}
            </ul>}
        </div>
      </div>

      <div className="two-column">
        <div className="panel">
          <h3>Record an expense</h3>
          <p className="note">Allocations must add up exactly. This records a ledger event, not a payment.</p>
          <form className="form-stack" onSubmit={(event) => void submitExpense(event)}>
            <label>Description<input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={200} required /></label>
            <label>Total amount (INR)<input inputMode="decimal" value={totalAmount} onChange={(event) => setTotalAmount(event.target.value)} placeholder="0.00" required /></label>
            <label>Paid by
              <select value={paidBy} onChange={(event) => setPaidBy(event.target.value)} required>
                <option value="">Choose a member</option>
                {activeMembers.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
              </select>
            </label>
            <fieldset className="allocation-fields">
              <legend>Allocation shares</legend>
              {allocations.map((allocation, index) => (
                <div className="form-row allocation-row" key={index}>
                  <label>Member
                    <select value={allocation.membershipId} onChange={(event) => setAllocations((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, membershipId: event.target.value } : row))} required>
                      <option value="">Choose</option>
                      {activeMembers.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
                    </select>
                  </label>
                  <label>Share (INR)<input inputMode="decimal" value={allocation.amount} onChange={(event) => setAllocations((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, amount: event.target.value } : row))} placeholder="0.00" required /></label>
                  {allocations.length > 1 && <button className="text-button remove-allocation" type="button" onClick={() => setAllocations((current) => current.filter((_, rowIndex) => rowIndex !== index))}>Remove</button>}
                </div>
              ))}
              <button className="text-button" type="button" onClick={() => setAllocations((current) => [...current, { membershipId: '', amount: '' }])}>+ Add allocation</button>
            </fieldset>
            {expenseError && <p role="alert" className="error-message">{expenseError}</p>}
            <button className="button button-primary" disabled={busy !== '' || activeMembers.length === 0}>{busy === 'create-expense' ? 'Recording…' : 'Record expense'}</button>
          </form>
        </div>
        <div className="panel">
          <h3>Record a simulated settlement</h3>
          <p className="note">This records a claimed settlement in the virtual ledger only. It does not move money.</p>
          {debtors.length === 0 || creditors.length === 0
            ? <EmptyState>A settlement can be recorded when the backend reports both a debtor and a member who should receive money.</EmptyState>
            : <form className="form-stack" onSubmit={(event) => void submitSettlement(event)}>
              <label>From (owes)
                <select value={fromMember} onChange={(event) => setFromMember(event.target.value)} required>
                  <option value="">Choose debtor</option>
                  {debtors.map((entry) => <option key={entry.member.membershipId} value={entry.member.membershipId}>{entry.member.name} · {formatInr(entry.balance)}</option>)}
                </select>
              </label>
              <label>To (should receive)
                <select value={toMember} onChange={(event) => setToMember(event.target.value)} required>
                  <option value="">Choose recipient</option>
                  {creditors.map((entry) => <option key={entry.member.membershipId} value={entry.member.membershipId}>{entry.member.name} · {formatInr(entry.balance)}</option>)}
                </select>
              </label>
              <label>Amount (INR)<input inputMode="decimal" value={settlementAmount} onChange={(event) => setSettlementAmount(event.target.value)} placeholder="0.00" required /></label>
              {settlementError && <p role="alert" className="error-message">{settlementError}</p>}
              <button className="button button-secondary" disabled={busy !== ''}>{busy === 'create-settlement' ? 'Recording…' : 'Record simulated settlement'}</button>
            </form>}
          {settlementError && (debtors.length === 0 || creditors.length === 0) && <p role="alert" className="error-message">{settlementError}</p>}
        </div>
      </div>
    </section>
  )
}

import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, setApiCredentials } from './api/client'
import type {
  Expense,
  Group,
  GroupBalances,
  Member,
  Offer,
  OfferEligibility,
  Optimization,
  Purchase,
  PurchaseProposal,
  Settlement,
} from './api/types'
import { ErrorMessage, Loading } from './components/ui'
import { DashboardView } from './views/DashboardView'
import { GroupsView } from './views/GroupsView'
import { LedgerView } from './views/LedgerView'
import { OffersView } from './views/OffersView'
import { PurchasesView } from './views/PurchasesView'
import './App.css'

type Tab = 'dashboard' | 'groups' | 'offers' | 'purchases' | 'ledger'

const navigation: { id: Tab; label: string; icon: string }[] = [
  { id: 'dashboard', label: 'Overview', icon: '01' },
  { id: 'groups', label: 'Groups & people', icon: '02' },
  { id: 'offers', label: 'Offers', icon: '03' },
  { id: 'purchases', label: 'Purchase plans', icon: '04' },
  { id: 'ledger', label: 'Expenses & balances', icon: '05' },
]

interface GroupData {
  groupId: string
  version: number
  members: Member[]
  offers: Offer[]
  purchases: Purchase[]
  expenses: Expense[]
  balances: GroupBalances | null
  settlements: Settlement[]
  errors: string[]
}

const EMPTY_GROUP_DATA: GroupData = {
  groupId: '',
  version: -1,
  members: [],
  offers: [],
  purchases: [],
  expenses: [],
  balances: null,
  settlements: [],
  errors: [],
}

function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Authentication failed. Check your configured development email and password.'
    if (error.status === 403) return 'You need an active membership in this group to perform that action.'
    if (error.status === 503) return 'The optimizer is unavailable or returned an invalid recommendation. No purchase state was changed.'
    return error.message
  }
  return 'The request could not be completed. Please try again.'
}

function isUnauthorizedFailure(result: PromiseSettledResult<unknown>): boolean {
  return result.status === 'rejected'
    && result.reason instanceof ApiError
    && result.reason.status === 401
}

function SignIn({ onSignIn }: { onSignIn: (email: string, password: string) => Promise<void> }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await onSignIn(email.trim(), password)
    } catch (caught) {
      setError(errorText(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="signin-page">
      <div className="signin-brand">Paisa<span>Vasool</span></div>
      <section className="signin-card">
        <p className="eyebrow">GROUP SPENDING, MADE CLEAR</p>
        <h1>Welcome back.</h1>
        <p className="intro">Sign in with the development identity configured for your Java backend.</p>
        <form className="form-stack" onSubmit={(event) => void submit(event)}>
          <label htmlFor="signin-email">Email
            <input id="signin-email" type="email" autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          <label htmlFor="signin-password">Password
            <input id="signin-password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label>
          {error && <ErrorMessage>{error}</ErrorMessage>}
          <button className="button button-primary button-wide" disabled={busy}>{busy ? 'Connecting…' : 'Continue securely'}</button>
        </form>
        <p className="security-note">Credentials are held in memory for this page session and sent only to the configured Java API. This project uses development-only HTTP Basic authentication.</p>
      </section>
      <footer>Simulated purchases and a virtual ledger. No real payments are made.</footer>
    </main>
  )
}

function App() {
  const [authenticated, setAuthenticated] = useState(false)
  const [groups, setGroups] = useState<Group[]>([])
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [activeTab, setActiveTab] = useState<Tab>('dashboard')
  const [groupData, setGroupData] = useState<GroupData>(EMPTY_GROUP_DATA)
  const [optimizations, setOptimizations] = useState<Record<string, Optimization>>({})
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState('')
  const [refreshVersion, setRefreshVersion] = useState(0)

  const selectedGroup = groups.find((group) => group.id === selectedGroupId) ?? null
  const currentGroupData = groupData.groupId === selectedGroupId && groupData.version === refreshVersion
    ? groupData
    : EMPTY_GROUP_DATA
  const dataLoading = authenticated && selectedGroupId !== ''
    && (groupData.groupId !== selectedGroupId || groupData.version !== refreshVersion)
  const dataAvailable = currentGroupData.groupId === selectedGroupId
    && currentGroupData.version === refreshVersion
    && currentGroupData.errors.length === 0
  const dataError = currentGroupData.errors.join(' ')

  async function signIn(email: string, password: string) {
    setApiCredentials({ email, password })
    try {
      const loadedGroups = await api.getGroups()
      setGroups(loadedGroups)
      setSelectedGroupId(loadedGroups[0]?.id ?? '')
      setAuthenticated(true)
      setActiveTab('dashboard')
    } catch (error) {
      setApiCredentials(null)
      throw error
    }
  }

  function signOut() {
    setApiCredentials(null)
    setAuthenticated(false)
    setGroups([])
    setSelectedGroupId('')
    setGroupData(EMPTY_GROUP_DATA)
    setOptimizations({})
    setNotice('')
  }

  async function runAction<T>(
    action: string,
    operation: () => Promise<T>,
    refresh = true,
  ): Promise<T | undefined> {
    if (busy) return undefined
    setBusy(action)
    setNotice('')
    try {
      const result = await operation()
      if (refresh) setRefreshVersion((version) => version + 1)
      return result
    } catch (error) {
      setNotice(errorText(error))
      return undefined
    } finally {
      setBusy('')
    }
  }

  useEffect(() => {
    if (!authenticated || !selectedGroupId) return

    let current = true
    const loaders = [
      api.getMembers(selectedGroupId),
      api.getOffers(selectedGroupId),
      api.getPurchases(selectedGroupId),
      api.getExpenses(selectedGroupId),
      api.getBalances(selectedGroupId),
      api.getSettlements(selectedGroupId),
    ] as const
    void Promise.allSettled(loaders).then(([membersResult, offersResult, purchasesResult, expensesResult, balancesResult, settlementsResult]) => {
      if (!current) return
      const results = [membersResult, offersResult, purchasesResult, expensesResult, balancesResult, settlementsResult]
      const errors = results.flatMap((result) =>
        result.status === 'rejected' ? [errorText(result.reason)] : [],
      )
      if (results.some(isUnauthorizedFailure)) {
        setApiCredentials(null)
        setAuthenticated(false)
        setGroups([])
        setSelectedGroupId('')
        setGroupData(EMPTY_GROUP_DATA)
        setNotice('Your authentication is no longer valid. Sign in again.')
        return
      }
      setGroupData({
        groupId: selectedGroupId,
        version: refreshVersion,
        members: membersResult.status === 'fulfilled' ? membersResult.value : [],
        offers: offersResult.status === 'fulfilled' ? offersResult.value : [],
        purchases: purchasesResult.status === 'fulfilled' ? purchasesResult.value : [],
        expenses: expensesResult.status === 'fulfilled' ? expensesResult.value : [],
        balances: balancesResult.status === 'fulfilled' ? balancesResult.value : null,
        settlements: settlementsResult.status === 'fulfilled' ? settlementsResult.value : [],
        errors,
      })
    })
    return () => { current = false }
  }, [authenticated, selectedGroupId, refreshVersion])

  async function createGroup(name: string): Promise<boolean> {
    const created = await runAction('create-group', () => api.createGroup(name), false)
    if (!created) return false
    setGroups((current) => [created, ...current.filter((group) => group.id !== created.id)])
    setSelectedGroupId(created.id)
    setNotice(`Group “${created.name}” created.`)
    setRefreshVersion((version) => version + 1)
    return true
  }

  async function addMember(name: string, email: string): Promise<boolean> {
    const result = await runAction('add-member', () => api.addMember(selectedGroupId, name, email))
    if (!result) return false
    setNotice(`${result.name} was added to the group.`)
    return true
  }

  async function createPurchase(title: string, amount: string): Promise<boolean> {
    const result = await runAction('create-purchase', () => api.createPurchase(selectedGroupId, title, amount))
    if (!result) return false
    setNotice('Draft purchase created. No expense or payment was recorded.')
    return true
  }

  async function optimizePurchase(purchaseId: string) {
    const result = await runAction(
      `optimize-${purchaseId}`,
      () => api.optimizePurchase(selectedGroupId, purchaseId),
      false,
    )
    if (result) setOptimizations((current) => ({ ...current, [purchaseId]: result }))
    return result
  }

  async function getEligibleOffers(purchaseId: string): Promise<OfferEligibility[] | undefined> {
    return runAction(
      `eligible-${purchaseId}`,
      () => api.getEligibleOffers(selectedGroupId, purchaseId),
      false,
    )
  }

  async function getProposal(purchaseId: string): Promise<PurchaseProposal | undefined> {
    return runAction(
      `proposal-details-${purchaseId}`,
      () => api.getProposal(selectedGroupId, purchaseId),
      false,
    )
  }

  async function submitProposal(purchaseId: string, offerId: string) {
    const result = await runAction(`proposal-${purchaseId}`, () => api.proposeOffer(selectedGroupId, purchaseId, offerId))
    if (result) {
      setOptimizations((current) => {
        const next = { ...current }
        delete next[purchaseId]
        return next
      })
      setNotice('Offer submitted for group approval. The purchase remains a proposal, not a payment.')
    }
  }

  async function approvePurchase(purchaseId: string) {
    const result = await runAction(`approve-${purchaseId}`, () => api.approvePurchase(selectedGroupId, purchaseId))
    if (result) setNotice('Purchase proposal approved. No payment was executed.')
  }

  async function cancelPurchase(purchaseId: string) {
    const result = await runAction(`cancel-${purchaseId}`, () => api.cancelPurchase(selectedGroupId, purchaseId))
    if (result) setNotice('Purchase plan cancelled.')
  }

  async function completePurchase(purchaseId: string) {
    const result = await runAction(`complete-${purchaseId}`, () => api.completePurchaseSimulated(selectedGroupId, purchaseId))
    if (result) setNotice('Purchase marked complete as a simulation only.')
  }

  async function createExpense(body: {
    description: string
    totalAmount: string
    paidByMembershipId: string
    allocations: { membershipId: string; amount: string }[]
  }): Promise<boolean> {
    const result = await runAction('create-expense', () => api.createExpense(selectedGroupId, body))
    if (!result) return false
    setNotice('Expense added to the virtual ledger.')
    return true
  }

  async function createSettlement(body: {
    fromMembershipId: string
    toMembershipId: string
    amount: string
  }): Promise<boolean> {
    const result = await runAction('create-settlement', () => api.createSettlement(selectedGroupId, body))
    if (!result) return false
    setNotice('Simulated settlement recorded in the virtual ledger.')
    return true
  }

  if (!authenticated) return <SignIn onSignIn={signIn} />

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" onClick={(event) => { event.preventDefault(); setActiveTab('dashboard') }}>Paisa<span>Vasool</span></a>
        <p className="sidebar-caption">GROUP FINANCE · INR</p>
        <nav aria-label="Main navigation">
          {navigation.map((item) => (
            <button className={`nav-item ${activeTab === item.id ? 'active' : ''}`} key={item.id} onClick={() => setActiveTab(item.id)} aria-current={activeTab === item.id ? 'page' : undefined}>
              <span aria-hidden="true">{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <label htmlFor="active-group">Current group</label>
          <select id="active-group" value={selectedGroupId} onChange={(event) => setSelectedGroupId(event.target.value)}>
            <option value="">No group selected</option>
            {groups.map((group) => <option value={group.id} key={group.id}>{group.name}</option>)}
          </select>
          <button className="signout-button" onClick={signOut}>Sign out</button>
        </div>
      </aside>

      <main className="main-content">
        <header className="page-header">
          <div><span className="header-mark">PV</span><span className="header-context">Shared spending, made clear</span></div>
          <div className="header-right"><span className="currency-chip">INR</span><span className="connection-chip"><i /> Java API</span></div>
        </header>
        <div className="page-body">
          {notice && <div className="notice-banner" role="status"><span>{notice}</span><button aria-label="Dismiss message" onClick={() => setNotice('')}>×</button></div>}
          {dataError && <ErrorMessage>{dataError}</ErrorMessage>}
          {dataLoading && <Loading label="Loading group data…" />}
          {activeTab === 'dashboard' && <DashboardView group={selectedGroup} purchases={currentGroupData.purchases} balances={currentGroupData.balances} dataAvailable={dataAvailable} />}
          {activeTab === 'groups' && (
            <GroupsView
              groups={groups}
              selectedGroupId={selectedGroupId}
              members={currentGroupData.members}
              busy={busy}
              onSelectGroup={setSelectedGroupId}
              onCreateGroup={createGroup}
              onAddMember={addMember}
            />
          )}
          {activeTab === 'offers' && <OffersView offers={currentGroupData.offers} />}
          {activeTab === 'purchases' && selectedGroupId && (
            <PurchasesView
              groupId={selectedGroupId}
              purchases={currentGroupData.purchases}
              busy={busy}
              optimizations={optimizations}
              onCreate={createPurchase}
              onOptimize={optimizePurchase}
              onEligibleOffers={getEligibleOffers}
              onProposal={submitProposal}
              onApprove={approvePurchase}
              onCancel={cancelPurchase}
              onComplete={completePurchase}
              onGetProposal={getProposal}
            />
          )}
          {activeTab === 'purchases' && !selectedGroupId && <p className="empty-state">Create or select a group before planning a purchase.</p>}
          {activeTab === 'ledger' && selectedGroupId && (
            <LedgerView
              members={currentGroupData.members}
              expenses={currentGroupData.expenses}
              balances={currentGroupData.balances}
              settlements={currentGroupData.settlements}
              busy={busy}
              onCreateExpense={createExpense}
              onCreateSettlement={createSettlement}
            />
          )}
          {activeTab === 'ledger' && !selectedGroupId && <p className="empty-state">Create or select a group before opening its ledger.</p>}
        </div>
        <footer className="app-footer">Virtual ledger only · No real-money payments are made</footer>
      </main>
    </div>
  )
}

export default App

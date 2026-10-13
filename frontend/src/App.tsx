import { useEffect, useState } from 'react'
import './App.css'

type HealthResponse = {
  status: 'UP' | 'DOWN'
  service: string
  timestamp: string
  database: { status: 'UP' | 'DOWN'; database: string; healthy: boolean }
}

function App() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/health', { signal: controller.signal })
      .then(async (response) => {
        const body = (await response.json()) as HealthResponse
        setHealth(body)
        if (!response.ok) setError('The backend is running but its database check failed.')
      })
      .catch(() => setError('Cannot reach the backend. Start the API and refresh this page.'))
    return () => controller.abort()
  }, [])

  return (
    <main className="dashboard">
      <header className="topbar"><a className="brand" href="/">Paisa<span>Vasool</span></a><span className="stage">FOUNDATION · WEEK 1</span></header>
      <section className="welcome">
        <p className="eyebrow">GROUP SPENDING, MADE CLEAR</p>
        <h1>Every rupee,<br />accounted for.</h1>
        <p className="intro">A thoughtful way to plan shared purchases, find better offers, and settle up with confidence.</p>
        <div className="status-card" aria-live="polite">
          <span className={`status-dot ${health?.status === 'UP' && !error ? 'online' : ''}`} />
          <div><strong>{error ? 'Connection needs attention' : health ? 'Foundation services' : 'Checking services…'}</strong>
            <p>{error ?? (health ? `API ${health.status.toLowerCase()} · PostgreSQL ${health.database.status.toLowerCase()}` : 'Connecting to the Paisa Vasool API')}</p>
          </div>
          {health && <span className="service-name">{health.service}</span>}
        </div>
      </section>
      <section className="features" aria-label="Product goals">
        <article><span className="number">01</span><h2>Plan together</h2><p>Create a group, set a budget, and agree on what to buy.</p></article>
        <article><span className="number">02</span><h2>Find better value</h2><p>Compare curated offers and see the real cost before deciding.</p></article>
        <article><span className="number">03</span><h2>Settle clearly</h2><p>Keep a shared record of expenses, contributions, and balances.</p></article>
      </section>
      <footer>Simulated purchases and a virtual ledger. No real payments are made.</footer>
    </main>
  )
}

export default App

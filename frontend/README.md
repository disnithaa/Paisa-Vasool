# Paisa Vasool frontend

Responsive React and TypeScript application for the existing Java Spring Boot API. The browser never calls the Python optimizer; purchase optimization is requested through Java's authenticated purchase-scoped endpoint.

## Requirements

- Node.js 20.19+ or 22.12+
- npm 10+
- The Paisa Vasool Java backend; PostgreSQL for persisted local data
- The independent Python optimizer when requesting a purchase recommendation

## Local setup

From this directory:

```powershell
npm install
npm run dev
```

Vite normally serves the app at `http://localhost:5173`. It proxies `/api` requests to `http://localhost:8080`, so the default API base is the same-origin path `/api`. To use another Java API URL, set `VITE_API_BASE_URL` before starting Vite, including the `/api` path, for example:

```powershell
$env:VITE_API_BASE_URL = "https://api.example.test/api"
npm run dev
```

For a cross-origin API, configure the Java backend's allowed CORS origin as well. Do not put credentials or secrets in a `VITE_*` variable; Vite embeds these values into client-side assets.

The sign-in form uses the backend's existing stateless HTTP Basic authentication. Enter the configured `DEV_AUTH_EMAIL` and `DEV_AUTH_PASSWORD`. The browser keeps the credentials in memory only until page reload or sign-out. There is no production login, registration, or token endpoint.

Run PostgreSQL and Spring Boot using the repository [setup instructions](../README.md). Run the optimizer independently for recommendation requests:

```powershell
Set-Location ..\optimizer
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

The Java backend's optimizer URL defaults to `http://localhost:8000` and can be configured with `OPTIMIZER_BASE_URL`. Optimization returns an estimate only; it does not approve a purchase, record an expense, or execute a payment.

## Implemented screens

- **Overview:** actual purchases and ledger balance summary for the selected group.
- **Groups and members:** list the current user's active group memberships, create groups, view members, and add member records.
- **Offers:** browse catalog entries returned by Java. The list API only returns offers that are active and currently within their validity window.
- **Purchase plans:** create draft estimates, inspect offer eligibility, request an optimization recommendation, submit an eligible offer for approval, approve or cancel a proposal, and record simulated completion.
- **Expenses and balances:** show expense/allocation and settlement history, display backend-calculated member balances, record expenses with explicit allocations, and create simulated settlement ledger entries.

The offer recommendation is separate from proposal/approval status. Amounts submitted by forms remain decimal strings, and displayed INR is formatted without browser-side financial arithmetic. The balance page consumes the Java ledger result rather than deriving balances independently.

## Limitations

- Authentication is the backend's local development HTTP Basic identity, not production authentication. A member record added by the group API does not create a login account.
- There is no backend endpoint for a distinct "reject" action. A pending proposal can be cancelled; the UI labels that operation as cancellation.
- Recommendations are ephemeral and purchase-scoped. Only the supported Java workflow endpoints change the purchase state.
- Expenses and settlements are virtual ledger records. No actual payment is performed.
- The API does not provide catalog administration, invitations, gift-card handling, offer stacking, or payment execution.

## Checks

```powershell
npm test
npm run lint
npm run build
```

Vitest and Testing Library mock HTTP responses; frontend tests do not require a live Java or Python service.

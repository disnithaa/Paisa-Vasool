# Paisa Vasool

Paisa Vasool is a group spending and savings application. Its initial product journey will help groups plan purchases, compare curated offers, record simulated expenses, and understand settlements. It does not custody money or execute payments.

## Implemented scope

The foundation includes a React + TypeScript + Vite frontend, a Java 21 / Spring Boot REST API, PostgreSQL in Docker Compose, Flyway migrations, and a database-backed health check. The backend supports users, groups, memberships, purchase estimates, and an internal expense ledger with allocations, derived balances, and simulated settlements. Purchases remain estimates; only a separately recorded expense changes the ledger. No real payments are executed.

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Node.js 20+ and npm 10+
- Docker Desktop (Windows/macOS) or Docker Engine with the Compose plugin (Linux)
- Git

Check the installed tools:

```cmd
java -version
mvn -version
node --version
npm --version
docker --version
docker compose version
```

## Repository layout

```text
paisa-vasool/
├── backend/       Spring Boot API and Flyway migrations
├── frontend/      React + TypeScript + Vite application
├── infra/         Local PostgreSQL Compose configuration
├── docs/          Architecture and project documentation
├── optimizer/     Reserved for the later Python optimization service
└── README.md
```

## Start PostgreSQL

From the repository root:

```cmd
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml ps
```

The local database is `paisa_vasool`, with username `paisa` and password `paisa123`. These are development-only defaults; set `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` before starting Compose if you need different local credentials. Do not use these defaults for a deployed environment.

Stop the database and keep its data:

```cmd
docker compose -f infra/docker-compose.yml down
```

To also remove the local database volume and all its data:

```cmd
docker compose -f infra/docker-compose.yml down -v
```

## Run the backend

In a **local development terminal** from the repository root, configure the development-only Basic authentication account and start the backend.

Command Prompt:

```cmd
set DEV_AUTH_ENABLED=true
set DEV_AUTH_EMAIL=dev@paisavasool.local
set DEV_AUTH_NAME=Local Developer
set DEV_AUTH_PASSWORD=choose-a-local-password
cd backend
mvn spring-boot:run
```

PowerShell:

```powershell
$env:DEV_AUTH_ENABLED = "true"
$env:DEV_AUTH_EMAIL = "dev@paisavasool.local"
$env:DEV_AUTH_NAME = "Local Developer"
$env:DEV_AUTH_PASSWORD = "choose-a-local-password"
Set-Location backend
mvn spring-boot:run
```

The first startup applies Flyway migrations and checks PostgreSQL connectivity. The API listens on `http://localhost:8080`. When local authentication is enabled, startup provisions that configured email as a user if it is not already in the database.

To use another database, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend process environment. Defaults match the Compose configuration.

### Development identity warning

The local HTTP Basic account is a development-only identity mechanism, not production authentication. Its password is configured by the process environment and must not be reused or enabled on a public deployment. Basic authentication over plain HTTP is suitable only for local development. The authenticated principal's configured email determines the current user; the API never accepts a caller user ID as proof of identity. An email in the add-member request identifies the person to add, not the caller.

## Group and purchase APIs

All group and purchase APIs require HTTP Basic authentication while local development auth is enabled. Replace the values below with the configured local credentials. `curl.exe` is available on Windows.

Create a group (the authenticated caller is added as its `OWNER` in the same transaction):

```cmd
curl -u dev@paisavasool.local:choose-a-local-password ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Weekend trip\"}" ^
  http://localhost:8080/api/groups
```

Run backend tests (including API and Flyway/JPA persistence tests):

```cmd
cd backend
mvn clean test
```

Group/member endpoints:

- `POST /api/groups` with `{"name":"Weekend trip"}` — create a group and owner membership (`201`).
- `GET /api/groups/{groupId}` — retrieve a group (active members only).
- `POST /api/groups/{groupId}/members` with `{"name":"Mira","email":"mira@example.com"}` — add or register that member (`201`). The caller must already be an active group member; an existing user account is reused by email.
- `GET /api/groups/{groupId}/members` — list memberships, including their role and status (active members only).

Purchase endpoints:

- `POST /api/groups/{groupId}/purchases` with `{"title":"Train tickets","estimatedAmount":1250.50}` — create a `DRAFT` estimate (`201`). The backend derives the creator from the authenticated principal, checks that user's active membership, and validates the positive amount.
- `GET /api/groups/{groupId}/purchases` — list a group's purchases (active members only).

Requests with invalid fields return `400`; missing groups return `404`; callers without active group membership receive `403`; duplicate memberships return `409`; unauthenticated requests return `401`. Errors use Spring's RFC 9457 problem-details response format.

Health endpoints:

- `GET http://localhost:8080/api/health` checks the API and runs `SELECT 1` against PostgreSQL. It returns HTTP 200 when both are available and HTTP 503 when the database check fails.
- `GET http://localhost:8080/actuator/health` is Spring Boot's health endpoint.

## Expense ledger API

All ledger endpoints require an active group member, authenticated using the development-only HTTP Basic identity described above. Amounts are INR with exactly two decimal places of precision. Amounts must be positive and are rejected rather than rounded if they have more than two fractional digits. `paidByMembershipId`, allocation membership IDs, and settlement participant IDs identify group memberships; the backend validates that each belongs to the specified group and is active. The creator is always derived from the authenticated principal.

Create an expense with explicit allocations:

```cmd
curl -u dev@paisavasool.local:choose-a-local-password ^
  -H "Content-Type: application/json" ^
  -d "{\"description\":\"Shared dinner\",\"totalAmount\":25.50,\"paidByMembershipId\":\"<payer-membership-uuid>\",\"allocations\":[{\"membershipId\":\"<payer-membership-uuid>\",\"amount\":10.00},{\"membershipId\":\"<other-membership-uuid>\",\"amount\":15.50}]}" ^
  http://localhost:8080/api/groups/<group-uuid>/expenses
```

The allocations must be non-empty, positive, and sum exactly to `totalAmount`; the API does not silently equal-split or round. An optional `purchaseId` links an actual expense to a planned purchase in the same group, but does not make the purchase estimate itself a ledger entry. An optional `incurredAt` records when the expense occurred; otherwise the current time is used.

Record a simulated settlement from a debtor membership to a member who is owed:

```cmd
curl -u dev@paisavasool.local:choose-a-local-password ^
  -H "Content-Type: application/json" ^
  -d "{\"fromMembershipId\":\"<debtor-membership-uuid>\",\"toMembershipId\":\"<creditor-membership-uuid>\",\"amount\":7.25}" ^
  http://localhost:8080/api/groups/<group-uuid>/settlements
```

A settlement is an append-only ledger event, not a payment instruction or proof of a real-world transfer. It is accepted only if the sender currently owes, the recipient is currently owed, and the amount does not exceed either net balance. An optional `settledAt` records the claimed event time; otherwise the current time is used.

Ledger endpoints:

- `POST /api/groups/{groupId}/expenses` — record an expense and its allocations (`201`).
- `GET /api/groups/{groupId}/expenses` — list expense history with allocations.
- `GET /api/groups/{groupId}/expenses/{expenseId}` — retrieve one expense and its allocations.
- `GET /api/groups/{groupId}/balances` — calculate current balances from expense, allocation, and settlement records.
- `POST /api/groups/{groupId}/settlements` — record a simulated settlement (`201`).
- `GET /api/groups/{groupId}/settlements` — list settlement history.

Balance convention: positive means the member should receive money; negative means the member owes money; zero means settled. For every expense, the payer is credited the full expense amount and every allocation is debited by its allocated amount. For a settlement from A to B, A is credited by the settlement amount and B is debited by it. The service checks that member balances total exactly zero. Balances are derived from the authoritative ledger records; no independently editable balance is stored.

Expense and settlement writes are transactional. They serialize on the group row so concurrent ledger writes cannot validate against the same stale balance and over-settle the same debt. The ledger tables are introduced by Flyway V3; Hibernate remains configured to validate the schema rather than create or update it.

## Run the frontend

In a second terminal from the repository root:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). The frontend requests `/api/health`; Vite proxies that request to the backend at `http://localhost:8080`, avoiding local CORS setup for development.

Create a production frontend build with:

```bash
npm run build
```

## Verify the application journey

1. Start PostgreSQL using the Compose command above.
2. Configure the local development identity and start the backend.
3. Open `http://localhost:8080/api/health`. Confirm the response reports `UP` for both the service and database.
4. Use the group API to create a group, add a member, and record a purchase estimate. Use the ledger API to record an expense, retrieve balances, and record a simulated settlement.
5. Start the frontend and open its Vite URL. The frontend currently displays the Week 1 service health screen.
6. If the database is stopped, the API health endpoint returns HTTP 503; the frontend shows that the database check failed.

## Backend dependencies

- Spring Web serves the REST API.
- Spring Data JPA and the PostgreSQL JDBC driver establish the persistence foundation.
- Spring Security protects APIs with an explicitly enabled local-only HTTP Basic identity; it is not a production authentication solution.
- Bean Validation validates API request DTOs.
- Flyway Core and its PostgreSQL support apply versioned database migrations.
- Spring Boot Actuator exposes application health.
- H2 is used only for lightweight backend tests; PostgreSQL remains the development database.

## Current scope

Implemented: group/member management, purchase estimate creation/listing, expense and allocation recording, derived zero-sum balances, and simulated settlement recording/history. Group owners are represented by memberships. Only active group members can access group APIs and the ledger. Purchase status transitions are guarded in the domain model, but no purchase approval or transition endpoint exists yet.

Not implemented: production identity/authentication, invitations, offer discovery, optimization, or real payment execution. Do not treat the development identity or purchase estimates as financial records. The internal ledger records user-reported expenses and settlements; it does not verify external payments.

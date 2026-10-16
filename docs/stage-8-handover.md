# Stage 8 demo and placement guide

## Demo checklist

### Before the interview

- [ ] Confirm Java 21, Node.js 24, Python 3.11, and Docker Compose are available.
- [ ] Copy `.env.example` to `.env` and set two local-only passwords; never reuse a real credential.
- [ ] Start the stack with `docker compose --env-file .env -f infra\docker-compose.yml up -d --build`.
- [ ] Wait until `docker compose --env-file .env -f infra\docker-compose.yml ps` reports all four services healthy.
- [ ] Open `http://localhost:5173` and sign in with the configured development identity.

### Walk through the user journey

1. Create a group and show that its creator becomes the owner membership.
2. Add a group member and point out that the backend scopes group access to active membership.
3. Browse the seeded fictional offer catalog and explain that there is no public offer-write API.
4. Create a draft purchase. Its amount and creator are validated/derived by Java.
5. Request an optimization recommendation. Show the base amount, selected offer (or no-offer baseline), discount, estimated payable, savings, explanation, and solver metadata.
6. Explain the service boundary: Java loads the purchase and eligible catalog entries, Python evaluates those candidates statelessly, and Java validates the returned recommendation. The recommendation does not change purchase status.
7. Submit an offer proposal, approve it, and record simulated completion. Point out that simulated completion does not itself create an expense.
8. Separately record an expense with explicit allocations, show the server-derived group balances, and create a simulated settlement if time permits.
9. Finish by distinguishing user-reported ledger records from verified purchases, bank transfers, or actual payment execution.

### If a service fails

- Check `docker compose --env-file .env -f infra\docker-compose.yml ps`.
- Inspect one service with `docker compose --env-file .env -f infra\docker-compose.yml logs backend` (replace `backend` with `postgres`, `optimizer`, or `frontend` as needed).
- Check `http://localhost:8080/api/health` for Java/PostgreSQL health and `http://localhost:8080/actuator/health` for the Spring health endpoint.
- Stop with `docker compose --env-file .env -f infra\docker-compose.yml down`; this preserves the database volume.

## Placement interview questions and answers

### Architecture and service boundaries

**Why is the Java backend authoritative while Python performs optimization?**

Java owns identity mapping, group authorization, purchase state, catalog eligibility, and financial records. Python receives a validated snapshot of candidates and returns a stateless recommendation. Java verifies the response against its own pricing rules; Python cannot approve a purchase or mutate application data.

**Why use a modular monolith for the core application?**

Groups, purchases, and ledger writes share transactional relational invariants. Keeping them in one Spring application makes authorization and database transactions straightforward, while the optimizer is independently deployable because its computation is isolated and stateless.

**What design principles are visible in the domain?**

Entities encapsulate state transitions and invariants; application services coordinate transactions and repository access; DTOs separate HTTP contracts from persistence models. Composition represents memberships, purchases, expenses, allocations, and settlements. The optimizer client is an integration boundary; the project avoids artificial inheritance hierarchies.

### REST, security, and workflow

**How is a caller authorized for a group operation?**

Spring Security authenticates the development HTTP Basic identity. The backend maps the authenticated email to its application user, then domain services check for an active membership in the requested group. A request body user ID is not accepted as proof of caller identity.

**What does an optimization request change?**

Nothing persistent. Java accepts it only for an authorized draft purchase, loads the authoritative purchase and offer data, calls Python, and validates the result. Approval, proposal submission, simulated completion, and expense recording are separate operations.

**How are HTTP and optimizer errors handled?**

Request DTO validation produces client errors; missing resources and forbidden group access remain distinct; invalid state transitions conflict. The optimizer integration maps connectivity, timeout, and invalid-response failures to an explicit service-unavailable response rather than fabricating a recommendation.

**What does “simulated purchase” mean here?**

It records a workflow status only. It does not prove a purchase happened, transfer funds, or create an expense. A separately recorded user-reported expense is what affects the virtual ledger.

### Spring, persistence, and financial correctness

**How do Flyway and Hibernate work together?**

Flyway applies ordered SQL migrations to the `app` schema during application startup. Hibernate uses `ddl-auto: validate`, checking entity mappings against the migrated schema without altering it.

**Why use PostgreSQL constraints as well as Java validation?**

Java validation gives useful API feedback and protects business rules; database constraints protect persisted state from invalid writes through any application path. Foreign keys, uniqueness, check constraints, and numeric precision preserve relational and financial invariants.

**How are group balances calculated?**

The ledger credits each expense payer for the total, debits members by their explicit allocations, and applies settlement entries with the documented sign convention. The backend verifies the sum of group balances is zero; the browser displays backend-provided balances rather than acting as the accounting authority.

**How is money represented and rounded?**

Java uses `BigDecimal`, PostgreSQL uses `NUMERIC(12,2)`, and API values are validated to paise precision. Percentage discounts use the defined half-up rounding rule; explicit ledger amounts with excess fractional precision are rejected rather than rounded.

**Are ledger writes idempotent?**

Not currently. Expense and settlement endpoints do not accept idempotency keys, so clients must not blindly retry a timed-out write. This limitation is documented; Stage 8 does not add a new financial API or schema contract.

### Frontend, optimizer, and testing

**How does the frontend reach the services?**

The React/TypeScript client calls the Java REST API. Vite proxies `/api` during development; in the Compose stack, Nginx serves the production build and proxies `/api` to Java. The browser never calls Python directly.

**Why use OR-Tools for a small offer list?**

The CP-SAT model demonstrates a constrained optimization formulation and deterministic tie-breaking. The implementation also compares the solver result against an exhaustive-search baseline, which is simple and appropriate for the current small candidate set.

**How are the three layers tested?**

Frontend Vitest tests mock API responses; Java uses JUnit 5, Spring tests, and Mockito for service/HTTP behavior, with a PostgreSQL Testcontainers test for real migrations and schema behavior; Python pytest covers validation, pricing, and deterministic solver outcomes.

**What does the PostgreSQL integration test establish?**

It starts PostgreSQL 16 in a disposable container, starts the Spring application against that database, applies all Flyway migrations, validates JPA mappings, and checks seeded catalog data and the real database precision/scale for a money column.

### CI, containers, and limitations

**What does CI run?**

GitHub Actions installs locked frontend dependencies and runs tests, lint, and build; uses Java 21 to run Maven verification (including PostgreSQL-backed Testcontainers coverage); and installs the constrained Python requirements before running pytest.

**How is startup ordering managed in Compose?**

PostgreSQL and the optimizer have health checks. Java waits for both to be healthy; the frontend waits for Java's database-backed health endpoint. Flyway runs in the Java application at startup.

**Why is there no public deployment?**

The current identity is a single development-only HTTP Basic account and is not production authentication. Public deployment is deliberately deferred until production identity, authorization, transport security, and deployment configuration are reviewed and approved.

**What would you improve next?**

Design production authentication and role-based approval rules; add idempotency for ledger writes; add browser-driven end-to-end coverage; add operational metrics and deployment-specific secret management; then evaluate a small managed deployment after a threat and cost review.

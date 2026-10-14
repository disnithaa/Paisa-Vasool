# Week 5 optimizer architecture

## Service boundary

```mermaid
flowchart LR
    Caller[HTTP caller] --> API[FastAPI\nPOST /api/v1/optimize]
    API --> Validation[Pydantic request validation]
    Validation --> Pricing[Eligibility and Decimal pricing\ninteger paise conversion]
    Pricing --> CP[OR-Tools CP-SAT\nsingle-offer selection]
    Pricing --> Baseline[Exhaustive-search baseline]
    CP --> Compare[Compare cost and selected offer]
    Baseline --> Compare
    Compare --> Response[Validated JSON response]
```

The service receives the complete purchase amount, evaluation time, and candidate offer terms in each request. It has no persistence layer or Java-owned state. Pydantic validates the request; a pure pricing component derives candidate eligibility and exact discount amounts; CP-SAT selects among the no-offer baseline and eligible discounts. The independent exhaustive search must agree with the solver before the response is returned.

## Ownership and integration

- Java remains authoritative for users, group authorization, catalog data, purchase state, and the expense ledger.
- The optimizer does not connect to PostgreSQL, authorize a user, approve or update a purchase, record an expense, or execute a payment.
- The request's `active`, validity, and discount fields are supplied inputs. The service checks that those fields are internally consistent and computes eligibility, but cannot verify who is allowed to use an offer or whether supplied catalog data is current.
- The standalone HTTP service is implemented in Week 5. In Week 6, the Java backend added a purchase-scoped REST client and authenticated API endpoint. Java filters the offer catalog using its own pricing and eligibility rules before calling Python and independently validates the returned recommendation.
- Recommendations are ephemeral and do not mutate or persist purchase, offer, approval, payment, or ledger state. Java makes the call outside a database transaction and maps optimizer failures or malformed responses to HTTP `503`.

The Java HTTP boundary is `POST /api/groups/{groupId}/purchases/{purchaseId}/optimization`; it accepts no client-supplied financial data. The internal Python contract remains `POST /api/v1/optimize` and uses decimal strings for all money. Java configures its optimizer URL and bounded timeouts using `OPTIMIZER_BASE_URL`, `OPTIMIZER_CONNECT_TIMEOUT`, and `OPTIMIZER_READ_TIMEOUT`.

## Runtime components

`app.main` exposes the versioned optimization endpoint and a health route. `app.models` defines strict Pydantic input/output contracts. `app.pricing` validates the INR/paise rules and implements deterministic eligibility, discount calculation, and the exhaustive baseline. `app.solver` builds the binary CP-SAT choice model, applies deterministic tie-breaking, and checks the result against that baseline.

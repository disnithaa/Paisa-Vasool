# Paisa Vasool Optimization Service

This is a stateless Python service for choosing the lowest estimated cost for one INR purchase using no offer or one discount offer. It uses FastAPI and Pydantic for the HTTP contract, `Decimal` for pricing, integer paise in OR-Tools CP-SAT, and an exhaustive-search baseline to verify solver results.

The service does not connect to PostgreSQL, authenticate users, change Java-owned purchase records, record expenses, or execute payments. The Java backend calls this service over REST for purchase-scoped recommendations. Java remains authoritative for user authorization, purchase state, catalog data, and eligibility; this service evaluates only the offer facts supplied in each request.

See [the Week 5 optimizer architecture note](../docs/optimizer-architecture.md) for the component flow and service ownership boundary.

## Optimization model

Let:

- `P` be the purchase amount in integer paise.
- `d_i` be the calculated discount for eligible offer `i`, also in paise.
- `x_0` be 1 when no offer is selected, otherwise 0.
- `x_i` be 1 when offer `i` is selected, otherwise 0.

The CP-SAT model chooses exactly one of the baseline and eligible beneficial single-offer choices:

```text
minimize  P*x_0 + sum((P - d_i)*x_i)
subject to x_0 + sum(x_i) = 1
           x_0, x_i in {0, 1}
```

Activity, validity, and minimum-spend rules are evaluated before a candidate gets a decision variable. Inactive, not-yet-valid, expired, and below-minimum offers are excluded and returned with a reason. Candidates with a valid zero discount do not displace the baseline. There are no offer-combination variables: at most one discount offer can be selected.

After minimizing payable amount, the solver fixes that optimum and minimizes a second objective that ranks offer IDs lexicographically (with no-offer ranked first). This two-pass solve makes ties deterministic without a large weighted objective coefficient. A single-worker CP-SAT solver is used. Every result is compared to the exhaustive baseline; disagreement or a non-optimal CP-SAT status raises an error rather than returning a fallback recommendation.

The exhaustive baseline evaluates the baseline and each eligible offer in O(n) time for `n` offers. It uses the same eligibility and price calculations as the solver. OR-Tools is included to establish a constrained optimization model that can be extended later, not because it is expected to outperform exhaustive search for this small problem.

## Pricing and financial rules

- Currency is INR. Purchase, discount, minimum spend, maximum discount, payable, and savings are represented as decimal strings at the API boundary and as Python `Decimal` internally.
- Monetary inputs must fit `NUMERIC(12,2)` and have at most two fractional digits. Percentage inputs must fit `NUMERIC(5,2)`, be greater than zero, and be at most 100. Invalid precision is rejected; inputs are never silently rounded.
- A fixed discount has `discount_amount` and no percentage or maximum-discount field. A percentage discount has `discount_percentage` and may have `maximum_discount`. These rules match the Java Week 4 Offer model.
- Offer validity starts at `valid_from` inclusively and ends at `valid_until` exclusively. All timestamps, including `evaluated_at`, must include a timezone. The caller supplies `evaluated_at` so a request is repeatable; in a future integration Java should supply its trusted server-side evaluation time.
- Percentage discount is rounded to the nearest paise with `ROUND_HALF_UP`, matching Java. The maximum-discount cap is applied next, followed by a cap at the purchase amount. Fixed discounts are also capped at the purchase amount.
- Prices are converted to integer paise only after input precision validation. The optimizer does not use binary floating point. Final payable cannot be negative, and savings are defined as `base_amount - final_payable_amount`. The response guarantees `discount_amount == savings_amount` and `final_payable_amount + savings_amount == base_amount`.

The `active`, validity, and offer-term fields in a request are inputs, not proof of authorization or authoritative catalog state. The service independently checks their internal consistency and derives eligibility from them, but it cannot establish whether a caller is allowed to use an offer.

## Java integration

The authenticated Java endpoint is `POST /api/groups/{groupId}/purchases/{purchaseId}/optimization` with an empty request body. Java loads the stored draft purchase and authoritative active catalog entries, filters offers using the existing Java pricing rules, and sends `purchase_amount`, `currency`, `evaluated_at`, and `offers` to the versioned optimizer endpoint below. Monetary values cross this service boundary as decimal strings; timestamps are timezone-aware ISO-8601 strings.

The optimizer returns `status`, `currency`, `base_amount`, `selected_offer_id`, `discount_amount`, `final_payable_amount`, `savings_amount`, `explanation`, `rejected_offers`, and `solver`. Java rejects responses with a non-optimal status, incompatible currency or solver metadata, unexpected rejected candidates, inconsistent amounts, or an offer outside the supplied candidate set. It also compares the selected discount and payable amount with Java's pricing result before returning the recommendation.

The optimization result is not persisted. The purchase remains a draft, and the operation does not create a proposal, approve a purchase, create a ledger expense, or execute a payment. HTTP/network failures and invalid responses are mapped to an explicit `503` by Java; neither service invents a fallback recommendation. Configure the Java service URL and timeouts with `OPTIMIZER_BASE_URL` (default `http://localhost:8000`), `OPTIMIZER_CONNECT_TIMEOUT` (default `1s`), and `OPTIMIZER_READ_TIMEOUT` (default `3s`). See the [root README](../README.md) for the local two-service setup.

## API

### `POST /api/v1/optimize`

Example request:

```json
{
  "purchase_amount": "250.00",
  "currency": "INR",
  "evaluated_at": "2026-10-14T00:00:00Z",
  "offers": [
    {
      "offer_id": "00000000-0000-0000-0000-000000000001",
      "offer_type": "PERCENTAGE_DISCOUNT",
      "active": true,
      "valid_from": "2026-10-01T00:00:00Z",
      "valid_until": "2026-11-01T00:00:00Z",
      "minimum_spend": "100.00",
      "maximum_discount": "50.00",
      "discount_percentage": "10.00",
      "discount_amount": null
    },
    {
      "offer_id": "00000000-0000-0000-0000-000000000002",
      "offer_type": "FIXED_DISCOUNT",
      "active": true,
      "valid_from": "2026-10-01T00:00:00Z",
      "valid_until": "2026-11-01T00:00:00Z",
      "minimum_spend": "0.00",
      "maximum_discount": null,
      "discount_percentage": null,
      "discount_amount": "20.00"
    }
  ]
}
```

The offer list may be empty. Offer IDs must be unique. Unknown fields, unsupported currencies or offer types, JSON numeric money values, excessive precision, inconsistent discount fields, and invalid date ranges are rejected with HTTP `422`. Eligibility failures are not request errors; they are listed in `rejected_offers`.

Example response (the percentage offer is cheaper than the fixed offer):

```json
{
  "status": "OPTIMAL",
  "currency": "INR",
  "base_amount": "250.00",
  "selected_offer_id": "00000000-0000-0000-0000-000000000001",
  "discount_amount": "25.00",
  "final_payable_amount": "225.00",
  "savings_amount": "25.00",
  "explanation": "Selected offer 00000000-0000-0000-0000-000000000001 for the lowest payable amount among eligible single-offer choices.",
  "rejected_offers": [],
  "solver": {
    "algorithm": "ortools-cp-sat",
    "solver_status": "OPTIMAL",
    "baseline_agrees": true
  }
}
```

When no offer is beneficial or eligible, `selected_offer_id` is `null`, the discount and savings are `"0.00"`, and final payable equals the base amount. Solver failures or a baseline disagreement return HTTP `503`; the service does not return an unverified recommendation. `GET /health` returns `{"status":"UP"}`.

## Install, run, and test

From PowerShell:

```powershell
Set-Location optimizer
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

The service listens on `http://127.0.0.1:8000`; interactive OpenAPI documentation is available at `http://127.0.0.1:8000/docs`. In another terminal with the environment activated, run:

```powershell
python -m pytest -q
```

Install runtime dependencies only with `python -m pip install -r requirements.txt`. The development requirements also include pytest and HTTPX for tests.

## Scope and limitations

- Only one purchase and at most one `PERCENTAGE_DISCOUNT` or `FIXED_DISCOUNT` offer are supported.
- The request includes all offer data needed for a stateless decision; there is no database or catalog lookup.
- Gift cards, stored-value balances, cashback, fees, offer stacking, payment routing, and a global shopping optimizer are out of scope.
- The output is an estimate only. No purchase, ledger entry, approval, or payment is created.

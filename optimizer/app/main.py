from fastapi import FastAPI, HTTPException

from app.models import OptimizeRequest, OptimizeResponse, SolverMetadata
from app.pricing import from_paise
from app.solver import OptimizationSolverError, solve_optimization


app = FastAPI(
    title="Paisa Vasool Optimization Service",
    version="1.0.0",
    description="Stateless single-offer purchase cost optimization for INR offers.",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/optimize", response_model=OptimizeResponse)
def optimize(request: OptimizeRequest) -> OptimizeResponse:
    try:
        outcome = solve_optimization(request)
    except OptimizationSolverError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error

    result = outcome.result
    return OptimizeResponse(
        status="OPTIMAL",
        currency=request.currency,
        base_amount=from_paise(result.base_amount_paise),
        selected_offer_id=(
            result.selected_offer.offer_id if result.selected_offer is not None else None
        ),
        discount_amount=from_paise(result.discount_paise),
        final_payable_amount=from_paise(result.final_payable_paise),
        savings_amount=from_paise(result.savings_paise),
        explanation=result.explanation,
        rejected_offers=list(result.rejected_offers),
        solver=SolverMetadata(
            algorithm="ortools-cp-sat",
            solver_status=outcome.solver_status,
            baseline_agrees=outcome.baseline_agrees,
        ),
    )

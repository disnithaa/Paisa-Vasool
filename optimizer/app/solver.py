from dataclasses import dataclass

from ortools.sat.python import cp_model

from app.models import OptimizeRequest
from app.pricing import (
    EvaluatedOffer,
    OptimizationResult,
    build_optimization_result,
    evaluate_offers,
    exhaustive_search,
)


class OptimizationSolverError(RuntimeError):
    pass


@dataclass(frozen=True)
class SolverOutcome:
    result: OptimizationResult
    solver_status: str
    baseline_agrees: bool


def solve_optimization(request: OptimizeRequest) -> SolverOutcome:
    evaluations = evaluate_offers(request)
    eligible = [
        evaluation
        for evaluation in evaluations
        if evaluation.ineligibility_reason is None
        and evaluation.discount_paise is not None
        and evaluation.discount_paise > 0
    ]
    eligible.sort(key=lambda evaluation: str(evaluation.offer.offer_id))

    model = cp_model.CpModel()
    no_offer = model.NewBoolVar("no_offer")
    offer_variables: list[tuple[EvaluatedOffer, cp_model.IntVar]] = []
    for index, evaluation in enumerate(eligible):
        offer_variables.append(
            (evaluation, model.NewBoolVar(f"offer_{index}"))
        )

    model.Add(
        no_offer + sum(variable for _, variable in offer_variables) == 1
    )
    base_paise = int(request.purchase_amount * 100)
    payable_expression = base_paise * no_offer + sum(
        evaluation.final_payable_paise * variable
        for evaluation, variable in offer_variables
    )
    model.Minimize(payable_expression)

    solver = _new_solver()
    status = solver.Solve(model)
    if status != cp_model.OPTIMAL:
        raise _solver_failure(solver, status, "primary cost minimization")

    optimal_payable_paise = solver.Value(payable_expression)
    model.Add(payable_expression == optimal_payable_paise)
    tie_break_expression = sum(
        rank * variable
        for rank, (_, variable) in enumerate(offer_variables, start=1)
    )
    model.Minimize(tie_break_expression)

    tie_status = solver.Solve(model)
    if tie_status != cp_model.OPTIMAL:
        raise _solver_failure(solver, tie_status, "deterministic tie-breaking")

    selected = next(
        (
            evaluation
            for evaluation, variable in offer_variables
            if solver.Value(variable) == 1
        ),
        None,
    )
    result = build_optimization_result(request, evaluations, selected)
    baseline = exhaustive_search(request)
    baseline_agrees = (
        result.final_payable_paise == baseline.final_payable_paise
        and _selected_offer_id(result) == _selected_offer_id(baseline)
    )
    if not baseline_agrees:
        raise OptimizationSolverError(
            "OR-Tools result does not agree with the exhaustive-search baseline"
        )

    return SolverOutcome(
        result=result,
        solver_status=solver.StatusName(tie_status),
        baseline_agrees=baseline_agrees,
    )


def _new_solver() -> cp_model.CpSolver:
    solver = cp_model.CpSolver()
    solver.parameters.num_search_workers = 1
    return solver


def _solver_failure(solver: cp_model.CpSolver, status: int, phase: str) -> OptimizationSolverError:
    return OptimizationSolverError(
        f"OR-Tools returned {solver.StatusName(status)} during {phase}"
    )


def _selected_offer_id(result: OptimizationResult) -> str | None:
    if result.selected_offer is None:
        return None
    return str(result.selected_offer.offer_id)

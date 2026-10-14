import random
from uuid import UUID

import pytest

from app.models import OptimizeRequest
from app.solver import OptimizationSolverError, solve_optimization
from tests.factories import fixed_offer, percentage_offer, request_data


def optimize(amount: str, *offers: dict[str, object]):
    request = OptimizeRequest.model_validate(
        request_data(purchase_amount=amount, offers=list(offers))
    )
    return solve_optimization(request)


def test_solver_uses_baseline_when_no_offers_exist() -> None:
    outcome = optimize("125.35")

    assert outcome.result.selected_offer is None
    assert outcome.result.final_payable_paise == 12535
    assert outcome.solver_status == "OPTIMAL"
    assert outcome.baseline_agrees


def test_solver_selects_lowest_cost_offer() -> None:
    outcome = optimize(
        "200.00",
        fixed_offer(discount_amount="20.00"),
        percentage_offer(
            minimum_spend="0.00",
            maximum_discount=None,
            discount_percentage="15.00",
        ),
    )

    assert outcome.result.selected_offer.offer_id == UUID(
        "00000000-0000-0000-0000-000000000001"
    )
    assert outcome.result.final_payable_paise == 17000
    assert outcome.baseline_agrees


def test_solver_uses_lexical_id_for_equal_cost_choices() -> None:
    outcome = optimize(
        "100.00",
        fixed_offer(discount_amount="10.00"),
        fixed_offer(
            offer_id="00000000-0000-0000-0000-000000000000",
            discount_amount="10.00",
        ),
    )

    assert str(outcome.result.selected_offer.offer_id) == "00000000-0000-0000-0000-000000000000"
    assert outcome.baseline_agrees


def test_solver_agrees_with_baseline_for_seeded_generated_cases() -> None:
    random_source = random.Random(20261014)

    for case_number in range(150):
        offers: list[dict[str, object]] = []
        for offer_number in range(random_source.randint(0, 12)):
            offer_id = str(UUID(int=case_number * 20 + offer_number + 1))
            if random_source.choice((True, False)):
                offers.append(
                    fixed_offer(
                        offer_id=offer_id,
                        active=random_source.choice((True, False)),
                        minimum_spend=f"{random_source.randint(0, 600)}.00",
                        discount_amount=f"{random_source.randint(0, 1200)}.{random_source.choice((0, 25, 50, 75)):02d}",
                    )
                )
            else:
                offers.append(
                    percentage_offer(
                        offer_id=offer_id,
                        active=random_source.choice((True, False)),
                        minimum_spend=f"{random_source.randint(0, 600)}.00",
                        discount_percentage=f"{random_source.randint(1, 100)}.00",
                        maximum_discount=(
                            None
                            if random_source.choice((True, False))
                            else f"{random_source.randint(0, 1200)}.00"
                        ),
                    )
                )
        request = OptimizeRequest.model_validate(
            request_data(
                purchase_amount=f"{random_source.randint(1, 1000)}.25",
                offers=offers,
            )
        )

        outcome = solve_optimization(request)

        assert outcome.baseline_agrees


def test_solver_raises_on_non_optimal_status(monkeypatch: pytest.MonkeyPatch) -> None:
    class FailedSolver:
        class Parameters:
            num_search_workers = 1

        parameters = Parameters()

        def Solve(self, model: object) -> int:
            return 0

        def StatusName(self, status: int) -> str:
            return "UNKNOWN"

    monkeypatch.setattr("app.solver._new_solver", lambda: FailedSolver())
    request = OptimizeRequest.model_validate(request_data())

    with pytest.raises(OptimizationSolverError, match="UNKNOWN"):
        solve_optimization(request)

from decimal import Decimal
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.models import OptimizeRequest, OptimizeResponse, OfferType, SolverMetadata
from tests.factories import (
    EVALUATED_AT,
    FIXED_OFFER_ID,
    PERCENT_OFFER_ID,
    VALID_FROM,
    VALID_UNTIL,
    fixed_offer,
    percentage_offer,
    request_data,
)


def test_accepts_percentage_and_fixed_offer_terms() -> None:
    request = OptimizeRequest.model_validate(
        request_data(offers=[percentage_offer(), fixed_offer()])
    )

    assert request.purchase_amount == Decimal("250.00")
    assert request.offers[0].offer_type == OfferType.PERCENTAGE_DISCOUNT
    assert request.offers[0].discount_percentage == Decimal("10.00")
    assert request.offers[1].offer_type == OfferType.FIXED_DISCOUNT
    assert request.offers[1].discount_amount == Decimal("25.00")


@pytest.mark.parametrize("amount", [250.0, 250, Decimal("250.00")])
def test_requires_money_as_decimal_string(amount: object) -> None:
    with pytest.raises(ValidationError, match="decimal string"):
        OptimizeRequest.model_validate(request_data(purchase_amount=amount))


@pytest.mark.parametrize("amount", ["1.001", "10000000000.00", "-1.00", "1e2", "NaN"])
def test_rejects_invalid_money_precision_or_format(amount: str) -> None:
    with pytest.raises(ValidationError):
        OptimizeRequest.model_validate(request_data(purchase_amount=amount))


@pytest.mark.parametrize("amount", ["0.00", "0"])
def test_purchase_amount_must_be_positive(amount: str) -> None:
    with pytest.raises(ValidationError, match="greater than zero"):
        OptimizeRequest.model_validate(request_data(purchase_amount=amount))


@pytest.mark.parametrize(
    ("offer", "message"),
    [
        (percentage_offer(discount_percentage="0.00"), "greater than zero"),
        (percentage_offer(discount_percentage="100.01"), "no more than 100"),
        (percentage_offer(discount_percentage="1.001"), "two decimal places"),
        (percentage_offer(discount_amount="1.00"), "must not define discount_amount"),
        (percentage_offer(discount_percentage=None), "require discount_percentage"),
        (fixed_offer(discount_amount=None), "require discount_amount"),
        (fixed_offer(discount_percentage="1.00"), "must not define percentage"),
        (fixed_offer(maximum_discount="5.00"), "must not define percentage"),
        (fixed_offer(discount_amount="-0.01"), "non-negative"),
    ],
)
def test_rejects_invalid_discount_terms(offer: dict[str, object], message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        OptimizeRequest.model_validate(request_data(offers=[offer]))


def test_rejects_unsupported_offer_type() -> None:
    with pytest.raises(ValidationError, match="Input should be"):
        OptimizeRequest.model_validate(
            request_data(offers=[percentage_offer(offer_type="GIFT_CARD")])
        )


@pytest.mark.parametrize(
    ("valid_from", "valid_until", "message"),
    [
        ("2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", "after valid_from"),
        ("2026-10-01T00:00:00", VALID_UNTIL, "valid_from must include a timezone"),
        (VALID_FROM, "2026-11-01T00:00:00", "valid_until must include a timezone"),
    ],
)
def test_rejects_invalid_offer_date_ranges(
    valid_from: str, valid_until: str, message: str
) -> None:
    offer = percentage_offer(valid_from=valid_from, valid_until=valid_until)
    with pytest.raises(ValidationError, match=message):
        OptimizeRequest.model_validate(request_data(offers=[offer]))


def test_evaluation_time_must_include_timezone() -> None:
    with pytest.raises(ValidationError, match="evaluated_at must include a timezone"):
        OptimizeRequest.model_validate(
            request_data(evaluated_at="2026-10-14T00:00:00")
        )


def test_currency_is_limited_to_inr() -> None:
    with pytest.raises(ValidationError, match="INR"):
        OptimizeRequest.model_validate(request_data(currency="USD"))


def test_offer_ids_must_be_unique() -> None:
    with pytest.raises(ValidationError, match="offer IDs must be unique"):
        OptimizeRequest.model_validate(
            request_data(offers=[percentage_offer(), percentage_offer()])
        )


def test_rejects_unknown_request_fields() -> None:
    with pytest.raises(ValidationError, match="eligible"):
        OptimizeRequest.model_validate(request_data(eligible=True))


def test_response_serializes_money_as_two_decimal_strings() -> None:
    response = OptimizeResponse(
        status="OPTIMAL",
        currency="INR",
        base_amount=Decimal("250"),
        selected_offer_id=UUID(PERCENT_OFFER_ID),
        discount_amount=Decimal("25"),
        final_payable_amount=Decimal("225"),
        savings_amount=Decimal("25"),
        explanation="Selected the best eligible discount.",
        solver=SolverMetadata(
            algorithm="ortools-cp-sat",
            solver_status="OPTIMAL",
            baseline_agrees=True,
        ),
    )

    assert response.model_dump(mode="json")["base_amount"] == "250.00"
    assert response.model_dump(mode="json")["discount_amount"] == "25.00"
    assert response.model_dump(mode="json")["final_payable_amount"] == "225.00"


def test_response_enforces_balance_equation() -> None:
    with pytest.raises(ValidationError, match="must equal base_amount"):
        OptimizeResponse(
            status="OPTIMAL",
            currency="INR",
            base_amount=Decimal("250.00"),
            selected_offer_id=None,
            discount_amount=Decimal("20.00"),
            final_payable_amount=Decimal("225.00"),
            savings_amount=Decimal("20.00"),
            explanation="Invalid accounting response.",
            solver=SolverMetadata(
                algorithm="ortools-cp-sat",
                solver_status="OPTIMAL",
                baseline_agrees=True,
            ),
        )


def test_response_without_offer_cannot_report_discount() -> None:
    with pytest.raises(ValidationError, match="without a selected offer"):
        OptimizeResponse(
            status="OPTIMAL",
            currency="INR",
            base_amount=Decimal("250.00"),
            selected_offer_id=None,
            discount_amount=Decimal("25.00"),
            final_payable_amount=Decimal("225.00"),
            savings_amount=Decimal("25.00"),
            explanation="Invalid no-offer response.",
            solver=SolverMetadata(
                algorithm="ortools-cp-sat",
                solver_status="OPTIMAL",
                baseline_agrees=True,
            ),
        )

from decimal import Decimal

import pytest

from app.models import IneligibilityReason, OptimizeRequest
from app.pricing import exhaustive_search, from_paise, to_paise
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


def optimize(amount: str, *offers: dict[str, object]):
    return exhaustive_search(
        OptimizeRequest.model_validate(
            request_data(purchase_amount=amount, offers=list(offers))
        )
    )


def test_no_offers_returns_baseline_amount() -> None:
    result = optimize("125.35")

    assert result.selected_offer is None
    assert result.discount_paise == 0
    assert result.final_payable_paise == 12535
    assert result.savings_paise == 0


def test_selects_percentage_discount_and_rounds_half_up_to_paise() -> None:
    result = optimize(
        "10.05",
        percentage_offer(
            minimum_spend="0.00",
            maximum_discount=None,
            discount_percentage="10.00",
        ),
    )

    assert result.selected_offer.offer_id.hex == PERCENT_OFFER_ID.replace("-", "")
    assert result.discount_paise == 101
    assert result.final_payable_paise == 904
    assert result.savings_paise + result.final_payable_paise == result.base_amount_paise


def test_selects_fixed_discount() -> None:
    result = optimize("100.00", fixed_offer(discount_amount="15.50"))

    assert result.selected_offer.offer_id.hex == FIXED_OFFER_ID.replace("-", "")
    assert result.discount_paise == 1550
    assert result.final_payable_paise == 8450


def test_uses_baseline_if_no_eligible_offer_is_beneficial() -> None:
    result = optimize(
        "100.00",
        fixed_offer(discount_amount="0.00"),
        percentage_offer(
            offer_id=PERCENT_OFFER_ID,
            minimum_spend="200.00",
            discount_percentage="10.00",
        ),
    )

    assert result.selected_offer is None
    assert result.discount_paise == 0
    assert result.final_payable_paise == result.base_amount_paise
    assert result.eligible_offer_count == 1
    assert result.rejected_offers[0].reason == IneligibilityReason.MINIMUM_SPEND_NOT_MET


def test_selects_lowest_payable_offer_from_multiple_candidates() -> None:
    result = optimize(
        "200.00",
        fixed_offer(discount_amount="20.00"),
        percentage_offer(
            minimum_spend="0.00",
            maximum_discount=None,
            discount_percentage="15.00",
        ),
    )

    assert result.selected_offer.offer_id.hex == PERCENT_OFFER_ID.replace("-", "")
    assert result.discount_paise == 3000
    assert result.final_payable_paise == 17000


@pytest.mark.parametrize(
    ("offer", "reason"),
    [
        (fixed_offer(active=False), IneligibilityReason.INACTIVE),
        (
            fixed_offer(valid_from="2026-10-15T00:00:00Z"),
            IneligibilityReason.NOT_YET_VALID,
        ),
        (
            fixed_offer(valid_until=EVALUATED_AT),
            IneligibilityReason.EXPIRED,
        ),
        (
            percentage_offer(minimum_spend="300.00"),
            IneligibilityReason.MINIMUM_SPEND_NOT_MET,
        ),
    ],
)
def test_records_reason_for_ineligible_offers(
    offer: dict[str, object], reason: IneligibilityReason
) -> None:
    result = optimize("250.00", offer)

    assert result.selected_offer is None
    assert len(result.rejected_offers) == 1
    assert result.rejected_offers[0].reason == reason


def test_validity_start_is_inclusive_and_end_is_exclusive() -> None:
    at_start = OptimizeRequest.model_validate(
        request_data(evaluated_at=VALID_FROM, offers=[fixed_offer()])
    )
    at_end = OptimizeRequest.model_validate(
        request_data(evaluated_at=VALID_UNTIL, offers=[fixed_offer()])
    )

    assert exhaustive_search(at_start).selected_offer is not None
    assert exhaustive_search(at_end).rejected_offers[0].reason == IneligibilityReason.EXPIRED


def test_applies_percentage_maximum_discount_cap() -> None:
    result = optimize(
        "500.00",
        percentage_offer(
            minimum_spend="0.00",
            discount_percentage="50.00",
            maximum_discount="30.00",
        ),
    )

    assert result.discount_paise == 3000
    assert result.final_payable_paise == 47000


def test_discount_cannot_exceed_purchase_amount() -> None:
    result = optimize("20.00", fixed_offer(discount_amount="100.00"))

    assert result.discount_paise == 2000
    assert result.final_payable_paise == 0
    assert result.final_payable_paise >= 0


def test_ties_choose_lexicographically_smallest_offer_id() -> None:
    larger_id = fixed_offer(discount_amount="10.00")
    smaller_id = fixed_offer(
        offer_id="00000000-0000-0000-0000-000000000000",
        discount_amount="10.00",
    )
    result = optimize("100.00", larger_id, smaller_id)

    assert str(result.selected_offer.offer_id) == "00000000-0000-0000-0000-000000000000"


def test_converts_decimal_amounts_to_and_from_paise_exactly() -> None:
    assert to_paise(Decimal("123456789.01")) == 12345678901
    assert from_paise(12345678901) == Decimal("123456789.01")


@pytest.mark.parametrize("amount", [Decimal("-0.01"), Decimal("1.001")])
def test_paise_conversion_rejects_negative_or_fractional_paise(amount: Decimal) -> None:
    with pytest.raises(ValueError):
        to_paise(amount)


def test_result_explanation_describes_no_offer_baseline() -> None:
    result = optimize("50.00")

    assert "baseline price is unchanged" in result.explanation

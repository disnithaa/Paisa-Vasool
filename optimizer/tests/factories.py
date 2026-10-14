EVALUATED_AT = "2026-10-14T00:00:00Z"
VALID_FROM = "2026-10-01T00:00:00Z"
VALID_UNTIL = "2026-11-01T00:00:00Z"
PERCENT_OFFER_ID = "00000000-0000-0000-0000-000000000001"
FIXED_OFFER_ID = "00000000-0000-0000-0000-000000000002"


def percentage_offer(**overrides: object) -> dict[str, object]:
    return {
        "offer_id": PERCENT_OFFER_ID,
        "offer_type": "PERCENTAGE_DISCOUNT",
        "active": True,
        "valid_from": VALID_FROM,
        "valid_until": VALID_UNTIL,
        "minimum_spend": "100.00",
        "maximum_discount": "50.00",
        "discount_percentage": "10.00",
        "discount_amount": None,
        **overrides,
    }


def fixed_offer(**overrides: object) -> dict[str, object]:
    return {
        "offer_id": FIXED_OFFER_ID,
        "offer_type": "FIXED_DISCOUNT",
        "active": True,
        "valid_from": VALID_FROM,
        "valid_until": VALID_UNTIL,
        "minimum_spend": "0.00",
        "maximum_discount": None,
        "discount_percentage": None,
        "discount_amount": "25.00",
        **overrides,
    }


def request_data(**overrides: object) -> dict[str, object]:
    return {
        "purchase_amount": "250.00",
        "currency": "INR",
        "evaluated_at": EVALUATED_AT,
        "offers": [],
        **overrides,
    }

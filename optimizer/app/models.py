import re
from datetime import datetime
from decimal import Decimal
from enum import Enum
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    AfterValidator,
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    PlainSerializer,
    model_validator,
)


_DECIMAL_STRING = re.compile(r"\d+(?:\.\d+)?\Z")
_MAX_MONEY_INTEGER_DIGITS = 10
_MAX_SCALE = 2


class OfferType(str, Enum):
    PERCENTAGE_DISCOUNT = "PERCENTAGE_DISCOUNT"
    FIXED_DISCOUNT = "FIXED_DISCOUNT"


class IneligibilityReason(str, Enum):
    INACTIVE = "INACTIVE"
    NOT_YET_VALID = "NOT_YET_VALID"
    EXPIRED = "EXPIRED"
    MINIMUM_SPEND_NOT_MET = "MINIMUM_SPEND_NOT_MET"


def _parse_decimal_string(value: object) -> Decimal:
    if not isinstance(value, str) or _DECIMAL_STRING.fullmatch(value) is None:
        raise ValueError("must be a non-negative decimal string without exponent notation")
    return Decimal(value)


def _validate_money(value: Decimal) -> Decimal:
    if not value.is_finite() or value < 0:
        raise ValueError("must be a finite, non-negative amount")
    if max(-value.as_tuple().exponent, 0) > _MAX_SCALE:
        raise ValueError("must have no more than two decimal places")
    integer_digits = max(value.adjusted() + 1, 0) if value else 0
    if integer_digits > _MAX_MONEY_INTEGER_DIGITS:
        raise ValueError("must fit NUMERIC(12, 2)")
    return value


def _validate_positive_money(value: Decimal) -> Decimal:
    _validate_money(value)
    if value <= 0:
        raise ValueError("must be greater than zero")
    return value


def _validate_percentage(value: Decimal) -> Decimal:
    if not value.is_finite() or value <= 0 or value > Decimal("100"):
        raise ValueError("must be greater than zero and no more than 100")
    if max(-value.as_tuple().exponent, 0) > _MAX_SCALE:
        raise ValueError("must have no more than two decimal places")
    if max(value.adjusted() + 1, 0) > 3:
        raise ValueError("must fit NUMERIC(5, 2)")
    return value


def _format_money(value: Decimal) -> str:
    return format(value, ".2f")


MoneyInput = Annotated[
    Decimal,
    BeforeValidator(_parse_decimal_string),
    AfterValidator(_validate_money),
]
PositiveMoneyInput = Annotated[
    Decimal,
    BeforeValidator(_parse_decimal_string),
    AfterValidator(_validate_positive_money),
]
PercentageInput = Annotated[
    Decimal,
    BeforeValidator(_parse_decimal_string),
    AfterValidator(_validate_percentage),
]
ResponseMoney = Annotated[
    Decimal,
    AfterValidator(_validate_money),
    PlainSerializer(_format_money, return_type=str, when_used="json"),
]


class OfferCandidate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    offer_id: UUID
    offer_type: OfferType
    active: bool = Field(strict=True)
    valid_from: datetime
    valid_until: datetime
    minimum_spend: MoneyInput
    maximum_discount: MoneyInput | None = None
    discount_percentage: PercentageInput | None = None
    discount_amount: MoneyInput | None = None

    @model_validator(mode="after")
    def validate_offer_terms(self) -> "OfferCandidate":
        if self.valid_from.tzinfo is None or self.valid_from.utcoffset() is None:
            raise ValueError("valid_from must include a timezone")
        if self.valid_until.tzinfo is None or self.valid_until.utcoffset() is None:
            raise ValueError("valid_until must include a timezone")
        if self.valid_until <= self.valid_from:
            raise ValueError("valid_until must be after valid_from")

        if self.offer_type == OfferType.PERCENTAGE_DISCOUNT:
            if self.discount_percentage is None or self.discount_amount is not None:
                raise ValueError(
                    "percentage offers require discount_percentage and must not define discount_amount"
                )
        elif (
            self.discount_amount is None
            or self.discount_percentage is not None
            or self.maximum_discount is not None
        ):
            raise ValueError(
                "fixed offers require discount_amount and must not define percentage or maximum_discount"
            )
        return self


class OptimizeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    purchase_amount: PositiveMoneyInput
    currency: Literal["INR"]
    evaluated_at: datetime
    offers: list[OfferCandidate] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_evaluation_context(self) -> "OptimizeRequest":
        if self.evaluated_at.tzinfo is None or self.evaluated_at.utcoffset() is None:
            raise ValueError("evaluated_at must include a timezone")
        offer_ids = [offer.offer_id for offer in self.offers]
        if len(offer_ids) != len(set(offer_ids)):
            raise ValueError("offer IDs must be unique")
        return self


class RejectedOffer(BaseModel):
    offer_id: UUID
    reason: IneligibilityReason


class SolverMetadata(BaseModel):
    algorithm: Literal["ortools-cp-sat"]
    solver_status: Literal["OPTIMAL"]
    baseline_agrees: bool


class OptimizeResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: Literal["OPTIMAL"]
    currency: Literal["INR"]
    base_amount: ResponseMoney
    selected_offer_id: UUID | None
    discount_amount: ResponseMoney
    final_payable_amount: ResponseMoney
    savings_amount: ResponseMoney
    explanation: str
    rejected_offers: list[RejectedOffer] = Field(default_factory=list)
    solver: SolverMetadata

    @model_validator(mode="after")
    def validate_accounting(self) -> "OptimizeResponse":
        if self.discount_amount != self.savings_amount:
            raise ValueError("discount_amount must equal savings_amount")
        if self.base_amount != self.final_payable_amount + self.savings_amount:
            raise ValueError("final_payable_amount plus savings_amount must equal base_amount")
        if self.selected_offer_id is None and self.discount_amount != Decimal("0"):
            raise ValueError("a response without a selected offer must have no discount")
        if self.selected_offer_id is not None and self.discount_amount <= 0:
            raise ValueError("a selected offer must provide a positive discount")
        return self

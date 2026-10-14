from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP, localcontext

from app.models import (
    IneligibilityReason,
    OfferCandidate,
    OfferType,
    OptimizeRequest,
    RejectedOffer,
)


_PAISE_PER_RUPEE = Decimal("100")
_ONE_HUNDRED = Decimal("100")


def to_paise(amount: Decimal) -> int:
    if not amount.is_finite() or amount < 0:
        raise ValueError("amount must be a finite, non-negative decimal")
    if max(-amount.as_tuple().exponent, 0) > 2:
        raise ValueError("amount must have no more than two decimal places")
    paise = amount * _PAISE_PER_RUPEE
    integral_paise = paise.to_integral_value()
    if paise != integral_paise:
        raise ValueError("amount cannot be represented as a whole number of paise")
    return int(integral_paise)


def from_paise(paise: int) -> Decimal:
    if paise < 0:
        raise ValueError("paise must not be negative")
    return Decimal(paise).scaleb(-2)


@dataclass(frozen=True)
class EvaluatedOffer:
    offer: OfferCandidate
    ineligibility_reason: IneligibilityReason | None
    discount_paise: int | None
    final_payable_paise: int | None

    def __post_init__(self) -> None:
        is_eligible = self.ineligibility_reason is None
        amounts_are_present = (
            self.discount_paise is not None and self.final_payable_paise is not None
        )
        if is_eligible != amounts_are_present:
            raise ValueError("eligibility and calculated amounts must be consistent")
        if self.discount_paise is not None and self.discount_paise < 0:
            raise ValueError("discount must not be negative")
        if self.final_payable_paise is not None and self.final_payable_paise < 0:
            raise ValueError("final payable must not be negative")


@dataclass(frozen=True)
class OptimizationResult:
    base_amount_paise: int
    selected_offer: OfferCandidate | None
    discount_paise: int
    final_payable_paise: int
    rejected_offers: tuple[RejectedOffer, ...]
    eligible_offer_count: int

    def __post_init__(self) -> None:
        if self.base_amount_paise <= 0:
            raise ValueError("base amount must be positive")
        if self.discount_paise < 0 or self.final_payable_paise < 0:
            raise ValueError("discount and payable amounts must not be negative")
        if self.discount_paise + self.final_payable_paise != self.base_amount_paise:
            raise ValueError("discount plus final payable must equal the base amount")
        if self.selected_offer is None and self.discount_paise != 0:
            raise ValueError("a result without an offer must not include a discount")
        if self.selected_offer is not None and self.discount_paise <= 0:
            raise ValueError("a selected offer must provide a positive discount")

    @property
    def savings_paise(self) -> int:
        return self.discount_paise

    @property
    def explanation(self) -> str:
        if self.selected_offer is None:
            return "No beneficial eligible offer was available; the baseline price is unchanged."
        return (
            f"Selected offer {self.selected_offer.offer_id} for the lowest payable amount "
            "among eligible single-offer choices."
        )


def evaluate_offer(
    request: OptimizeRequest, offer: OfferCandidate
) -> EvaluatedOffer:
    if not offer.active:
        return EvaluatedOffer(offer, IneligibilityReason.INACTIVE, None, None)
    if request.evaluated_at < offer.valid_from:
        return EvaluatedOffer(offer, IneligibilityReason.NOT_YET_VALID, None, None)
    if request.evaluated_at >= offer.valid_until:
        return EvaluatedOffer(offer, IneligibilityReason.EXPIRED, None, None)
    if request.purchase_amount < offer.minimum_spend:
        return EvaluatedOffer(offer, IneligibilityReason.MINIMUM_SPEND_NOT_MET, None, None)

    base_paise = to_paise(request.purchase_amount)
    discount_paise = _calculate_discount_paise(base_paise, offer)
    payable_paise = base_paise - discount_paise
    return EvaluatedOffer(offer, None, discount_paise, payable_paise)


def evaluate_offers(request: OptimizeRequest) -> tuple[EvaluatedOffer, ...]:
    return tuple(evaluate_offer(request, offer) for offer in request.offers)


def build_optimization_result(
    request: OptimizeRequest,
    evaluations: tuple[EvaluatedOffer, ...],
    selected: EvaluatedOffer | None,
) -> OptimizationResult:
    base_paise = to_paise(request.purchase_amount)
    rejected = tuple(
        RejectedOffer(offer_id=evaluation.offer.offer_id, reason=evaluation.ineligibility_reason)
        for evaluation in evaluations
        if evaluation.ineligibility_reason is not None
    )
    discount_paise = selected.discount_paise if selected is not None else 0
    final_payable_paise = (
        selected.final_payable_paise if selected is not None else base_paise
    )
    eligible_offer_count = sum(
        evaluation.ineligibility_reason is None for evaluation in evaluations
    )
    return OptimizationResult(
        base_amount_paise=base_paise,
        selected_offer=selected.offer if selected is not None else None,
        discount_paise=discount_paise,
        final_payable_paise=final_payable_paise,
        rejected_offers=rejected,
        eligible_offer_count=eligible_offer_count,
    )


def exhaustive_search(request: OptimizeRequest) -> OptimizationResult:
    evaluations = evaluate_offers(request)
    eligible = [evaluation for evaluation in evaluations if evaluation.ineligibility_reason is None]
    beneficial = [
        evaluation
        for evaluation in eligible
        if evaluation.discount_paise is not None and evaluation.discount_paise > 0
    ]
    selected = min(
        beneficial,
        key=lambda evaluation: (
            evaluation.final_payable_paise,
            str(evaluation.offer.offer_id),
        ),
        default=None,
    )
    return build_optimization_result(request, evaluations, selected)


def _calculate_discount_paise(base_paise: int, offer: OfferCandidate) -> int:
    if offer.offer_type == OfferType.FIXED_DISCOUNT:
        discount_paise = to_paise(offer.discount_amount)
    else:
        with localcontext() as context:
            context.prec = 50
            percentage_discount_paise = (
                Decimal(base_paise) * offer.discount_percentage / _ONE_HUNDRED
            )
            discount_paise = int(
                percentage_discount_paise.to_integral_value(rounding=ROUND_HALF_UP)
            )

    if offer.maximum_discount is not None:
        discount_paise = min(discount_paise, to_paise(offer.maximum_discount))
    return min(discount_paise, base_paise)

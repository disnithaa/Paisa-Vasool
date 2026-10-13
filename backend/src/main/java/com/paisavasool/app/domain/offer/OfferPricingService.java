package com.paisavasool.app.domain.offer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paisavasool.app.domain.money.MoneyAmount;

@Service
public class OfferPricingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public OfferEvaluation evaluate(Offer offer, BigDecimal purchaseAmount, Instant evaluatedAt) {
        MoneyAmount.requirePositiveInr(purchaseAmount, "purchaseAmount");
        if (offer == null || evaluatedAt == null) {
            throw new IllegalArgumentException("offer and evaluatedAt must not be null");
        }
        if (!offer.isActive()) {
            return OfferEvaluation.ineligible(OfferIneligibilityReason.INACTIVE);
        }
        if (evaluatedAt.isBefore(offer.getValidFrom())) {
            return OfferEvaluation.ineligible(OfferIneligibilityReason.NOT_YET_VALID);
        }
        if (!evaluatedAt.isBefore(offer.getValidUntil())) {
            return OfferEvaluation.ineligible(OfferIneligibilityReason.EXPIRED);
        }
        if (purchaseAmount.compareTo(offer.getMinimumSpend()) < 0) {
            return OfferEvaluation.ineligible(OfferIneligibilityReason.MINIMUM_SPEND_NOT_MET);
        }

        BigDecimal discount = calculateDiscount(offer, purchaseAmount);
        if (offer.getMaximumDiscount() != null) {
            discount = discount.min(offer.getMaximumDiscount());
        }
        discount = discount.min(purchaseAmount).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal finalAmount = purchaseAmount.subtract(discount).setScale(2, RoundingMode.UNNECESSARY);
        return OfferEvaluation.eligible(discount, finalAmount);
    }

    private static BigDecimal calculateDiscount(Offer offer, BigDecimal purchaseAmount) {
        if (offer.getOfferType() == OfferType.FIXED_DISCOUNT) {
            return offer.getDiscountAmount();
        }
        return purchaseAmount
            .multiply(offer.getDiscountPercentage())
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }
}

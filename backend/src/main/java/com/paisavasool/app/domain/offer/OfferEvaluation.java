package com.paisavasool.app.domain.offer;

import java.math.BigDecimal;

public record OfferEvaluation(
    boolean eligible,
    OfferIneligibilityReason ineligibilityReason,
    BigDecimal discountAmount,
    BigDecimal finalAmount
) {
    public OfferEvaluation {
        if (eligible && (ineligibilityReason != null || discountAmount == null || finalAmount == null)) {
            throw new IllegalArgumentException("Eligible evaluations require calculated amounts and no rejection reason");
        }
        if (!eligible && (ineligibilityReason == null || discountAmount != null || finalAmount != null)) {
            throw new IllegalArgumentException("Ineligible evaluations require a rejection reason and no calculated amounts");
        }
    }

    public static OfferEvaluation eligible(BigDecimal discountAmount, BigDecimal finalAmount) {
        return new OfferEvaluation(true, null, discountAmount, finalAmount);
    }

    public static OfferEvaluation ineligible(OfferIneligibilityReason reason) {
        return new OfferEvaluation(false, reason, null, null);
    }
}

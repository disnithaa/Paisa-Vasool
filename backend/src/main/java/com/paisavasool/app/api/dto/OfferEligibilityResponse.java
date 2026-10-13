package com.paisavasool.app.api.dto;

import java.math.BigDecimal;

import com.paisavasool.app.domain.offer.OfferEvaluation;
import com.paisavasool.app.domain.offer.OfferIneligibilityReason;

public record OfferEligibilityResponse(
    OfferResponse offer,
    boolean eligible,
    OfferIneligibilityReason ineligibilityReason,
    BigDecimal discountAmount,
    BigDecimal finalAmount
) {
    public static OfferEligibilityResponse from(OfferResponse offer, OfferEvaluation evaluation) {
        return new OfferEligibilityResponse(
            offer,
            evaluation.eligible(),
            evaluation.ineligibilityReason(),
            evaluation.discountAmount(),
            evaluation.finalAmount()
        );
    }
}

package com.paisavasool.app.integration.optimizer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.paisavasool.app.api.dto.OfferResponse;
import com.paisavasool.app.integration.optimizer.dto.OptimizerOfferCandidate;

public record PurchaseOptimizationData(
    UUID purchaseId,
    BigDecimal baseAmount,
    Instant evaluatedAt,
    List<EligibleOffer> eligibleOffers
) {

    public PurchaseOptimizationData {
        eligibleOffers = List.copyOf(eligibleOffers);
    }

    public record EligibleOffer(
        OptimizerOfferCandidate optimizerCandidate,
        OfferResponse offer,
        BigDecimal expectedDiscount,
        BigDecimal expectedFinalAmount
    ) {
    }
}

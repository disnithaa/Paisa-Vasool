package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOptimizationResponse(
    UUID purchaseId,
    String status,
    String currency,
    BigDecimal baseAmount,
    UUID selectedOfferId,
    OfferResponse selectedOffer,
    BigDecimal discountAmount,
    BigDecimal finalPayableAmount,
    BigDecimal savingsAmount,
    String explanation,
    OptimizationSolverResponse solver
) {
}

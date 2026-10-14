package com.paisavasool.app.integration.optimizer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptimizerRequest(
    @JsonProperty("purchase_amount") String purchaseAmount,
    @JsonProperty("currency") String currency,
    @JsonProperty("evaluated_at") String evaluatedAt,
    @JsonProperty("offers") List<OptimizerOfferCandidate> offers
) {

    public static String decimalString(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        return amount.toPlainString();
    }

    public static String instantString(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return instant.toString();
    }
}

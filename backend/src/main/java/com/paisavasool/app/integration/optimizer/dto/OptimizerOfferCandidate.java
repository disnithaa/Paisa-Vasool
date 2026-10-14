package com.paisavasool.app.integration.optimizer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paisavasool.app.domain.offer.OfferType;

public record OptimizerOfferCandidate(
    @JsonProperty("offer_id") UUID offerId,
    @JsonProperty("offer_type") OfferType offerType,
    @JsonProperty("active") boolean active,
    @JsonProperty("valid_from") String validFrom,
    @JsonProperty("valid_until") String validUntil,
    @JsonProperty("minimum_spend") String minimumSpend,
    @JsonProperty("maximum_discount") String maximumDiscount,
    @JsonProperty("discount_percentage") String discountPercentage,
    @JsonProperty("discount_amount") String discountAmount
) {

    public static OptimizerOfferCandidate from(
        UUID offerId,
        OfferType offerType,
        boolean active,
        Instant validFrom,
        Instant validUntil,
        BigDecimal minimumSpend,
        BigDecimal maximumDiscount,
        BigDecimal discountPercentage,
        BigDecimal discountAmount
    ) {
        return new OptimizerOfferCandidate(
            offerId,
            offerType,
            active,
            OptimizerRequest.instantString(validFrom),
            OptimizerRequest.instantString(validUntil),
            OptimizerRequest.decimalString(minimumSpend),
            optionalDecimalString(maximumDiscount),
            optionalDecimalString(discountPercentage),
            optionalDecimalString(discountAmount)
        );
    }

    private static String optionalDecimalString(BigDecimal amount) {
        return amount == null ? null : OptimizerRequest.decimalString(amount);
    }
}

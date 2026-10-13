package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferType;

public record OfferResponse(
    UUID id,
    String merchantName,
    String title,
    String description,
    OfferType offerType,
    BigDecimal discountPercentage,
    BigDecimal discountAmount,
    BigDecimal minimumSpend,
    BigDecimal maximumDiscount,
    Instant validFrom,
    Instant validUntil,
    boolean active,
    boolean sample,
    String terms
) {
    public static OfferResponse from(Offer offer) {
        return new OfferResponse(
            offer.getId(),
            offer.getMerchantName(),
            offer.getTitle(),
            offer.getDescription(),
            offer.getOfferType(),
            offer.getDiscountPercentage(),
            offer.getDiscountAmount(),
            offer.getMinimumSpend(),
            offer.getMaximumDiscount(),
            offer.getValidFrom(),
            offer.getValidUntil(),
            offer.isActive(),
            offer.isSample(),
            offer.getTerms()
        );
    }
}

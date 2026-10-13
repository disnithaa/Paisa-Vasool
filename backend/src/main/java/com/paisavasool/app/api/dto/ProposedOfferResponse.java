package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.offer.OfferType;

public record ProposedOfferResponse(
    UUID offerId,
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
    String terms
) {
    public static ProposedOfferResponse from(Purchase purchase) {
        return new ProposedOfferResponse(
            purchase.getProposedOffer().getId(),
            purchase.getOfferMerchantSnapshot(),
            purchase.getOfferTitleSnapshot(),
            purchase.getOfferDescriptionSnapshot(),
            purchase.getOfferTypeSnapshot(),
            purchase.getOfferDiscountPercentageSnapshot(),
            purchase.getOfferDiscountAmountSnapshot(),
            purchase.getOfferMinimumSpendSnapshot(),
            purchase.getOfferMaximumDiscountSnapshot(),
            purchase.getOfferValidFromSnapshot(),
            purchase.getOfferValidUntilSnapshot(),
            purchase.getOfferTermsSnapshot()
        );
    }
}

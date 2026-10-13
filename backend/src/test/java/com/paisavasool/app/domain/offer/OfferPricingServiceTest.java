package com.paisavasool.app.domain.offer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OfferPricingServiceTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2027-01-01T00:00:00Z");
    private static final Instant DURING_VALIDITY = Instant.parse("2026-06-01T00:00:00Z");

    private final OfferPricingService pricingService = new OfferPricingService();

    @Test
    void calculatesPercentageDiscountAndRoundsHalfUpToPaise() {
        Offer offer = percentageOffer("10.00", null, BigDecimal.ZERO, true);

        OfferEvaluation evaluation = pricingService.evaluate(
            offer,
            new BigDecimal("99.99"),
            DURING_VALIDITY
        );

        assertThat(evaluation.eligible()).isTrue();
        assertThat(evaluation.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(evaluation.finalAmount()).isEqualByComparingTo("89.99");
    }

    @Test
    void calculatesFixedDiscount() {
        Offer offer = fixedOffer("75.00", "300.00", true);

        OfferEvaluation evaluation = pricingService.evaluate(
            offer,
            new BigDecimal("400.00"),
            DURING_VALIDITY
        );

        assertThat(evaluation.discountAmount()).isEqualByComparingTo("75.00");
        assertThat(evaluation.finalAmount()).isEqualByComparingTo("325.00");
    }

    @Test
    void rejectsPurchaseBelowMinimumSpend() {
        Offer offer = fixedOffer("75.00", "300.00", true);

        OfferEvaluation evaluation = pricingService.evaluate(
            offer,
            new BigDecimal("299.99"),
            DURING_VALIDITY
        );

        assertThat(evaluation.eligible()).isFalse();
        assertThat(evaluation.ineligibilityReason())
            .isEqualTo(OfferIneligibilityReason.MINIMUM_SPEND_NOT_MET);
        assertThat(evaluation.discountAmount()).isNull();
        assertThat(evaluation.finalAmount()).isNull();
    }

    @Test
    void rejectsOffersBeforeStartAndAtExclusiveEndTime() {
        Offer offer = fixedOffer("10.00", "0.00", true);

        assertThat(pricingService.evaluate(
            offer, new BigDecimal("20.00"), VALID_FROM.minusNanos(1)
        ).ineligibilityReason()).isEqualTo(OfferIneligibilityReason.NOT_YET_VALID);

        assertThat(pricingService.evaluate(
            offer, new BigDecimal("20.00"), VALID_UNTIL
        ).ineligibilityReason()).isEqualTo(OfferIneligibilityReason.EXPIRED);
    }

    @Test
    void rejectsInactiveOfferEvenWithinDateRange() {
        Offer offer = fixedOffer("10.00", "0.00", false);

        assertThat(pricingService.evaluate(
            offer, new BigDecimal("20.00"), DURING_VALIDITY
        ).ineligibilityReason()).isEqualTo(OfferIneligibilityReason.INACTIVE);
    }

    @Test
    void capsPercentageDiscountAtMaximumDiscount() {
        Offer offer = percentageOffer("20.00", new BigDecimal("100.00"), BigDecimal.ZERO, true);

        OfferEvaluation evaluation = pricingService.evaluate(
            offer,
            new BigDecimal("1000.00"),
            DURING_VALIDITY
        );

        assertThat(evaluation.discountAmount()).isEqualByComparingTo("100.00");
        assertThat(evaluation.finalAmount()).isEqualByComparingTo("900.00");
    }

    @Test
    void discountCannotReduceFinalAmountBelowZero() {
        Offer offer = fixedOffer("50.00", "0.00", true);

        OfferEvaluation evaluation = pricingService.evaluate(
            offer,
            new BigDecimal("25.00"),
            DURING_VALIDITY
        );

        assertThat(evaluation.discountAmount()).isEqualByComparingTo("25.00");
        assertThat(evaluation.finalAmount()).isEqualByComparingTo("0.00");
    }

    private static Offer percentageOffer(
        String percentage,
        BigDecimal maximumDiscount,
        BigDecimal minimumSpend,
        boolean active
    ) {
        return new Offer(
            "Sample Merchant",
            "Sample percentage offer",
            "Fictional pricing test offer",
            OfferType.PERCENTAGE_DISCOUNT,
            new BigDecimal(percentage),
            null,
            minimumSpend,
            maximumDiscount,
            VALID_FROM,
            VALID_UNTIL,
            active,
            true,
            "Fictional test terms"
        );
    }

    private static Offer fixedOffer(String amount, String minimumSpend, boolean active) {
        return new Offer(
            "Sample Merchant",
            "Sample fixed offer",
            "Fictional pricing test offer",
            OfferType.FIXED_DISCOUNT,
            null,
            new BigDecimal(amount),
            new BigDecimal(minimumSpend),
            null,
            VALID_FROM,
            VALID_UNTIL,
            active,
            true,
            "Fictional test terms"
        );
    }
}

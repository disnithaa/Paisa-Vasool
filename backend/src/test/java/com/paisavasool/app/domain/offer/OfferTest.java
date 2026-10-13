package com.paisavasool.app.domain.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OfferTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void createsPercentageDiscountWithExplicitPercentageAndCap() {
        Offer offer = offer(
            OfferType.PERCENTAGE_DISCOUNT,
            new BigDecimal("12.50"),
            null,
            new BigDecimal("500.00"),
            new BigDecimal("100.00")
        );

        assertThat(offer.getOfferType()).isEqualTo(OfferType.PERCENTAGE_DISCOUNT);
        assertThat(offer.getDiscountPercentage()).isEqualByComparingTo("12.50");
        assertThat(offer.getDiscountAmount()).isNull();
        assertThat(offer.getMaximumDiscount()).isEqualByComparingTo("100.00");
    }

    @Test
    void createsFixedDiscountWithoutPercentageOrCap() {
        Offer offer = offer(
            OfferType.FIXED_DISCOUNT,
            null,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            null
        );

        assertThat(offer.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(offer.isSample()).isTrue();
        offer.deactivate();
        assertThat(offer.isActive()).isFalse();
    }

    @Test
    void rejectsMissingOrConflictingDiscountValues() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            offer(OfferType.PERCENTAGE_DISCOUNT, null, null, BigDecimal.ZERO, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            offer(OfferType.FIXED_DISCOUNT, new BigDecimal("10.00"), new BigDecimal("10.00"), BigDecimal.ZERO, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            offer(OfferType.FIXED_DISCOUNT, null, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rejectsInvalidPercentageAndMonetaryValues() {
        for (String percentage : new String[] {"0.00", "100.01", "-1.00", "10.001"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                offer(OfferType.PERCENTAGE_DISCOUNT, new BigDecimal(percentage), null, BigDecimal.ZERO, null));
        }

        for (BigDecimal amount : new BigDecimal[] {
            new BigDecimal("-1.00"),
            new BigDecimal("1.001"),
            new BigDecimal("10000000000.00")
        }) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                offer(OfferType.FIXED_DISCOUNT, null, amount, BigDecimal.ZERO, null));
        }
    }

    @Test
    void rejectsNegativeMinimumSpendAndInvalidDateRange() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            offer(OfferType.FIXED_DISCOUNT, null, BigDecimal.ONE, new BigDecimal("-0.01"), null));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new Offer(
                "Sample Merchant",
                "Sample offer",
                "Fictional test entry",
                OfferType.FIXED_DISCOUNT,
                null,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                VALID_UNTIL,
                VALID_FROM,
                true,
                true,
                "Fictional test terms"
            ));
    }

    private static Offer offer(
        OfferType type,
        BigDecimal percentage,
        BigDecimal amount,
        BigDecimal minimumSpend,
        BigDecimal maximumDiscount
    ) {
        return new Offer(
            "Sample Merchant",
            "Sample offer",
            "Fictional test entry",
            type,
            percentage,
            amount,
            minimumSpend,
            maximumDiscount,
            VALID_FROM,
            VALID_UNTIL,
            true,
            true,
            "Fictional test terms"
        );
    }
}

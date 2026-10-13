package com.paisavasool.app.domain.offer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "offers", schema = "app")
public class Offer {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String merchantName;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferType offerType;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @NotNull
    @Column(name = "minimum_spend", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumSpend;

    @Column(name = "maximum_discount", precision = 12, scale = 2)
    private BigDecimal maximumDiscount;

    @NotNull
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @NotNull
    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @NotBlank
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String terms;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Offer() {
    }

    public Offer(
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
        this.merchantName = requireText(merchantName, "merchantName");
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.offerType = requireValue(offerType, "offerType");
        this.minimumSpend = requireNonNegativeMoney(minimumSpend, "minimumSpend");
        this.validFrom = requireValue(validFrom, "validFrom");
        this.validUntil = requireValue(validUntil, "validUntil");
        if (!validFrom.isBefore(validUntil)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        this.active = active;
        this.sample = sample;
        this.terms = requireText(terms, "terms");
        validateDiscountTerms(
            offerType,
            discountPercentage,
            discountAmount,
            maximumDiscount
        );
        this.discountPercentage = discountPercentage;
        this.discountAmount = discountAmount;
        this.maximumDiscount = maximumDiscount;
    }

    public void deactivate() {
        active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public OfferType getOfferType() {
        return offerType;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getMinimumSpend() {
        return minimumSpend;
    }

    public BigDecimal getMaximumDiscount() {
        return maximumDiscount;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSample() {
        return sample;
    }

    public String getTerms() {
        return terms;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void validateDiscountTerms(
        OfferType offerType,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal maximumDiscount
    ) {
        if (offerType == OfferType.PERCENTAGE_DISCOUNT) {
            if (discountPercentage == null || discountPercentage.signum() <= 0
                || discountPercentage.compareTo(ONE_HUNDRED) > 0
                || discountPercentage.scale() > 2
                || discountAmount != null) {
                throw new IllegalArgumentException(
                    "Percentage discounts require a percentage in (0, 100] with no fixed amount"
                );
            }
            if (maximumDiscount != null) {
                requireNonNegativeMoney(maximumDiscount, "maximumDiscount");
            }
        } else if (discountPercentage != null || discountAmount == null || maximumDiscount != null) {
            throw new IllegalArgumentException(
                "Fixed discounts require a fixed amount and must not define percentage or maximum discount"
            );
        } else {
            requireNonNegativeMoney(discountAmount, "discountAmount");
        }
    }

    private static BigDecimal requireNonNegativeMoney(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        int integerDigits = amount.precision() - amount.scale();
        if (amount.scale() > 2 || integerDigits > 10) {
            throw new IllegalArgumentException(fieldName + " must fit NUMERIC(12, 2)");
        }
        return amount;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}

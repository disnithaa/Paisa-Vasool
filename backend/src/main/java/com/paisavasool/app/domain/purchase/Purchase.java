package com.paisavasool.app.domain.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.money.MoneyAmount;
import com.paisavasool.app.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "purchases", schema = "app")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Purchase() {
    }

    public Purchase(Group group, User createdBy, String title, BigDecimal estimatedAmount) {
        this.group = requireValue(group, "group");
        this.createdBy = requireValue(createdBy, "createdBy");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.title = title.trim();
        this.estimatedAmount = MoneyAmount.requirePositiveInr(estimatedAmount, "estimatedAmount");
        this.status = PurchaseStatus.DRAFT;
    }

    public void transitionTo(PurchaseStatus nextStatus) {
        if (nextStatus == null || !isAllowedTransition(status, nextStatus)) {
            throw new IllegalStateException("Invalid purchase status transition: " + status + " -> " + nextStatus);
        }
        status = nextStatus;
    }

    private static boolean isAllowedTransition(PurchaseStatus current, PurchaseStatus next) {
        return switch (current) {
            case DRAFT -> next == PurchaseStatus.PENDING_APPROVAL || next == PurchaseStatus.CANCELLED;
            case PENDING_APPROVAL -> next == PurchaseStatus.APPROVED || next == PurchaseStatus.CANCELLED;
            case APPROVED -> next == PurchaseStatus.COMPLETED || next == PurchaseStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.money.MoneyAmount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "settlements", schema = "app")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_membership_id", nullable = false)
    private GroupMembership fromMember;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_membership_id", nullable = false)
    private GroupMembership toMember;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_membership_id", nullable = false)
    private GroupMembership createdBy;

    @Column(nullable = false, updatable = false)
    private Instant settledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Settlement() {
    }

    public Settlement(
        Group group,
        GroupMembership fromMember,
        GroupMembership toMember,
        BigDecimal amount,
        GroupMembership createdBy,
        Instant settledAt
    ) {
        this.group = requireValue(group, "group");
        this.fromMember = requireValue(fromMember, "fromMember");
        this.toMember = requireValue(toMember, "toMember");
        if (fromMember == toMember
            || (fromMember.getId() != null && fromMember.getId().equals(toMember.getId()))) {
            throw new IllegalArgumentException("fromMember and toMember must be different members");
        }
        this.amount = MoneyAmount.requirePositiveInr(amount, "amount");
        this.createdBy = requireValue(createdBy, "createdBy");
        this.settledAt = requireValue(settledAt, "settledAt");
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public GroupMembership getFromMember() {
        return fromMember;
    }

    public GroupMembership getToMember() {
        return toMember;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public GroupMembership getCreatedBy() {
        return createdBy;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}

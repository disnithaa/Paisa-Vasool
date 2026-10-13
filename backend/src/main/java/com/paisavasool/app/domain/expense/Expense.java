package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.money.MoneyAmount;
import com.paisavasool.app.domain.purchase.Purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "expenses", schema = "app")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String description;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_membership_id", nullable = false)
    private GroupMembership paidBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_membership_id", nullable = false)
    private GroupMembership createdBy;

    @Column(nullable = false, updatable = false)
    private Instant incurredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.PERSIST)
    private List<ExpenseAllocation> allocations = new ArrayList<>();

    protected Expense() {
    }

    public Expense(
        Group group,
        Purchase purchase,
        String description,
        BigDecimal totalAmount,
        GroupMembership paidBy,
        GroupMembership createdBy,
        Instant incurredAt
    ) {
        this.group = requireValue(group, "group");
        this.purchase = purchase;
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        this.description = description.trim();
        this.totalAmount = MoneyAmount.requirePositiveInr(totalAmount, "totalAmount");
        this.paidBy = requireValue(paidBy, "paidBy");
        this.createdBy = requireValue(createdBy, "createdBy");
        this.incurredAt = requireValue(incurredAt, "incurredAt");
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public GroupMembership getPaidBy() {
        return paidBy;
    }

    public GroupMembership getCreatedBy() {
        return createdBy;
    }

    public Instant getIncurredAt() {
        return incurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExpenseAllocation> getAllocations() {
        return List.copyOf(allocations);
    }

    public void addAllocation(ExpenseAllocation allocation) {
        ExpenseAllocation checkedAllocation = requireValue(allocation, "allocation");
        if (checkedAllocation.getExpense() != this) {
            throw new IllegalArgumentException("allocation must belong to this expense");
        }
        allocations.add(checkedAllocation);
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}

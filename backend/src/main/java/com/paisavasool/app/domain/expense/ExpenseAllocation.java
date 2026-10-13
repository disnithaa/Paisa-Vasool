package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.util.UUID;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "expense_allocations",
    schema = "app",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_expense_allocations_expense_member",
        columnNames = {"expense_id", "membership_id"}
    )
)
public class ExpenseAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private GroupMembership member;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected ExpenseAllocation() {
    }

    public ExpenseAllocation(Expense expense, GroupMembership member, BigDecimal amount) {
        if (expense == null) {
            throw new IllegalArgumentException("expense must not be null");
        }
        if (member == null) {
            throw new IllegalArgumentException("member must not be null");
        }
        this.expense = expense;
        this.member = member;
        this.amount = MoneyAmount.requirePositiveInr(amount, "allocation amount");
    }

    public UUID getId() {
        return id;
    }

    public Expense getExpense() {
        return expense;
    }

    public GroupMembership getMember() {
        return member;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}

package com.paisavasool.app.domain.expense;

import java.util.List;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseAllocationRepository extends JpaRepository<ExpenseAllocation, UUID> {

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<ExpenseAllocation> findAllByExpense_IdOrderByIdAsc(UUID expenseId);

    @EntityGraph(attributePaths = {"member", "member.user"})
    List<ExpenseAllocation> findAllByExpense_IdInOrderByExpense_IdAsc(Collection<UUID> expenseIds);

    @Query("""
        select a.member.id as membershipId, a.amount as amount
        from ExpenseAllocation a
        where a.expense.group.id = :groupId
        """)
    List<MemberAmountProjection> findAllocationDebitsByGroupId(@Param("groupId") UUID groupId);
}

package com.paisavasool.app.domain.expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @EntityGraph(attributePaths = {"paidBy", "paidBy.user", "createdBy", "createdBy.user"})
    List<Expense> findAllByGroup_IdOrderByIncurredAtDesc(UUID groupId);

    @EntityGraph(attributePaths = {"group", "paidBy", "paidBy.user", "createdBy", "createdBy.user"})
    Optional<Expense> findWithDetailsByIdAndGroup_Id(UUID id, UUID groupId);

    @Query("""
        select e.paidBy.id as membershipId, e.totalAmount as amount
        from Expense e
        where e.group.id = :groupId
        """)
    List<MemberAmountProjection> findPayerCreditsByGroupId(@Param("groupId") UUID groupId);
}

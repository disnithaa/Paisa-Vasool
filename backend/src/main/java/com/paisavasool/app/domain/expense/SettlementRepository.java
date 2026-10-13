package com.paisavasool.app.domain.expense;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    @EntityGraph(attributePaths = {
        "fromMember", "fromMember.user",
        "toMember", "toMember.user",
        "createdBy", "createdBy.user"
    })
    List<Settlement> findAllByGroup_IdOrderBySettledAtDesc(UUID groupId);

    @Query("""
        select s.fromMember.id as fromMembershipId,
               s.toMember.id as toMembershipId,
               s.amount as amount
        from Settlement s
        where s.group.id = :groupId
        """)
    List<SettlementBalanceProjection> findBalanceEntriesByGroupId(@Param("groupId") UUID groupId);
}

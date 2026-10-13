package com.paisavasool.app.domain.purchase;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = "createdBy")
    List<Purchase> findAllByGroup_IdOrderByCreatedAtDesc(UUID groupId);

    Optional<Purchase> findByIdAndGroup_Id(UUID id, UUID groupId);

    @Query(
        value = "SELECT * FROM app.purchases WHERE id = :purchaseId AND group_id = :groupId FOR UPDATE",
        nativeQuery = true
    )
    Optional<Purchase> findByIdAndGroupIdForUpdate(
        @Param("purchaseId") UUID purchaseId,
        @Param("groupId") UUID groupId
    );
}

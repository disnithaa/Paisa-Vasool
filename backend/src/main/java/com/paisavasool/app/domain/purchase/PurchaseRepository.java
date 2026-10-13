package com.paisavasool.app.domain.purchase;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = "createdBy")
    List<Purchase> findAllByGroup_IdOrderByCreatedAtDesc(UUID groupId);
}

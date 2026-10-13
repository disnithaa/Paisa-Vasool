package com.paisavasool.app.domain.group;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query(value = "SELECT * FROM app.expense_groups WHERE id = :groupId FOR UPDATE", nativeQuery = true)
    java.util.Optional<Group> findByIdForUpdate(@Param("groupId") UUID groupId);
}

package com.paisavasool.app.domain.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

    boolean existsByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    boolean existsByGroup_IdAndUser_IdAndStatus(UUID groupId, UUID userId, MembershipStatus status);

    Optional<GroupMembership> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<GroupMembership> findAllByGroup_IdOrderByJoinedAtAsc(UUID groupId);
}

package com.paisavasool.app.api.dto;

import java.util.UUID;

import com.paisavasool.app.domain.group.GroupMembership;

public record LedgerMemberResponse(UUID membershipId, UUID userId, String name, String email) {

    public static LedgerMemberResponse from(GroupMembership membership) {
        return new LedgerMemberResponse(
            membership.getId(),
            membership.getUser().getId(),
            membership.getUser().getName(),
            membership.getUser().getEmail()
        );
    }
}

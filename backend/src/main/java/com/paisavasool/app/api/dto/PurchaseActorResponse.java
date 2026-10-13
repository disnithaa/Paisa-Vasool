package com.paisavasool.app.api.dto;

import java.util.UUID;

import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.user.User;

public record PurchaseActorResponse(UUID membershipId, UUID userId, String name, String email) {

    public static PurchaseActorResponse from(GroupMembership membership) {
        User user = membership.getUser();
        return new PurchaseActorResponse(
            membership.getId(),
            user.getId(),
            user.getName(),
            user.getEmail()
        );
    }
}

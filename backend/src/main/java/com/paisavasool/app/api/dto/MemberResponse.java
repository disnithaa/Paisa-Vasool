package com.paisavasool.app.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;

public record MemberResponse(
    UUID id,
    UUID userId,
    String name,
    String email,
    MembershipRole role,
    MembershipStatus status,
    Instant joinedAt
) {
}

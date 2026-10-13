package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
    UUID id,
    UUID groupId,
    LedgerMemberResponse fromMember,
    LedgerMemberResponse toMember,
    BigDecimal amount,
    LedgerMemberResponse createdBy,
    Instant settledAt,
    Instant createdAt
) {
}

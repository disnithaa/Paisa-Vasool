package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paisavasool.app.domain.purchase.PurchaseCompletionType;
import com.paisavasool.app.domain.purchase.PurchaseStatus;

public record PurchaseResponse(
    UUID id,
    UUID groupId,
    UUID createdById,
    String createdByEmail,
    String title,
    BigDecimal estimatedAmount,
    PurchaseStatus status,
    PurchaseActorResponse approvedBy,
    Instant approvedAt,
    PurchaseActorResponse cancelledBy,
    Instant cancelledAt,
    PurchaseActorResponse completedBy,
    Instant completedAt,
    PurchaseCompletionType completionType,
    Instant createdAt
) {
}

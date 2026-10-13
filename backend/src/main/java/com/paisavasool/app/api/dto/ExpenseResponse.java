package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
    UUID id,
    UUID groupId,
    UUID purchaseId,
    String description,
    BigDecimal totalAmount,
    LedgerMemberResponse paidBy,
    LedgerMemberResponse createdBy,
    Instant incurredAt,
    Instant createdAt,
    List<ExpenseAllocationResponse> allocations
) {
}

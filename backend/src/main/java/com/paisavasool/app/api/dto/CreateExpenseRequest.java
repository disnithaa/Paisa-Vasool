package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateExpenseRequest(
    @NotBlank @Size(max = 200) String description,
    @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal totalAmount,
    @NotNull UUID paidByMembershipId,
    UUID purchaseId,
    Instant incurredAt,
    @NotEmpty @Valid List<ExpenseAllocationRequest> allocations
) {
}

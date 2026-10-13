package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExpenseAllocationRequest(
    @NotNull UUID membershipId,
    @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount
) {
}

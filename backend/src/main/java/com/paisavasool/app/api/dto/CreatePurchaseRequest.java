package com.paisavasool.app.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePurchaseRequest(
    @NotBlank @Size(max = 200) String title,
    @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal estimatedAmount
) {
}

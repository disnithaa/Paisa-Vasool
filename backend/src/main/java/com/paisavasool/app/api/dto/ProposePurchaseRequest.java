package com.paisavasool.app.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ProposePurchaseRequest(@NotNull UUID offerId) {
}

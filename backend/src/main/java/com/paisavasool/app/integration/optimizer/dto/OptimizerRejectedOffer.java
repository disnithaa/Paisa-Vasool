package com.paisavasool.app.integration.optimizer.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptimizerRejectedOffer(
    @JsonProperty("offer_id") UUID offerId,
    @JsonProperty("reason") String reason
) {
}

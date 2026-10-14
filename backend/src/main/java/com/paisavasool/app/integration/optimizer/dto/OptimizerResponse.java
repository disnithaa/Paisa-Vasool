package com.paisavasool.app.integration.optimizer.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptimizerResponse(
    @JsonProperty("status") String status,
    @JsonProperty("currency") String currency,
    @JsonProperty("base_amount") String baseAmount,
    @JsonProperty("selected_offer_id") UUID selectedOfferId,
    @JsonProperty("discount_amount") String discountAmount,
    @JsonProperty("final_payable_amount") String finalPayableAmount,
    @JsonProperty("savings_amount") String savingsAmount,
    @JsonProperty("explanation") String explanation,
    @JsonProperty("rejected_offers") List<OptimizerRejectedOffer> rejectedOffers,
    @JsonProperty("solver") OptimizerSolverMetadata solver
) {
}

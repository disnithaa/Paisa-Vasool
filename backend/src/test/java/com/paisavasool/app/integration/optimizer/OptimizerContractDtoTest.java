package com.paisavasool.app.integration.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.integration.optimizer.dto.OptimizerOfferCandidate;
import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;

import org.junit.jupiter.api.Test;

class OptimizerContractDtoTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesRequestUsingPythonFieldNamesAndLosslessDecimalStrings() throws Exception {
        OptimizerOfferCandidate offer = OptimizerOfferCandidate.from(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            OfferType.PERCENTAGE_DISCOUNT,
            true,
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-11-01T00:00:00Z"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            new BigDecimal("10.00"),
            null
        );
        OptimizerRequest request = new OptimizerRequest(
            OptimizerRequest.decimalString(new BigDecimal("250.00")),
            "INR",
            OptimizerRequest.instantString(Instant.parse("2026-10-14T00:00:00Z")),
            List.of(offer)
        );

        JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(request));

        assertThat(json.has("purchase_amount")).isTrue();
        assertThat(json.has("purchaseAmount")).isFalse();
        assertThat(json.get("purchase_amount").asText()).isEqualTo("250.00");
        assertThat(json.get("evaluated_at").asText()).isEqualTo("2026-10-14T00:00:00Z");
        assertThat(json.get("offers").get(0).get("minimum_spend").asText()).isEqualTo("100.00");
        assertThat(json.get("offers").get(0).get("discount_percentage").asText()).isEqualTo("10.00");
        assertThat(json.get("offers").get(0).get("discount_amount").isNull()).isTrue();
    }

    @Test
    void deserializesPythonResponseAndDecimalStringsWithoutNumericCoercion() throws Exception {
        String responseJson = """
            {
              "status": "OPTIMAL",
              "currency": "INR",
              "base_amount": "250.00",
              "selected_offer_id": "00000000-0000-0000-0000-000000000001",
              "discount_amount": "25.00",
              "final_payable_amount": "225.00",
              "savings_amount": "25.00",
              "explanation": "Selected the best eligible offer.",
              "rejected_offers": [],
              "solver": {
                "algorithm": "ortools-cp-sat",
                "solver_status": "OPTIMAL",
                "baseline_agrees": true
              }
            }
            """;

        OptimizerResponse response = OBJECT_MAPPER.readValue(responseJson, OptimizerResponse.class);

        assertThat(response.status()).isEqualTo("OPTIMAL");
        assertThat(response.baseAmount()).isEqualTo("250.00");
        assertThat(response.selectedOfferId()).isEqualTo(
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
        assertThat(response.discountAmount()).isEqualTo("25.00");
        assertThat(response.solver().baselineAgrees()).isTrue();
    }

    @Test
    void decimalStringPreservesTrailingZeroesAndRejectsNull() {
        assertThat(OptimizerRequest.decimalString(new BigDecimal("25.50"))).isEqualTo("25.50");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> OptimizerRequest.decimalString(null));
    }

    @Test
    void optimizerPropertiesRequireAnHttpUrlAndPositiveTimeouts() {
        OptimizerProperties properties = new OptimizerProperties(
            URI.create("http://localhost:8000"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3)
        );

        assertThat(properties.baseUrl()).hasToString("http://localhost:8000");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));

        assertThatIllegalArgumentException().isThrownBy(() -> new OptimizerProperties(
            URI.create("file:///tmp/optimizer"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3)
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new OptimizerProperties(
            URI.create("http://localhost:8000"),
            Duration.ZERO,
            Duration.ofSeconds(3)
        ));
    }
}

package com.paisavasool.app.integration.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.integration.optimizer.dto.OptimizerOfferCandidate;
import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;

class RestClientOptimizerClientTest {

    private static final String BASE_URL = "http://optimizer.test";
    private static final UUID OFFER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final String RESPONSE_JSON = """
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

    private MockRestServiceServer server;
    private RestClientOptimizerClient optimizerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        optimizerClient = new RestClientOptimizerClient(builder.build());
    }

    @Test
    void postsTheExistingPythonContractAndReadsItsResponse() {
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "purchase_amount": "250.00",
                  "currency": "INR",
                  "evaluated_at": "2026-10-14T00:00:00Z",
                  "offers": [{
                    "offer_id": "00000000-0000-0000-0000-000000000001",
                    "offer_type": "PERCENTAGE_DISCOUNT",
                    "active": true,
                    "valid_from": "2026-10-01T00:00:00Z",
                    "valid_until": "2026-11-01T00:00:00Z",
                    "minimum_spend": "100.00",
                    "maximum_discount": "50.00",
                    "discount_percentage": "10.00",
                    "discount_amount": null
                  }]
                }
                """))
            .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        OptimizerResponse response = optimizerClient.optimize(request());

        assertThat(response.status()).isEqualTo("OPTIMAL");
        assertThat(response.baseAmount()).isEqualTo("250.00");
        assertThat(response.selectedOfferId()).isEqualTo(OFFER_ID);
        assertThat(response.finalPayableAmount()).isEqualTo("225.00");
        server.verify();
    }

    @Test
    void mapsClientAndServerHttpErrorsToOptimizerUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withBadRequest());
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> optimizerClient.optimize(request()))
                .isInstanceOf(OptimizerUnavailableException.class)
                .hasMessageContaining("HTTP");
        }
        server.verify();
    }

    @Test
    void mapsMalformedJsonToOptimizerUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andRespond(withSuccess("{ malformed", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> optimizerClient.optimize(request()))
            .isInstanceOf(OptimizerUnavailableException.class)
            .hasMessageContaining("invalid response");
        server.verify();
    }

    @Test
    void mapsEmptySuccessfulResponseToOptimizerUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andRespond(withSuccess());

        assertThatThrownBy(() -> optimizerClient.optimize(request()))
            .isInstanceOf(OptimizerUnavailableException.class)
            .hasMessageContaining("empty response");
        server.verify();
    }

    @Test
    void mapsConnectionFailureToOptimizerUnavailableWithoutRetrying() {
        server.expect(requestTo(BASE_URL + "/api/v1/optimize"))
            .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> optimizerClient.optimize(request()))
            .isInstanceOf(OptimizerUnavailableException.class)
            .hasMessageContaining("could not be reached");
        server.verify();
    }

    private static OptimizerRequest request() {
        OptimizerOfferCandidate offer = OptimizerOfferCandidate.from(
            OFFER_ID,
            OfferType.PERCENTAGE_DISCOUNT,
            true,
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-11-01T00:00:00Z"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            new BigDecimal("10.00"),
            null
        );
        return new OptimizerRequest(
            "250.00",
            "INR",
            OptimizerRequest.instantString(Instant.parse("2026-10-14T00:00:00Z")),
            List.of(offer)
        );
    }
}

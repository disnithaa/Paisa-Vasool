package com.paisavasool.app.integration.optimizer;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;

public class RestClientOptimizerClient implements OptimizerClient {

    private static final String OPTIMIZE_PATH = "/api/v1/optimize";

    private final RestClient restClient;

    public RestClientOptimizerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OptimizerResponse optimize(OptimizerRequest request) {
        try {
            OptimizerResponse response = restClient.post()
                .uri(OPTIMIZE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OptimizerResponse.class);
            if (response == null) {
                throw new OptimizerUnavailableException(
                    "Optimization service returned an empty response"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new OptimizerUnavailableException(
                "Optimization service returned HTTP " + exception.getStatusCode().value(),
                exception
            );
        } catch (ResourceAccessException exception) {
            throw new OptimizerUnavailableException(
                "Optimization service could not be reached",
                exception
            );
        } catch (RestClientException exception) {
            throw new OptimizerUnavailableException(
                "Optimization service returned an invalid response",
                exception
            );
        }
    }
}

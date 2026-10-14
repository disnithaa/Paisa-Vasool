package com.paisavasool.app.integration.optimizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptimizerSolverMetadata(
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("solver_status") String solverStatus,
    @JsonProperty("baseline_agrees") Boolean baselineAgrees
) {
}

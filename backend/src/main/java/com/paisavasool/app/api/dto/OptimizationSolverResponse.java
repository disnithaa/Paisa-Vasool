package com.paisavasool.app.api.dto;

public record OptimizationSolverResponse(
    String algorithm,
    String solverStatus,
    boolean baselineAgrees
) {
}

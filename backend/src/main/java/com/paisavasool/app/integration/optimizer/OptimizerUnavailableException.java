package com.paisavasool.app.integration.optimizer;

public class OptimizerUnavailableException extends RuntimeException {

    public OptimizerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public OptimizerUnavailableException(String message) {
        super(message);
    }
}

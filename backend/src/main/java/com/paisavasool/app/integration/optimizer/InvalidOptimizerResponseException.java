package com.paisavasool.app.integration.optimizer;

public class InvalidOptimizerResponseException extends RuntimeException {

    public InvalidOptimizerResponseException(String message) {
        super(message);
    }

    public InvalidOptimizerResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}

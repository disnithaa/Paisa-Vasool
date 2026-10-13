package com.paisavasool.app.domain.money;

import java.math.BigDecimal;

public final class MoneyAmount {

    private static final int MAX_INTEGER_DIGITS = 10;
    private static final int MAX_SCALE = 2;

    private MoneyAmount() {
    }

    public static BigDecimal requirePositiveInr(BigDecimal amount, String fieldName) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }

        int integerDigits = amount.precision() - amount.scale();
        if (amount.scale() > MAX_SCALE || integerDigits > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(fieldName + " must fit NUMERIC(12, 2)");
        }

        return amount;
    }
}

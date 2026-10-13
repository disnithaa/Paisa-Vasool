package com.paisavasool.app.api.dto;

import java.math.BigDecimal;

public record ExpenseAllocationResponse(LedgerMemberResponse member, BigDecimal amount) {
}

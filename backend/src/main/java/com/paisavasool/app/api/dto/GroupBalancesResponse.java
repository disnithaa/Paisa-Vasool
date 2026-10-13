package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupBalancesResponse(
    UUID groupId,
    String currency,
    String balanceConvention,
    BigDecimal totalBalance,
    List<MemberBalanceResponse> members
) {
}

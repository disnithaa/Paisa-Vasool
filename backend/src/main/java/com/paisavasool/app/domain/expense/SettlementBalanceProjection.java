package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.util.UUID;

public interface SettlementBalanceProjection {

    UUID getFromMembershipId();

    UUID getToMembershipId();

    BigDecimal getAmount();
}

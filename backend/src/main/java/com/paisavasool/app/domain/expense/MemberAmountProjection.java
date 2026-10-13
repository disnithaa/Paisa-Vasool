package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.util.UUID;

public interface MemberAmountProjection {

    UUID getMembershipId();

    BigDecimal getAmount();
}

package com.paisavasool.app.api.dto;

import java.math.BigDecimal;

import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.MembershipStatus;

public record MemberBalanceResponse(
    LedgerMemberResponse member,
    MembershipStatus membershipStatus,
    BigDecimal balance
) {

    public static MemberBalanceResponse from(GroupMembership membership, BigDecimal balance) {
        return new MemberBalanceResponse(
            LedgerMemberResponse.from(membership),
            membership.getStatus(),
            balance
        );
    }
}

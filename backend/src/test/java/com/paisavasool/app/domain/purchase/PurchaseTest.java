package com.paisavasool.app.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferEvaluation;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.domain.user.User;

class PurchaseTest {

    private final Group group = new Group("Trip");
    private final User user = new User("Asha", "asha@example.com");

    @Test
    void startsAsDraftAndAllowsValidTransitions() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));
        GroupMembership member = activeMember();

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.DRAFT);

        purchase.proposeOffer(
            fixedOffer(),
            OfferEvaluation.eligible(new BigDecimal("50.00"), new BigDecimal("1200.00")),
            Instant.parse("2026-06-01T00:00:00Z")
        );
        purchase.approve(member, Instant.parse("2026-06-01T00:01:00Z"));
        purchase.completeSimulated(member, Instant.parse("2026-06-01T00:02:00Z"));

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
        assertThat(purchase.getCompletionType()).isEqualTo(PurchaseCompletionType.SIMULATED);
        assertThat(purchase.getCompletedBy()).isSameAs(member);
    }

    @Test
    void rejectsInvalidTransitions() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));

        assertThatIllegalStateException()
            .isThrownBy(() -> purchase.approve(activeMember(), Instant.now()));

        purchase.cancel(activeMember(), Instant.now());
        assertThatIllegalStateException()
            .isThrownBy(() -> purchase.completeSimulated(activeMember(), Instant.now()));
    }

    @Test
    void rejectsInvalidEstimatedAmounts() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Purchase(group, user, "No cost", BigDecimal.ZERO));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Purchase(group, user, "Negative", new BigDecimal("-1.00")));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Purchase(group, user, "Too precise", new BigDecimal("1.001")));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Purchase(group, user, "Too large", new BigDecimal("10000000000.00")));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new Purchase(group, user, "Exponent too large", new BigDecimal("1E+12")));
    }

    @Test
    void cannotSubmitForApprovalWithoutAnOfferProposal() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));

        assertThatIllegalStateException()
            .isThrownBy(() -> purchase.approve(activeMember(), Instant.now()));
    }

    @Test
    void actionsRequireAnActiveMemberOfThePurchaseGroup() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));
        Group anotherGroup = new Group("Other group");
        GroupMembership outsider = new GroupMembership(
            user, anotherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        );

        assertThatIllegalArgumentException()
            .isThrownBy(() -> purchase.cancel(outsider, Instant.now()));

        GroupMembership formerMember = new GroupMembership(
            user, group, MembershipRole.MEMBER, MembershipStatus.LEFT
        );
        assertThatIllegalArgumentException()
            .isThrownBy(() -> purchase.cancel(formerMember, Instant.now()));
    }

    private static Offer fixedOffer() {
        return new Offer(
            "Sample Merchant",
            "Sample fixed discount",
            "Fictional test offer",
            OfferType.FIXED_DISCOUNT,
            null,
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2027-01-01T00:00:00Z"),
            true,
            true,
            "Fictional test terms"
        );
    }

    private GroupMembership activeMember() {
        return new GroupMembership(user, group, MembershipRole.OWNER, MembershipStatus.ACTIVE);
    }
}

package com.paisavasool.app.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.user.User;

class PurchaseTest {

    private final Group group = new Group("Trip");
    private final User user = new User("Asha", "asha@example.com");

    @Test
    void startsAsDraftAndAllowsValidTransitions() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.DRAFT);

        purchase.transitionTo(PurchaseStatus.PENDING_APPROVAL);
        purchase.transitionTo(PurchaseStatus.APPROVED);
        purchase.transitionTo(PurchaseStatus.COMPLETED);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.COMPLETED);
    }

    @Test
    void rejectsInvalidTransitions() {
        Purchase purchase = new Purchase(group, user, "Train tickets", new BigDecimal("1250.00"));

        assertThatIllegalStateException()
            .isThrownBy(() -> purchase.transitionTo(PurchaseStatus.APPROVED));

        purchase.transitionTo(PurchaseStatus.CANCELLED);
        assertThatIllegalStateException()
            .isThrownBy(() -> purchase.transitionTo(PurchaseStatus.COMPLETED));
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
}

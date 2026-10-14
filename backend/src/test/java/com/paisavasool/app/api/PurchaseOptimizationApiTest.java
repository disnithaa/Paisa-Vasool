package com.paisavasool.app.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferRepository;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.purchase.PurchaseStatus;
import com.paisavasool.app.domain.user.User;
import com.paisavasool.app.domain.user.UserRepository;
import com.paisavasool.app.integration.optimizer.OptimizerClient;
import com.paisavasool.app.integration.optimizer.OptimizerUnavailableException;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;
import com.paisavasool.app.integration.optimizer.dto.OptimizerSolverMetadata;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:purchase-optimization-api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.dev-auth.enabled=true",
    "app.dev-auth.email=dev@paisavasool.local",
    "app.dev-auth.name=Local Developer",
    "app.dev-auth.password=test-secret",
    "app.optimizer.base-url=http://localhost:8000",
    "app.optimizer.connect-timeout=1s",
    "app.optimizer.read-timeout=3s"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@Transactional
class PurchaseOptimizationApiTest {

    private static final String DEV_EMAIL = "dev@paisavasool.local";
    private static final String DEV_PASSWORD = "test-secret";
    private static final BigDecimal PURCHASE_AMOUNT = new BigDecimal("250.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipRepository membershipRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private OfferRepository offerRepository;

    @MockBean
    private OptimizerClient optimizerClient;

    private User developer;
    private Group group;
    private GroupMembership developerMembership;
    private Purchase purchase;
    private Offer eligibleOffer;

    @BeforeEach
    void setUp() {
        developer = userRepository.findByEmail(DEV_EMAIL).orElseThrow();
        group = groupRepository.saveAndFlush(new Group("Optimizer test group"));
        developerMembership = membershipRepository.saveAndFlush(new GroupMembership(
            developer, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));
        purchase = purchaseRepository.saveAndFlush(
            new Purchase(group, developer, "Shared purchase", PURCHASE_AMOUNT)
        );

        offerRepository.findAll().forEach(Offer::deactivate);
        offerRepository.flush();
        Instant now = Instant.now();
        eligibleOffer = offerRepository.saveAndFlush(new Offer(
            "Fictional merchant",
            "Sample percentage offer",
            "A fictional test offer.",
            OfferType.PERCENTAGE_DISCOUNT,
            new BigDecimal("10.00"),
            null,
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            now.minusSeconds(60),
            now.plusSeconds(3600),
            true,
            true,
            "Test only."
        ));
    }

    @Test
    void activeGroupMemberReceivesValidatedRecommendationWithoutChangingPurchaseState() throws Exception {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "INR",
            "250.00",
            eligibleOffer.getId(),
            "25.00",
            "225.00",
            "25.00"
        ));

        mockMvc.perform(post(path())
                .with(httpBasic(DEV_EMAIL, DEV_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"createdById":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchaseId").value(purchase.getId().toString()))
            .andExpect(jsonPath("$.selectedOfferId").value(eligibleOffer.getId().toString()))
            .andExpect(jsonPath("$.selectedOffer.title").value("Sample percentage offer"))
            .andExpect(jsonPath("$.discountAmount").value(25.00))
            .andExpect(jsonPath("$.finalPayableAmount").value(225.00))
            .andExpect(jsonPath("$.savingsAmount").value(25.00))
            .andExpect(jsonPath("$.solver.baselineAgrees").value(true));

        verify(optimizerClient).optimize(any());
        purchaseRepository.flush();
        Purchase unchangedPurchase = purchaseRepository.findById(purchase.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchangedPurchase.getStatus())
            .isEqualTo(PurchaseStatus.DRAFT);
        org.assertj.core.api.Assertions.assertThat(unchangedPurchase.getProposedOffer()).isNull();
        org.assertj.core.api.Assertions.assertThat(unchangedPurchase.getOfferProposedAt()).isNull();
    }

    @Test
    void nonMemberAndInactiveMemberCannotCallOptimizer() throws Exception {
        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group otherGroup = groupRepository.saveAndFlush(new Group("Other group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, otherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));
        Purchase otherPurchase = purchaseRepository.saveAndFlush(
            new Purchase(otherGroup, otherUser, "Private purchase", PURCHASE_AMOUNT)
        );

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/optimization",
                otherGroup.getId(), otherPurchase.getId())
                .with(httpBasic(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isForbidden());

        developerMembership.leave();
        membershipRepository.flush();
        mockMvc.perform(post(path()).with(httpBasic(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isForbidden());

        verifyNoInteractions(optimizerClient);
    }

    @Test
    void nonDraftPurchaseIsRejectedBeforeCallingOptimizer() throws Exception {
        purchase.cancel(developerMembership, Instant.now());
        purchaseRepository.flush();

        mockMvc.perform(post(path()).with(httpBasic(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Only draft purchases can be optimized"));

        verifyNoInteractions(optimizerClient);
    }

    @Test
    void unavailableOptimizerReturnsProblemDetailsAndDoesNotChangePurchase() throws Exception {
        doThrow(new OptimizerUnavailableException("Optimization service could not be reached"))
            .when(optimizerClient)
            .optimize(any());

        mockMvc.perform(post(path()).with(httpBasic(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Service Unavailable"))
            .andExpect(jsonPath("$.detail").value(
                "A valid optimization recommendation is temporarily unavailable"
            ));

        org.assertj.core.api.Assertions.assertThat(purchase.getStatus())
            .isEqualTo(PurchaseStatus.DRAFT);
        verify(optimizerClient).optimize(any());
    }

    @Test
    void optimizerResponseWithUnknownOfferIsRejectedAsUnavailable() throws Exception {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "INR",
            "250.00",
            UUID.randomUUID(),
            "25.00",
            "225.00",
            "25.00"
        ));

        mockMvc.perform(post(path()).with(httpBasic(DEV_EMAIL, DEV_PASSWORD)))
            .andExpect(status().isServiceUnavailable());

        org.assertj.core.api.Assertions.assertThat(purchase.getStatus())
            .isEqualTo(PurchaseStatus.DRAFT);
    }

    @Test
    void unauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(post(path()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(optimizerClient);
    }

    private String path() {
        return "/api/groups/" + group.getId() + "/purchases/" + purchase.getId() + "/optimization";
    }

    private static OptimizerResponse response(
        String currency,
        String baseAmount,
        UUID selectedOfferId,
        String discountAmount,
        String finalPayableAmount,
        String savingsAmount
    ) {
        return new OptimizerResponse(
            "OPTIMAL",
            currency,
            baseAmount,
            selectedOfferId,
            discountAmount,
            finalPayableAmount,
            savingsAmount,
            "Selected the lowest cost eligible offer.",
            List.of(),
            new OptimizerSolverMetadata("ortools-cp-sat", "OPTIMAL", true)
        );
    }
}

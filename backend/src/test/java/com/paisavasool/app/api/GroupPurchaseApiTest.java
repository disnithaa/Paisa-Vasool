package com.paisavasool.app.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.expense.ExpenseAllocationRepository;
import com.paisavasool.app.domain.expense.ExpenseRepository;
import com.paisavasool.app.domain.expense.SettlementRepository;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferRepository;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.user.User;
import com.paisavasool.app.domain.user.UserRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.dev-auth.enabled=true",
    "app.dev-auth.email=dev@paisavasool.local",
    "app.dev-auth.name=Local Developer",
    "app.dev-auth.password=test-secret"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@Transactional
class GroupPurchaseApiTest {

    private static final String DEV_EMAIL = "dev@paisavasool.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipRepository membershipRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseAllocationRepository allocationRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void groupCanBeCreatedAndRetrievedByItsOwner() throws Exception {
        String body = mockMvc.perform(post("/api/groups")
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Weekend trip"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Weekend trip"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String groupId = jsonValue(body, "id");

        mockMvc.perform(get("/api/groups/{groupId}", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(groupId))
            .andExpect(jsonPath("$.name").value("Weekend trip"));

        mockMvc.perform(get("/api/groups/{groupId}/members", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value(DEV_EMAIL))
            .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void groupListContainsOnlyGroupsWithCurrentUsersActiveMembership() throws Exception {
        String activeGroupId = createGroup("Active group");

        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group otherGroup = groupRepository.saveAndFlush(new Group("Other user's group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, otherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        Group leftGroup = groupRepository.saveAndFlush(new Group("Former membership"));
        User currentUser = userRepository.findByEmail(DEV_EMAIL).orElseThrow();
        GroupMembership formerMembership = membershipRepository.saveAndFlush(new GroupMembership(
            currentUser, leftGroup, MembershipRole.MEMBER, MembershipStatus.ACTIVE
        ));
        formerMembership.leave();
        membershipRepository.flush();

        mockMvc.perform(get("/api/groups").with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(activeGroupId))
            .andExpect(jsonPath("$[0].name").value("Active group"));
    }

    @Test
    void groupListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/groups"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void groupCreationRejectsInvalidName() throws Exception {
        mockMvc.perform(post("/api/groups")
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":" "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations.name").exists());
    }

    @Test
    void memberCanBeAddedAndDuplicateMembershipIsRejected() throws Exception {
        String groupId = createGroup("Team");
        String memberRequest = """
            {"name":"Mira","email":"mira@example.com"}
            """;

        mockMvc.perform(post("/api/groups/{groupId}/members", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberRequest))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Mira"))
            .andExpect(jsonPath("$.role").value("MEMBER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/groups/{groupId}/members", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(memberRequest))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("already has a membership")));

        mockMvc.perform(get("/api/groups/{groupId}/members", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void addingMemberToUnknownGroupReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/members", UUID.randomUUID())
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Mira","email":"mira@example.com"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void purchaseCanBeCreatedByActiveMemberAndListedForGroup() throws Exception {
        String groupId = createGroup("Travel");

        mockMvc.perform(post("/api/groups/{groupId}/purchases", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Train tickets","estimatedAmount":1250.50,"createdById":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.groupId").value(groupId))
            .andExpect(jsonPath("$.createdByEmail").value(DEV_EMAIL))
            .andExpect(jsonPath("$.createdById").value(userRepository.findByEmail(DEV_EMAIL).orElseThrow().getId().toString()))
            .andExpect(jsonPath("$.estimatedAmount").value(1250.50))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/groups/{groupId}/purchases", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Train tickets"));
    }

    @Test
    void purchaseCreationRejectsZeroAndNegativeAmounts() throws Exception {
        String groupId = createGroup("Travel");

        for (String amount : new String[] {"0", "-1.00"}) {
            mockMvc.perform(post("/api/groups/{groupId}/purchases", groupId)
                    .with(httpBasic(DEV_EMAIL, "test-secret"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"Invalid amount","estimatedAmount":%s}
                        """.formatted(amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.estimatedAmount").exists());
        }
    }

    @Test
    void purchaseCreationRejectsUnknownGroup() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/purchases", UUID.randomUUID())
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Train tickets","estimatedAmount":10.00}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void purchaseCreationRejectsNonMemberAndLeftMember() throws Exception {
        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group notMemberGroup = groupRepository.saveAndFlush(new Group("Not my group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, notMemberGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        Group leftGroup = groupRepository.saveAndFlush(new Group("Former group"));
        User devUser = userRepository.findByEmail(DEV_EMAIL).orElseThrow();
        membershipRepository.saveAndFlush(new GroupMembership(
            devUser, leftGroup, MembershipRole.MEMBER, MembershipStatus.LEFT
        ));

        for (Group group : new Group[] {notMemberGroup, leftGroup}) {
            mockMvc.perform(post("/api/groups/{groupId}/purchases", group.getId())
                    .with(httpBasic(DEV_EMAIL, "test-secret"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"Train tickets","estimatedAmount":10.00}
                        """))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void activeGroupMemberCanBrowseOfferCatalogAndDetails() throws Exception {
        String groupId = createGroup("Offer browsing");

        String response = mockMvc.perform(get("/api/groups/{groupId}/offers", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].sample").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String offerId = jsonValue(response, "[0].id");

        mockMvc.perform(get("/api/groups/{groupId}/offers/{offerId}", groupId, offerId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(offerId))
            .andExpect(jsonPath("$.terms").value(
                "Sample terms only. This fictional offer has no real-world value."
            ));
    }

    @Test
    void offerCatalogRejectsNonMembersAndUnknownOffers() throws Exception {
        String groupId = createGroup("Offer access");
        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group otherGroup = groupRepository.saveAndFlush(new Group("Other group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, otherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        mockMvc.perform(get("/api/groups/{groupId}/offers", otherGroup.getId())
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/groups/{groupId}/offers/{offerId}", groupId, UUID.randomUUID())
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isNotFound());
    }

    @Test
    void leftMemberCannotBrowseOfferCatalog() throws Exception {
        String groupId = createGroup("Former member");
        User devUser = userRepository.findByEmail(DEV_EMAIL).orElseThrow();
        membershipRepository.findByGroup_IdAndUser_Id(
            UUID.fromString(groupId),
            devUser.getId()
        ).orElseThrow().leave();
        membershipRepository.flush();

        mockMvc.perform(get("/api/groups/{groupId}/offers", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isForbidden());
    }

    @Test
    void eligibleOffersUsePurchaseAmountAndReturnExplicitRejectionReasons() throws Exception {
        String groupId = createGroup("Offer eligibility");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/eligible-offers",
                groupId,
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].offer.offerType").value("PERCENTAGE_DISCOUNT"))
            .andExpect(jsonPath("$[0].eligible").value(false))
            .andExpect(jsonPath("$[0].ineligibilityReason").value("MINIMUM_SPEND_NOT_MET"))
            .andExpect(jsonPath("$[1].offer.offerType").value("FIXED_DISCOUNT"))
            .andExpect(jsonPath("$[1].eligible").value(true))
            .andExpect(jsonPath("$[1].discountAmount").value(75.00))
            .andExpect(jsonPath("$[1].finalAmount").value(325.00));
    }

    @Test
    void offerEligibilityRequiresGroupMembershipAndDraftPurchase() throws Exception {
        String groupId = createGroup("Offer eligibility");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");
        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group otherGroup = groupRepository.saveAndFlush(new Group("Other group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, otherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/eligible-offers",
                otherGroup.getId(),
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/eligible-offers",
                groupId,
                UUID.randomUUID()
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isNotFound());

        Purchase purchase = purchaseRepository.findById(UUID.fromString(purchaseId)).orElseThrow();
        purchase.cancel(
            membershipRepository.findById(UUID.fromString(getMembershipId(groupId, DEV_EMAIL))).orElseThrow(),
            java.time.Instant.now()
        );
        purchaseRepository.flush();

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/eligible-offers",
                groupId,
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isConflict());
    }

    @Test
    void eligibleOfferCanBeProposedAndItsPriceTermsAreSnapshotted() throws Exception {
        String groupId = createGroup("Proposal");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");
        Offer selectedOffer = findSampleOffer(OfferType.FIXED_DISCOUNT);

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(selectedOffer.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.createdByEmail").value(DEV_EMAIL))
            .andExpect(jsonPath("$.offer.offerId").value(selectedOffer.getId().toString()))
            .andExpect(jsonPath("$.offer.title").value("Sample: 75 INR off"))
            .andExpect(jsonPath("$.offer.discountAmount").value(75.00))
            .andExpect(jsonPath("$.appliedDiscountAmount").value(75.00))
            .andExpect(jsonPath("$.expectedFinalAmount").value(325.00))
            .andReturn()
            .getResponse()
            .getContentAsString();

        jdbcTemplate.update(
            "UPDATE app.offers SET title = ?, discount_amount = ?, terms = ? WHERE id = ?",
            "Edited catalog title",
            new java.math.BigDecimal("200.00"),
            "Edited catalog terms",
            selectedOffer.getId()
        );

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.offer.title").value("Sample: 75 INR off"))
            .andExpect(jsonPath("$.offer.discountAmount").value(75.00))
            .andExpect(jsonPath("$.offer.terms").value(
                "Sample terms only. This fictional offer has no real-world value."
            ))
            .andExpect(jsonPath("$.appliedDiscountAmount").value(75.00))
            .andExpect(jsonPath("$.expectedFinalAmount").value(325.00));

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(selectedOffer.getId())))
            .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void proposalRejectsIneligibleOfferAndLeavesPurchaseDraft() throws Exception {
        String groupId = createGroup("Proposal");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");
        Offer percentageOffer = findSampleOffer(OfferType.PERCENTAGE_DISCOUNT);

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(percentageOffer.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", containsString("MINIMUM_SPEND_NOT_MET")));

        mockMvc.perform(get("/api/groups/{groupId}/purchases", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("DRAFT"));

        mockMvc.perform(get(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isConflict());
    }

    @Test
    void proposalRequiresActiveGroupMembershipAndReturnsNotFoundForUnknownOffer() throws Exception {
        String groupId = createGroup("Proposal");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");
        User otherUser = userRepository.saveAndFlush(new User("Other", "other@example.com"));
        Group otherGroup = groupRepository.saveAndFlush(new Group("Other group"));
        membershipRepository.saveAndFlush(new GroupMembership(
            otherUser, otherGroup, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                otherGroup.getId(),
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden());

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isNotFound());
    }

    @Test
    void activeMemberCanApproveAndCompletePurchaseAsSimulatedWithoutCreatingExpense() throws Exception {
        String groupId = createGroup("Simulated purchase");
        String purchaseId = createPurchase(groupId, "Dinner", "400.00");
        proposePurchase(groupId, purchaseId, findSampleOffer(OfferType.FIXED_DISCOUNT).getId());

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/approve", groupId, purchaseId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.approvedBy.email").value(DEV_EMAIL))
            .andExpect(jsonPath("$.approvedAt").exists());

        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/complete-simulated",
                groupId,
                purchaseId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completionType").value("SIMULATED"))
            .andExpect(jsonPath("$.completedBy.email").value(DEV_EMAIL))
            .andExpect(jsonPath("$.completedAt").exists());

        org.assertj.core.api.Assertions.assertThat(expenseRepository.count()).isZero();
        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBalance").value(0.00));
    }

    @Test
    void activeMemberCanCancelDraftOrPendingProposalButCannotRepeatTerminalActions() throws Exception {
        String groupId = createGroup("Cancellation");
        String draftId = createPurchase(groupId, "Unsubmitted purchase", "20.00");

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/cancel", groupId, draftId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy.email").value(DEV_EMAIL))
            .andExpect(jsonPath("$.cancelledAt").exists());

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/cancel", groupId, draftId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isConflict());

        String pendingId = createPurchase(groupId, "Pending purchase", "400.00");
        proposePurchase(groupId, pendingId, findSampleOffer(OfferType.FIXED_DISCOUNT).getId());
        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/cancel", groupId, pendingId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy.email").value(DEV_EMAIL));
    }

    @Test
    void purchaseActionsRejectInvalidTransitionsAndNonMembers() throws Exception {
        String groupId = createGroup("Purchase action authorization");
        String draftId = createPurchase(groupId, "Draft purchase", "400.00");

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/approve", groupId, draftId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isConflict());
        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/complete-simulated",
                groupId,
                draftId
            ).with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isConflict());

        String purchaseId = createPurchase(groupId, "Pending purchase", "400.00");
        proposePurchase(groupId, purchaseId, findSampleOffer(OfferType.FIXED_DISCOUNT).getId());
        User devUser = userRepository.findByEmail(DEV_EMAIL).orElseThrow();
        membershipRepository.findByGroup_IdAndUser_Id(UUID.fromString(groupId), devUser.getId())
            .orElseThrow()
            .leave();
        membershipRepository.flush();

        mockMvc.perform(post("/api/groups/{groupId}/purchases/{purchaseId}/approve", groupId, purchaseId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isForbidden());
    }

    @Test
    void apiRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"No identity"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void activeMemberCanRecordExpenseWithExplicitAllocations() throws Exception {
        String groupId = createGroup("Dinner");
        String payerMembershipId = getMembershipId(groupId, DEV_EMAIL);
        String memberMembershipId = addMember(groupId, "Mira", "mira@example.com");

        String response = mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Shared dinner",
                      "totalAmount":25.50,
                      "paidByMembershipId":"%s",
                      "allocations":[
                        {"membershipId":"%s","amount":10.00},
                        {"membershipId":"%s","amount":15.50}
                      ]
                    }
                    """.formatted(payerMembershipId, payerMembershipId, memberMembershipId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").value("Shared dinner"))
            .andExpect(jsonPath("$.totalAmount").value(25.50))
            .andExpect(jsonPath("$.paidBy.membershipId").value(payerMembershipId))
            .andExpect(jsonPath("$.createdBy.email").value(DEV_EMAIL))
            .andExpect(jsonPath("$.allocations.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String expenseId = jsonValue(response, "id");
        mockMvc.perform(get("/api/groups/{groupId}/expenses/{expenseId}", groupId, expenseId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allocations.length()").value(2));

        mockMvc.perform(get("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void expenseRejectsInvalidAmountAndMismatchedAllocationsWithoutPartialPersistence() throws Exception {
        String groupId = createGroup("Dinner");
        String payerMembershipId = getMembershipId(groupId, DEV_EMAIL);

        long expenseCountBefore = expenseRepository.count();
        long allocationCountBefore = allocationRepository.count();
        for (String amount : new String[] {"0", "-1.00"}) {
            mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                    .with(httpBasic(DEV_EMAIL, "test-secret"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "description":"Invalid amount",
                          "totalAmount":%s,
                          "paidByMembershipId":"%s",
                          "allocations":[{"membershipId":"%s","amount":%s}]
                        }
                        """.formatted(amount, payerMembershipId, payerMembershipId, amount)))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Does not balance",
                      "totalAmount":10.00,
                      "paidByMembershipId":"%s",
                      "allocations":[{"membershipId":"%s","amount":9.99}]
                    }
                    """.formatted(payerMembershipId, payerMembershipId)))
            .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(expenseRepository.count()).isEqualTo(expenseCountBefore);
        org.assertj.core.api.Assertions.assertThat(allocationRepository.count()).isEqualTo(allocationCountBefore);
    }

    @Test
    void expenseRejectsDuplicateAllocationsAndMemberFromAnotherGroup() throws Exception {
        String groupId = createGroup("Dinner");
        String payerMembershipId = getMembershipId(groupId, DEV_EMAIL);
        String otherGroupId = createGroup("Other");
        String otherMembershipId = getMembershipId(otherGroupId, DEV_EMAIL);

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Duplicate shares",
                      "totalAmount":10.00,
                      "paidByMembershipId":"%s",
                      "allocations":[
                        {"membershipId":"%s","amount":5.00},
                        {"membershipId":"%s","amount":5.00}
                      ]
                    }
                    """.formatted(payerMembershipId, payerMembershipId, payerMembershipId)))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Wrong group member",
                      "totalAmount":10.00,
                      "paidByMembershipId":"%s",
                      "allocations":[{"membershipId":"%s","amount":10.00}]
                    }
                    """.formatted(payerMembershipId, otherMembershipId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void expenseRejectsLeftMemberAndUnknownGroup() throws Exception {
        String groupId = createGroup("Dinner");
        String payerMembershipId = getMembershipId(groupId, DEV_EMAIL);
        String leftMembershipId = addMember(groupId, "Mira", "mira@example.com");
        membershipRepository.findById(UUID.fromString(leftMembershipId)).orElseThrow().leave();
        membershipRepository.flush();

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Left member allocation",
                      "totalAmount":10.00,
                      "paidByMembershipId":"%s",
                      "allocations":[{"membershipId":"%s","amount":10.00}]
                    }
                    """.formatted(payerMembershipId, leftMembershipId)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/groups/{groupId}/expenses", UUID.randomUUID())
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"Unknown group",
                      "totalAmount":10.00,
                      "paidByMembershipId":"%s",
                      "allocations":[{"membershipId":"%s","amount":10.00}]
                    }
                    """.formatted(payerMembershipId, payerMembershipId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void balancesCreditPayerDebitAllocationsAndSumToZero() throws Exception {
        String groupId = createGroup("Dinner");
        String payerId = getMembershipId(groupId, DEV_EMAIL);
        String memberId = addMember(groupId, "Mira", "mira@example.com");

        recordExpense(groupId, "Shared dinner", "25.50", payerId,
            """
                {"membershipId":"%s","amount":10.00},
                {"membershipId":"%s","amount":15.50}
                """.formatted(payerId, memberId));

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.balanceConvention").value("Positive means receivable; negative means owed"))
            .andExpect(jsonPath("$.totalBalance").value(0.00))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].balance").value(15.50))
            .andExpect(jsonPath("$.members[1].balance").value(-15.50));
    }

    @Test
    void balancesCombineMultipleExpensesAndPreserveZeroSum() throws Exception {
        String groupId = createGroup("Trip");
        String firstId = getMembershipId(groupId, DEV_EMAIL);
        String secondId = addMember(groupId, "Mira", "mira@example.com");
        String thirdId = addMember(groupId, "Dev", "dev@example.com");

        recordExpense(groupId, "Lunch", "30.00", firstId, """
            {"membershipId":"%s","amount":10.00},
            {"membershipId":"%s","amount":10.00},
            {"membershipId":"%s","amount":10.00}
            """.formatted(firstId, secondId, thirdId));
        recordExpense(groupId, "Taxi", "6.00", secondId, """
            {"membershipId":"%s","amount":2.00},
            {"membershipId":"%s","amount":2.00},
            {"membershipId":"%s","amount":2.00}
            """.formatted(firstId, secondId, thirdId));

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBalance").value(0.00))
            .andExpect(jsonPath("$.members.length()").value(3))
            .andExpect(jsonPath("$.members[0].balance").value(18.00))
            .andExpect(jsonPath("$.members[1].balance").value(-6.00))
            .andExpect(jsonPath("$.members[2].balance").value(-12.00));
    }

    @Test
    void settlementIsRecordedAndReducesSenderDebtAndRecipientReceivable() throws Exception {
        String groupId = createGroup("Settlement");
        String receiverId = getMembershipId(groupId, DEV_EMAIL);
        String senderId = addMember(groupId, "Mira", "mira@example.com");
        recordExpense(groupId, "Shared bill", "20.00", receiverId,
            """
                {"membershipId":"%s","amount":20.00}
                """.formatted(senderId));

        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fromMembershipId":"%s",
                      "toMembershipId":"%s",
                      "amount":7.25
                    }
                    """.formatted(senderId, receiverId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fromMember.membershipId").value(senderId))
            .andExpect(jsonPath("$.toMember.membershipId").value(receiverId))
            .andExpect(jsonPath("$.createdBy.email").value(DEV_EMAIL))
            .andExpect(jsonPath("$.amount").value(7.25));

        mockMvc.perform(get("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBalance").value(0.00))
            .andExpect(jsonPath("$.members[0].balance").value(12.75))
            .andExpect(jsonPath("$.members[1].balance").value(-12.75));
    }

    @Test
    void settlementRejectsInvalidAmountsSelfTransferAndOverSettlement() throws Exception {
        String groupId = createGroup("Settlement");
        String receiverId = getMembershipId(groupId, DEV_EMAIL);
        String senderId = addMember(groupId, "Mira", "mira@example.com");
        recordExpense(groupId, "Shared bill", "20.00", receiverId,
            """
                {"membershipId":"%s","amount":20.00}
                """.formatted(senderId));
        long settlementsBefore = settlementRepository.count();

        for (String amount : new String[] {"0", "-1.00"}) {
            mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                    .with(httpBasic(DEV_EMAIL, "test-secret"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(settlementJson(senderId, receiverId, amount)))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementJson(senderId, senderId, "1.00")))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementJson(senderId, receiverId, "20.01")))
            .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(settlementRepository.count()).isEqualTo(settlementsBefore);
    }

    @Test
    void settlementRejectsMembersOutsideGroupAndLeftMembers() throws Exception {
        String groupId = createGroup("Settlement");
        String receiverId = getMembershipId(groupId, DEV_EMAIL);
        String senderId = addMember(groupId, "Mira", "mira@example.com");
        recordExpense(groupId, "Shared bill", "20.00", receiverId,
            """
                {"membershipId":"%s","amount":20.00}
                """.formatted(senderId));

        String otherGroupId = createGroup("Other");
        String outsideMemberId = addMember(otherGroupId, "Dev", "dev@example.com");
        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementJson(senderId, outsideMemberId, "1.00")))
            .andExpect(status().isNotFound());

        membershipRepository.findById(UUID.fromString(senderId)).orElseThrow().leave();
        membershipRepository.flush();
        mockMvc.perform(post("/api/groups/{groupId}/settlements", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settlementJson(senderId, receiverId, "1.00")))
            .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(settlementRepository.count()).isZero();
    }

    private String createGroup(String name) throws Exception {
        String body = mockMvc.perform(post("/api/groups")
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s"}
                    """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return jsonValue(body, "id");
    }

    private String createPurchase(String groupId, String title, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/groups/{groupId}/purchases", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"%s","estimatedAmount":%s}
                    """.formatted(title, amount)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return jsonValue(body, "id");
    }

    private Offer findSampleOffer(OfferType type) {
        return offerRepository.findAllByActiveTrueOrderByMerchantNameAscTitleAsc().stream()
            .filter(offer -> offer.getOfferType() == type)
            .findFirst()
            .orElseThrow();
    }

    private void proposePurchase(String groupId, String purchaseId, UUID offerId) throws Exception {
        mockMvc.perform(post(
                "/api/groups/{groupId}/purchases/{purchaseId}/proposal",
                groupId,
                purchaseId
            )
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"offerId":"%s"}
                    """.formatted(offerId)))
            .andExpect(status().isCreated());
    }

    private String addMember(String groupId, String name, String email) throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/members", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","email":"%s"}
                    """.formatted(name, email)))
            .andExpect(status().isCreated());
        return getMembershipId(groupId, email);
    }

    private void recordExpense(
        String groupId,
        String description,
        String totalAmount,
        String payerMembershipId,
        String allocationsJson
    ) throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                .with(httpBasic(DEV_EMAIL, "test-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description":"%s",
                      "totalAmount":%s,
                      "paidByMembershipId":"%s",
                      "allocations":[%s]
                    }
                    """.formatted(description, totalAmount, payerMembershipId, allocationsJson)))
            .andExpect(status().isCreated());
    }

    private static String settlementJson(String fromId, String toId, String amount) {
        return """
            {"fromMembershipId":"%s","toMembershipId":"%s","amount":%s}
            """.formatted(fromId, toId, amount);
    }

    private String getMembershipId(String groupId, String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return membershipRepository.findByGroup_IdAndUser_Id(UUID.fromString(groupId), user.getId())
            .orElseThrow()
            .getId()
            .toString();
    }

    private static String jsonValue(String json, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            for (String part : field.split("\\.")) {
                node = part.matches("\\[\\d+\\]")
                    ? node.get(Integer.parseInt(part.substring(1, part.length() - 1)))
                    : node.get(part);
            }
            return node.asText();
        } catch (Exception exception) {
            throw new AssertionError("Could not read response field " + field, exception);
        }
    }
}

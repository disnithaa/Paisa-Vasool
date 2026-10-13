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
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json)
                .get(field)
                .asText();
        } catch (Exception exception) {
            throw new AssertionError("Could not read response field " + field, exception);
        }
    }
}

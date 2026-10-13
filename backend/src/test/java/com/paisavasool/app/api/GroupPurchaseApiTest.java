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

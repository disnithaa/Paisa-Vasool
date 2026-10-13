package com.paisavasool.app.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.expense.Expense;
import com.paisavasool.app.domain.expense.ExpenseAllocation;
import com.paisavasool.app.domain.expense.ExpenseAllocationRepository;
import com.paisavasool.app.domain.expense.ExpenseRepository;
import com.paisavasool.app.domain.expense.Settlement;
import com.paisavasool.app.domain.expense.SettlementRepository;
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.user.User;
import com.paisavasool.app.domain.user.UserRepository;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:domain-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    void flywaySchemaAndJpaMappingsPersistDomainRecords() {
        User user = userRepository.saveAndFlush(new User("Asha", "asha@example.com"));
        Group group = groupRepository.saveAndFlush(new Group("Trip"));
        membershipRepository.saveAndFlush(new GroupMembership(
            user, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        Purchase purchase = purchaseRepository.saveAndFlush(
            new Purchase(group, user, "Train tickets", new BigDecimal("1250.50"))
        );

        assertThat(purchase.getId()).isNotNull();
        assertThat(purchase.getEstimatedAmount()).isEqualByComparingTo("1250.50");
    }

    @Test
    void databaseRejectsDuplicateMembershipForSameGroupAndUser() {
        User user = userRepository.saveAndFlush(new User("Asha", "asha@example.com"));
        Group group = groupRepository.saveAndFlush(new Group("Trip"));
        membershipRepository.saveAndFlush(new GroupMembership(
            user, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(new GroupMembership(
            user, group, MembershipRole.MEMBER, MembershipStatus.ACTIVE
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywaySchemaAndJpaMappingsPersistExpensesAllocationsAndSettlements() {
        User payerUser = userRepository.saveAndFlush(new User("Asha", "asha@example.com"));
        User recipientUser = userRepository.saveAndFlush(new User("Mira", "mira@example.com"));
        Group group = groupRepository.saveAndFlush(new Group("Trip"));
        GroupMembership payer = membershipRepository.saveAndFlush(new GroupMembership(
            payerUser, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));
        GroupMembership recipient = membershipRepository.saveAndFlush(new GroupMembership(
            recipientUser, group, MembershipRole.MEMBER, MembershipStatus.ACTIVE
        ));

        Expense expense = expenseRepository.saveAndFlush(new Expense(
            group,
            null,
            "Shared dinner",
            new BigDecimal("25.50"),
            payer,
            payer,
            Instant.parse("2026-01-01T12:00:00Z")
        ));
        allocationRepository.saveAllAndFlush(List.of(
            new ExpenseAllocation(expense, payer, new BigDecimal("10.00")),
            new ExpenseAllocation(expense, recipient, new BigDecimal("15.50"))
        ));
        Settlement settlement = settlementRepository.saveAndFlush(new Settlement(
            group, recipient, payer, new BigDecimal("15.50"), payer, Instant.parse("2026-01-02T12:00:00Z")
        ));

        assertThat(expense.getId()).isNotNull();
        assertThat(expenseRepository.findWithDetailsByIdAndGroup_Id(expense.getId(), group.getId()))
            .get()
            .extracting(Expense::getTotalAmount)
            .isEqualTo(new BigDecimal("25.50"));
        assertThat(allocationRepository.findAllByExpense_IdOrderByIdAsc(expense.getId()))
            .extracting(ExpenseAllocation::getAmount)
            .containsExactlyInAnyOrder(new BigDecimal("10.00"), new BigDecimal("15.50"));
        assertThat(settlementRepository.findAllByGroup_IdOrderBySettledAtDesc(group.getId()))
            .extracting(Settlement::getId)
            .containsExactly(settlement.getId());
    }

    @Test
    void databaseRejectsNonPositiveAllocationAmounts() {
        User memberUser = userRepository.saveAndFlush(new User("Asha", "asha@example.com"));
        Group group = groupRepository.saveAndFlush(new Group("Trip"));
        GroupMembership member = membershipRepository.saveAndFlush(new GroupMembership(
            memberUser, group, MembershipRole.OWNER, MembershipStatus.ACTIVE
        ));
        Expense expense = expenseRepository.saveAndFlush(new Expense(
            group,
            null,
            "Shared dinner",
            new BigDecimal("10.00"),
            member,
            member,
            Instant.parse("2026-01-01T12:00:00Z")
        ));
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                INSERT INTO app.expense_allocations (id, expense_id, membership_id, amount)
                VALUES (?, ?, ?, ?)
                """,
            java.util.UUID.randomUUID(), expense.getId(), member.getId(), new BigDecimal("-1.00")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.paisavasool.app.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.MembershipRole;
import com.paisavasool.app.domain.group.MembershipStatus;
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
}

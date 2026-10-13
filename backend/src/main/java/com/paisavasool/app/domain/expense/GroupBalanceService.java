package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.GroupBalancesResponse;
import com.paisavasool.app.api.dto.MemberBalanceResponse;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupService;

@Service
public class GroupBalanceService {

    private static final BigDecimal ZERO_INR = new BigDecimal("0.00");

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final SettlementRepository settlementRepository;
    private final GroupService groupService;

    public GroupBalanceService(
        GroupRepository groupRepository,
        GroupMembershipRepository membershipRepository,
        ExpenseRepository expenseRepository,
        ExpenseAllocationRepository allocationRepository,
        SettlementRepository settlementRepository,
        GroupService groupService
    ) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.expenseRepository = expenseRepository;
        this.allocationRepository = allocationRepository;
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
    }

    @Transactional(readOnly = true)
    public GroupBalancesResponse getBalances(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        groupService.requireActiveMember(groupId);

        List<GroupMembership> memberships = membershipRepository.findAllByGroup_IdOrderByJoinedAtAsc(groupId);
        Map<UUID, BigDecimal> balances = new HashMap<>();
        memberships.forEach(membership -> balances.put(membership.getId(), ZERO_INR));

        expenseRepository.findPayerCreditsByGroupId(groupId)
            .forEach(entry -> adjust(balances, entry.getMembershipId(), entry.getAmount()));
        allocationRepository.findAllocationDebitsByGroupId(groupId)
            .forEach(entry -> adjust(balances, entry.getMembershipId(), entry.getAmount().negate()));
        settlementRepository.findBalanceEntriesByGroupId(groupId).forEach(entry -> {
            adjust(balances, entry.getFromMembershipId(), entry.getAmount());
            adjust(balances, entry.getToMembershipId(), entry.getAmount().negate());
        });

        BigDecimal total = balances.values().stream().reduce(ZERO_INR, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Group ledger balances do not sum to zero");
        }

        List<MemberBalanceResponse> memberBalances = memberships.stream()
            .map(membership -> MemberBalanceResponse.from(membership, balances.get(membership.getId())))
            .toList();
        return new GroupBalancesResponse(
            groupId,
            "INR",
            "Positive means receivable; negative means owed",
            total,
            memberBalances
        );
    }

    private static void adjust(Map<UUID, BigDecimal> balances, UUID membershipId, BigDecimal change) {
        BigDecimal current = balances.get(membershipId);
        if (current == null) {
            throw new IllegalStateException("Ledger entry refers to a member outside its group");
        }
        balances.put(membershipId, current.add(change));
    }
}

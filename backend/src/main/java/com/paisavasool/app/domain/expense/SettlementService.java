package com.paisavasool.app.domain.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.CreateSettlementRequest;
import com.paisavasool.app.api.dto.LedgerMemberResponse;
import com.paisavasool.app.api.dto.MemberBalanceResponse;
import com.paisavasool.app.api.dto.SettlementResponse;
import com.paisavasool.app.api.error.ConflictException;
import com.paisavasool.app.api.error.ForbiddenException;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupService;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.money.MoneyAmount;
import com.paisavasool.app.domain.user.CurrentUserService;
import com.paisavasool.app.domain.user.User;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupService groupService;
    private final GroupBalanceService groupBalanceService;
    private final CurrentUserService currentUserService;

    public SettlementService(
        SettlementRepository settlementRepository,
        GroupRepository groupRepository,
        GroupMembershipRepository membershipRepository,
        GroupService groupService,
        GroupBalanceService groupBalanceService,
        CurrentUserService currentUserService
    ) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.groupService = groupService;
        this.groupBalanceService = groupBalanceService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public SettlementResponse recordSettlement(UUID groupId, CreateSettlementRequest request) {
        Group group = groupRepository.findByIdForUpdate(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        groupService.requireActiveMember(groupId);
        MoneyAmount.requirePositiveInr(request.amount(), "amount");
        if (request.fromMembershipId().equals(request.toMembershipId())) {
            throw new ConflictException("A settlement must be between different members");
        }

        User currentUser = currentUserService.requireCurrentUser();
        GroupMembership creator = membershipRepository.findByGroup_IdAndUser_Id(groupId, currentUser.getId())
            .orElseThrow(() -> new ForbiddenException("An active group membership is required"));
        Set<UUID> ids = new HashSet<>(List.of(
            request.fromMembershipId(), request.toMembershipId(), creator.getId()
        ));
        Map<UUID, GroupMembership> memberships = membershipRepository
            .findAllByIdInAndGroup_Id(ids, groupId)
            .stream()
            .collect(Collectors.toMap(GroupMembership::getId, membership -> membership));
        if (memberships.size() != ids.size()) {
            throw new ResourceNotFoundException("One or more members do not belong to this group");
        }
        if (memberships.values().stream().anyMatch(member -> member.getStatus() != MembershipStatus.ACTIVE)) {
            throw new ForbiddenException("Settlement participants must be active group members");
        }

        GroupMembership fromMember = memberships.get(request.fromMembershipId());
        GroupMembership toMember = memberships.get(request.toMembershipId());
        validateSettlementAgainstBalances(groupId, fromMember.getId(), toMember.getId(), request.amount());

        Settlement settlement = settlementRepository.saveAndFlush(new Settlement(
            group,
            fromMember,
            toMember,
            request.amount(),
            creator,
            request.settledAt() == null ? Instant.now() : request.settledAt()
        ));
        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listSettlements(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        groupService.requireActiveMember(groupId);
        return settlementRepository.findAllByGroup_IdOrderBySettledAtDesc(groupId).stream()
            .map(SettlementService::toResponse)
            .toList();
    }

    private void validateSettlementAgainstBalances(
        UUID groupId,
        UUID fromMembershipId,
        UUID toMembershipId,
        BigDecimal amount
    ) {
        Map<UUID, BigDecimal> balances = groupBalanceService.getBalances(groupId).members().stream()
            .collect(Collectors.toMap(
                member -> member.member().membershipId(),
                MemberBalanceResponse::balance
            ));
        BigDecimal fromBalance = balances.get(fromMembershipId);
        BigDecimal toBalance = balances.get(toMembershipId);
        if (fromBalance == null || toBalance == null) {
            throw new ResourceNotFoundException("One or more members do not belong to this group");
        }
        if (fromBalance.signum() >= 0 || toBalance.signum() <= 0) {
            throw new ConflictException("Settlement sender must owe and recipient must be owed");
        }
        if (amount.compareTo(fromBalance.negate()) > 0 || amount.compareTo(toBalance) > 0) {
            throw new ConflictException("Settlement amount exceeds the debt or receivable balance");
        }
    }

    private static SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(
            settlement.getId(),
            settlement.getGroup().getId(),
            LedgerMemberResponse.from(settlement.getFromMember()),
            LedgerMemberResponse.from(settlement.getToMember()),
            settlement.getAmount(),
            LedgerMemberResponse.from(settlement.getCreatedBy()),
            settlement.getSettledAt(),
            settlement.getCreatedAt()
        );
    }
}

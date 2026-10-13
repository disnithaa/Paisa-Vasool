package com.paisavasool.app.domain.expense;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.CreateExpenseRequest;
import com.paisavasool.app.api.dto.ExpenseAllocationRequest;
import com.paisavasool.app.api.dto.ExpenseAllocationResponse;
import com.paisavasool.app.api.dto.ExpenseResponse;
import com.paisavasool.app.api.dto.LedgerMemberResponse;
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
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.user.CurrentUserService;
import com.paisavasool.app.domain.user.User;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final PurchaseRepository purchaseRepository;
    private final GroupService groupService;
    private final CurrentUserService currentUserService;

    public ExpenseService(
        ExpenseRepository expenseRepository,
        ExpenseAllocationRepository allocationRepository,
        GroupRepository groupRepository,
        GroupMembershipRepository membershipRepository,
        PurchaseRepository purchaseRepository,
        GroupService groupService,
        CurrentUserService currentUserService
    ) {
        this.expenseRepository = expenseRepository;
        this.allocationRepository = allocationRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.purchaseRepository = purchaseRepository;
        this.groupService = groupService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public ExpenseResponse createExpense(UUID groupId, CreateExpenseRequest request) {
        Group group = groupRepository.findByIdForUpdate(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        groupService.requireActiveMember(groupId);
        User currentUser = currentUserService.requireCurrentUser();
        GroupMembership creator = requireActiveMembership(groupId, currentUser.getId());

        validateAllocationTotal(request);

        Set<UUID> memberIds = new HashSet<>();
        memberIds.add(request.paidByMembershipId());
        request.allocations().forEach(allocation -> memberIds.add(allocation.membershipId()));
        Map<UUID, GroupMembership> memberships = findMemberships(groupId, memberIds);
        requireActiveMemberships(memberships.values());
        GroupMembership payer = requireMembership(memberships, request.paidByMembershipId());

        Purchase purchase = request.purchaseId() == null
            ? null
            : purchaseRepository.findByIdAndGroup_Id(request.purchaseId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found in this group"));

        Instant incurredAt = request.incurredAt() == null ? Instant.now() : request.incurredAt();
        Expense expense = expenseRepository.saveAndFlush(new Expense(
            group, purchase, request.description(), request.totalAmount(), payer, creator, incurredAt
        ));

        List<ExpenseAllocation> allocations = request.allocations().stream()
            .map(allocation -> new ExpenseAllocation(
                expense,
                requireMembership(memberships, allocation.membershipId()),
                allocation.amount()
            ))
            .toList();
        allocations.forEach(expense::addAllocation);
        allocationRepository.saveAllAndFlush(allocations);

        return toResponse(expense, allocations);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(UUID groupId) {
        findGroup(groupId);
        groupService.requireActiveMember(groupId);
        List<Expense> expenses = expenseRepository.findAllByGroup_IdOrderByIncurredAtDesc(groupId);
        if (expenses.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExpenseAllocation>> allocationsByExpense = allocationsFor(expenses);
        return expenses.stream()
            .map(expense -> toResponse(expense, allocationsByExpense.getOrDefault(expense.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(UUID groupId, UUID expenseId) {
        findGroup(groupId);
        groupService.requireActiveMember(groupId);
        Expense expense = expenseRepository.findWithDetailsByIdAndGroup_Id(expenseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        return toResponse(expense, allocationRepository.findAllByExpense_IdOrderByIdAsc(expenseId));
    }

    private void validateAllocationTotal(CreateExpenseRequest request) {
        MoneyAmount.requirePositiveInr(request.totalAmount(), "totalAmount");
        Set<UUID> memberIds = new HashSet<>();
        var sum = request.allocations().stream()
            .map(allocation -> {
                MoneyAmount.requirePositiveInr(allocation.amount(), "allocation amount");
                if (!memberIds.add(allocation.membershipId())) {
                    throw new ConflictException("Each member can have only one allocation per expense");
                }
                return allocation.amount();
            })
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (sum.compareTo(request.totalAmount()) != 0) {
            throw new ConflictException("Expense allocations must sum exactly to totalAmount");
        }
    }

    private Map<UUID, GroupMembership> findMemberships(UUID groupId, Collection<UUID> membershipIds) {
        List<GroupMembership> memberships = membershipRepository.findAllByIdInAndGroup_Id(membershipIds, groupId);
        if (memberships.size() != membershipIds.size()) {
            throw new ResourceNotFoundException("One or more members do not belong to this group");
        }
        return memberships.stream().collect(Collectors.toMap(GroupMembership::getId, membership -> membership));
    }

    private static void requireActiveMemberships(Collection<GroupMembership> memberships) {
        if (memberships.stream().anyMatch(membership -> membership.getStatus() != MembershipStatus.ACTIVE)) {
            throw new ForbiddenException("Expense payers and allocated members must be active group members");
        }
    }

    private GroupMembership requireActiveMembership(UUID groupId, UUID userId) {
        GroupMembership membership = membershipRepository.findByGroup_IdAndUser_Id(groupId, userId)
            .orElseThrow(() -> new ForbiddenException("An active group membership is required"));
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ForbiddenException("An active group membership is required");
        }
        return membership;
    }

    private static GroupMembership requireMembership(Map<UUID, GroupMembership> memberships, UUID membershipId) {
        GroupMembership membership = memberships.get(membershipId);
        if (membership == null) {
            throw new ResourceNotFoundException("Member not found in this group");
        }
        return membership;
    }

    private Map<UUID, List<ExpenseAllocation>> allocationsFor(List<Expense> expenses) {
        List<UUID> expenseIds = expenses.stream().map(Expense::getId).toList();
        Map<UUID, List<ExpenseAllocation>> allocationsByExpense = new HashMap<>();
        allocationRepository.findAllByExpense_IdInOrderByExpense_IdAsc(expenseIds).forEach(allocation ->
            allocationsByExpense.computeIfAbsent(allocation.getExpense().getId(), ignored -> new java.util.ArrayList<>())
                .add(allocation)
        );
        return allocationsByExpense;
    }

    private Group findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private static ExpenseResponse toResponse(Expense expense, List<ExpenseAllocation> allocations) {
        return new ExpenseResponse(
            expense.getId(),
            expense.getGroup().getId(),
            expense.getPurchase() == null ? null : expense.getPurchase().getId(),
            expense.getDescription(),
            expense.getTotalAmount(),
            LedgerMemberResponse.from(expense.getPaidBy()),
            LedgerMemberResponse.from(expense.getCreatedBy()),
            expense.getIncurredAt(),
            expense.getCreatedAt(),
            allocations.stream()
                .map(allocation -> new ExpenseAllocationResponse(
                    LedgerMemberResponse.from(allocation.getMember()),
                    allocation.getAmount()
                ))
                .toList()
        );
    }
}

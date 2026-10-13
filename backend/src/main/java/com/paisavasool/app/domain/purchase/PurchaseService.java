package com.paisavasool.app.domain.purchase;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.CreatePurchaseRequest;
import com.paisavasool.app.api.dto.PurchaseResponse;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupService;
import com.paisavasool.app.domain.user.CurrentUserService;
import com.paisavasool.app.domain.user.User;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final CurrentUserService currentUserService;

    public PurchaseService(
        PurchaseRepository purchaseRepository,
        GroupRepository groupRepository,
        GroupService groupService,
        CurrentUserService currentUserService
    ) {
        this.purchaseRepository = purchaseRepository;
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public PurchaseResponse createPurchase(UUID groupId, CreatePurchaseRequest request) {
        Group group = findGroup(groupId);
        groupService.requireActiveMember(groupId);
        User creator = currentUserService.requireCurrentUser();
        Purchase purchase = purchaseRepository.saveAndFlush(
            new Purchase(group, creator, request.title(), request.estimatedAmount())
        );
        return toResponse(purchase, groupId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listPurchases(UUID groupId) {
        findGroup(groupId);
        groupService.requireActiveMember(groupId);
        return purchaseRepository.findAllByGroup_IdOrderByCreatedAtDesc(groupId).stream()
            .map(purchase -> toResponse(purchase, groupId))
            .toList();
    }

    private Group findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private static PurchaseResponse toResponse(Purchase purchase, UUID groupId) {
        User creator = purchase.getCreatedBy();
        return new PurchaseResponse(
            purchase.getId(),
            groupId,
            creator.getId(),
            creator.getEmail(),
            purchase.getTitle(),
            purchase.getEstimatedAmount(),
            purchase.getStatus(),
            purchase.getCreatedAt()
        );
    }
}

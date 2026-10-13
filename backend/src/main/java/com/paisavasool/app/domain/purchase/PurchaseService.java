package com.paisavasool.app.domain.purchase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.CreatePurchaseRequest;
import com.paisavasool.app.api.dto.ProposePurchaseRequest;
import com.paisavasool.app.api.dto.PurchaseProposalResponse;
import com.paisavasool.app.api.dto.PurchaseResponse;
import com.paisavasool.app.api.dto.PurchaseActorResponse;
import com.paisavasool.app.api.error.ConflictException;
import com.paisavasool.app.api.error.ForbiddenException;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.GroupMembershipRepository;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupService;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferEvaluation;
import com.paisavasool.app.domain.offer.OfferPricingService;
import com.paisavasool.app.domain.offer.OfferRepository;
import com.paisavasool.app.domain.user.CurrentUserService;
import com.paisavasool.app.domain.user.User;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final CurrentUserService currentUserService;
    private final OfferRepository offerRepository;
    private final OfferPricingService offerPricingService;
    private final GroupMembershipRepository membershipRepository;

    public PurchaseService(
        PurchaseRepository purchaseRepository,
        GroupRepository groupRepository,
        GroupService groupService,
        CurrentUserService currentUserService,
        OfferRepository offerRepository,
        OfferPricingService offerPricingService,
        GroupMembershipRepository membershipRepository
    ) {
        this.purchaseRepository = purchaseRepository;
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.currentUserService = currentUserService;
        this.offerRepository = offerRepository;
        this.offerPricingService = offerPricingService;
        this.membershipRepository = membershipRepository;
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

    @Transactional
    public PurchaseProposalResponse proposeOffer(
        UUID groupId,
        UUID purchaseId,
        ProposePurchaseRequest request
    ) {
        requireGroupMember(groupId);
        Purchase purchase = purchaseRepository.findByIdAndGroupIdForUpdate(purchaseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (purchase.getStatus() != PurchaseStatus.DRAFT || purchase.getOfferProposedAt() != null) {
            throw new ConflictException("An offer can only be proposed once for a draft purchase");
        }

        Offer offer = offerRepository.findById(request.offerId())
            .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        Instant proposedAt = Instant.now();
        OfferEvaluation evaluation = offerPricingService.evaluate(
            offer,
            purchase.getEstimatedAmount(),
            proposedAt
        );
        if (!evaluation.eligible()) {
            throw new ConflictException("Offer is not eligible: " + evaluation.ineligibilityReason());
        }

        purchase.proposeOffer(offer, evaluation, proposedAt);
        purchaseRepository.flush();
        return PurchaseProposalResponse.from(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseProposalResponse getProposal(UUID groupId, UUID purchaseId) {
        requireGroupMember(groupId);
        Purchase purchase = purchaseRepository.findByIdAndGroup_Id(purchaseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (purchase.getOfferProposedAt() == null) {
            throw new ConflictException("Purchase does not have an offer proposal");
        }
        return PurchaseProposalResponse.from(purchase);
    }

    @Transactional
    public PurchaseResponse approvePurchase(UUID groupId, UUID purchaseId) {
        GroupMembership actor = requireCurrentGroupMembership(groupId);
        Purchase purchase = findPurchaseForUpdate(groupId, purchaseId);
        if (purchase.getStatus() != PurchaseStatus.PENDING_APPROVAL || purchase.getOfferProposedAt() == null) {
            throw new ConflictException("Only a proposed purchase awaiting approval can be approved");
        }
        purchase.approve(actor, Instant.now());
        purchaseRepository.flush();
        return toResponse(purchase, groupId);
    }

    @Transactional
    public PurchaseResponse cancelPurchase(UUID groupId, UUID purchaseId) {
        GroupMembership actor = requireCurrentGroupMembership(groupId);
        Purchase purchase = findPurchaseForUpdate(groupId, purchaseId);
        if (purchase.getStatus() == PurchaseStatus.CANCELLED || purchase.getStatus() == PurchaseStatus.COMPLETED) {
            throw new ConflictException("A cancelled or completed purchase cannot be cancelled");
        }
        purchase.cancel(actor, Instant.now());
        purchaseRepository.flush();
        return toResponse(purchase, groupId);
    }

    @Transactional
    public PurchaseResponse completePurchaseSimulated(UUID groupId, UUID purchaseId) {
        GroupMembership actor = requireCurrentGroupMembership(groupId);
        Purchase purchase = findPurchaseForUpdate(groupId, purchaseId);
        if (purchase.getStatus() != PurchaseStatus.APPROVED) {
            throw new ConflictException("Only an approved purchase can be completed as simulated");
        }
        purchase.completeSimulated(actor, Instant.now());
        purchaseRepository.flush();
        return toResponse(purchase, groupId);
    }

    private Group findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private void requireGroupMember(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        groupService.requireActiveMember(groupId);
    }

    private GroupMembership requireCurrentGroupMembership(UUID groupId) {
        requireGroupMember(groupId);
        User currentUser = currentUserService.requireCurrentUser();
        return membershipRepository.findByGroup_IdAndUser_Id(groupId, currentUser.getId())
            .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
            .orElseThrow(() -> new ForbiddenException("An active group membership is required"));
    }

    private Purchase findPurchaseForUpdate(UUID groupId, UUID purchaseId) {
        return purchaseRepository.findByIdAndGroupIdForUpdate(purchaseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
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
            purchase.getApprovedBy() == null ? null : PurchaseActorResponse.from(purchase.getApprovedBy()),
            purchase.getApprovedAt(),
            purchase.getCancelledBy() == null ? null : PurchaseActorResponse.from(purchase.getCancelledBy()),
            purchase.getCancelledAt(),
            purchase.getCompletedBy() == null ? null : PurchaseActorResponse.from(purchase.getCompletedBy()),
            purchase.getCompletedAt(),
            purchase.getCompletionType(),
            purchase.getCreatedAt()
        );
    }
}

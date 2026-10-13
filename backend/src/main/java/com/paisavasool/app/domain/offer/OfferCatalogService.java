package com.paisavasool.app.domain.offer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.OfferEligibilityResponse;
import com.paisavasool.app.api.dto.OfferResponse;
import com.paisavasool.app.api.error.ConflictException;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupService;
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.purchase.PurchaseStatus;

@Service
public class OfferCatalogService {

    private final OfferRepository offerRepository;
    private final GroupRepository groupRepository;
    private final PurchaseRepository purchaseRepository;
    private final GroupService groupService;
    private final OfferPricingService offerPricingService;

    public OfferCatalogService(
        OfferRepository offerRepository,
        GroupRepository groupRepository,
        PurchaseRepository purchaseRepository,
        GroupService groupService,
        OfferPricingService offerPricingService
    ) {
        this.offerRepository = offerRepository;
        this.groupRepository = groupRepository;
        this.purchaseRepository = purchaseRepository;
        this.groupService = groupService;
        this.offerPricingService = offerPricingService;
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> listOffers(UUID groupId) {
        requireGroupMember(groupId);
        Instant now = Instant.now();
        return offerRepository
            .findAllByActiveTrueAndValidFromLessThanEqualAndValidUntilGreaterThanOrderByMerchantNameAscTitleAsc(
                now,
                now
            )
            .stream()
            .map(OfferResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OfferResponse getOffer(UUID groupId, UUID offerId) {
        requireGroupMember(groupId);
        Offer offer = offerRepository.findById(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        return OfferResponse.from(offer);
    }

    @Transactional(readOnly = true)
    public List<OfferEligibilityResponse> getEligibleOffers(UUID groupId, UUID purchaseId) {
        requireGroupMember(groupId);
        Purchase purchase = purchaseRepository.findByIdAndGroup_Id(purchaseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new ConflictException("Offers can only be evaluated for draft purchases");
        }

        Instant evaluatedAt = Instant.now();
        return offerRepository.findAllByActiveTrueOrderByMerchantNameAscTitleAsc().stream()
            .map(offer -> {
                OfferResponse offerResponse = OfferResponse.from(offer);
                OfferEvaluation evaluation = offerPricingService.evaluate(
                    offer,
                    purchase.getEstimatedAmount(),
                    evaluatedAt
                );
                return OfferEligibilityResponse.from(offerResponse, evaluation);
            })
            .toList();
    }

    private void requireGroupMember(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        groupService.requireActiveMember(groupId);
    }
}

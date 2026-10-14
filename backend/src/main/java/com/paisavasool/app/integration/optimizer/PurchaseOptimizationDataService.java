package com.paisavasool.app.integration.optimizer;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisavasool.app.api.dto.OfferResponse;
import com.paisavasool.app.api.error.ConflictException;
import com.paisavasool.app.api.error.ResourceNotFoundException;
import com.paisavasool.app.domain.group.GroupRepository;
import com.paisavasool.app.domain.group.GroupService;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferEvaluation;
import com.paisavasool.app.domain.offer.OfferPricingService;
import com.paisavasool.app.domain.offer.OfferRepository;
import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseRepository;
import com.paisavasool.app.domain.purchase.PurchaseStatus;
import com.paisavasool.app.integration.optimizer.dto.OptimizerOfferCandidate;

@Service
public class PurchaseOptimizationDataService {

    private final GroupRepository groupRepository;
    private final GroupService groupService;
    private final PurchaseRepository purchaseRepository;
    private final OfferRepository offerRepository;
    private final OfferPricingService offerPricingService;

    public PurchaseOptimizationDataService(
        GroupRepository groupRepository,
        GroupService groupService,
        PurchaseRepository purchaseRepository,
        OfferRepository offerRepository,
        OfferPricingService offerPricingService
    ) {
        this.groupRepository = groupRepository;
        this.groupService = groupService;
        this.purchaseRepository = purchaseRepository;
        this.offerRepository = offerRepository;
        this.offerPricingService = offerPricingService;
    }

    @Transactional(readOnly = true)
    public PurchaseOptimizationData load(UUID groupId, UUID purchaseId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found");
        }
        groupService.requireActiveMember(groupId);

        Purchase purchase = purchaseRepository.findByIdAndGroup_Id(purchaseId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new ConflictException("Only draft purchases can be optimized");
        }

        Instant evaluatedAt = Instant.now();
        List<PurchaseOptimizationData.EligibleOffer> eligibleOffers = offerRepository
            .findAllByActiveTrueOrderByMerchantNameAscTitleAsc()
            .stream()
            .map(offer -> toEligibleOffer(offer, purchase, evaluatedAt))
            .filter(Objects::nonNull)
            .toList();
        return new PurchaseOptimizationData(
            purchase.getId(),
            purchase.getEstimatedAmount(),
            evaluatedAt,
            eligibleOffers
        );
    }

    private PurchaseOptimizationData.EligibleOffer toEligibleOffer(
        Offer offer,
        Purchase purchase,
        Instant evaluatedAt
    ) {
        OfferEvaluation evaluation = offerPricingService.evaluate(
            offer,
            purchase.getEstimatedAmount(),
            evaluatedAt
        );
        if (!evaluation.eligible()) {
            return null;
        }
        OptimizerOfferCandidate candidate = OptimizerOfferCandidate.from(
            offer.getId(),
            offer.getOfferType(),
            offer.isActive(),
            offer.getValidFrom(),
            offer.getValidUntil(),
            offer.getMinimumSpend(),
            offer.getMaximumDiscount(),
            offer.getDiscountPercentage(),
            offer.getDiscountAmount()
        );
        return new PurchaseOptimizationData.EligibleOffer(
            candidate,
            OfferResponse.from(offer),
            evaluation.discountAmount(),
            evaluation.finalAmount()
        );
    }
}

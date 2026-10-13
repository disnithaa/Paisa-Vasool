package com.paisavasool.app.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paisavasool.app.domain.purchase.Purchase;
import com.paisavasool.app.domain.purchase.PurchaseStatus;
import com.paisavasool.app.domain.user.User;

public record PurchaseProposalResponse(
    UUID purchaseId,
    UUID groupId,
    UUID createdById,
    String createdByEmail,
    String title,
    BigDecimal estimatedAmount,
    PurchaseStatus status,
    ProposedOfferResponse offer,
    BigDecimal appliedDiscountAmount,
    BigDecimal expectedFinalAmount,
    Instant proposedAt
) {
    public static PurchaseProposalResponse from(Purchase purchase) {
        User creator = purchase.getCreatedBy();
        return new PurchaseProposalResponse(
            purchase.getId(),
            purchase.getGroup().getId(),
            creator.getId(),
            creator.getEmail(),
            purchase.getTitle(),
            purchase.getEstimatedAmount(),
            purchase.getStatus(),
            ProposedOfferResponse.from(purchase),
            purchase.getAppliedDiscountAmount(),
            purchase.getExpectedFinalAmount(),
            purchase.getOfferProposedAt()
        );
    }
}

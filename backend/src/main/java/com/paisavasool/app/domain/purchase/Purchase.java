package com.paisavasool.app.domain.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.paisavasool.app.domain.group.Group;
import com.paisavasool.app.domain.group.GroupMembership;
import com.paisavasool.app.domain.group.MembershipStatus;
import com.paisavasool.app.domain.money.MoneyAmount;
import com.paisavasool.app.domain.offer.Offer;
import com.paisavasool.app.domain.offer.OfferEvaluation;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "purchases", schema = "app")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_offer_id")
    private Offer proposedOffer;

    @Column(name = "offer_merchant_snapshot", length = 120)
    private String offerMerchantSnapshot;

    @Column(name = "offer_title_snapshot", length = 200)
    private String offerTitleSnapshot;

    @Column(name = "offer_description_snapshot", length = 1000)
    private String offerDescriptionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type_snapshot", length = 32)
    private OfferType offerTypeSnapshot;

    @Column(name = "offer_discount_percentage_snapshot", precision = 5, scale = 2)
    private BigDecimal offerDiscountPercentageSnapshot;

    @Column(name = "offer_discount_amount_snapshot", precision = 12, scale = 2)
    private BigDecimal offerDiscountAmountSnapshot;

    @Column(name = "offer_minimum_spend_snapshot", precision = 12, scale = 2)
    private BigDecimal offerMinimumSpendSnapshot;

    @Column(name = "offer_maximum_discount_snapshot", precision = 12, scale = 2)
    private BigDecimal offerMaximumDiscountSnapshot;

    @Column(name = "offer_valid_from_snapshot")
    private Instant offerValidFromSnapshot;

    @Column(name = "offer_valid_until_snapshot")
    private Instant offerValidUntilSnapshot;

    @Column(name = "offer_terms_snapshot", length = 2000)
    private String offerTermsSnapshot;

    @Column(name = "applied_discount_amount", precision = 12, scale = 2)
    private BigDecimal appliedDiscountAmount;

    @Column(name = "expected_final_amount", precision = 12, scale = 2)
    private BigDecimal expectedFinalAmount;

    @Column(name = "offer_proposed_at")
    private Instant offerProposedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_membership_id")
    private GroupMembership approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_membership_id")
    private GroupMembership cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_membership_id")
    private GroupMembership completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_type", length = 24)
    private PurchaseCompletionType completionType;

    protected Purchase() {
    }

    public Purchase(Group group, User createdBy, String title, BigDecimal estimatedAmount) {
        this.group = requireValue(group, "group");
        this.createdBy = requireValue(createdBy, "createdBy");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.title = title.trim();
        this.estimatedAmount = MoneyAmount.requirePositiveInr(estimatedAmount, "estimatedAmount");
        this.status = PurchaseStatus.DRAFT;
    }

    public void proposeOffer(Offer offer, OfferEvaluation evaluation, Instant proposedAt) {
        if (status != PurchaseStatus.DRAFT || offerProposedAt != null) {
            throw new IllegalStateException("An offer can only be proposed once for a draft purchase");
        }
        if (offer == null || evaluation == null || !evaluation.eligible()) {
            throw new IllegalArgumentException("An eligible offer evaluation is required");
        }
        if (proposedAt == null) {
            throw new IllegalArgumentException("proposedAt must not be null");
        }
        BigDecimal discountAmount = evaluation.discountAmount();
        BigDecimal finalAmount = evaluation.finalAmount();
        if (discountAmount.signum() < 0 || finalAmount.signum() < 0
            || discountAmount.add(finalAmount).compareTo(estimatedAmount) != 0) {
            throw new IllegalArgumentException("Offer evaluation amounts must reconcile to the purchase estimate");
        }
        requireAllowedTransition(PurchaseStatus.PENDING_APPROVAL);

        proposedOffer = offer;
        offerMerchantSnapshot = offer.getMerchantName();
        offerTitleSnapshot = offer.getTitle();
        offerDescriptionSnapshot = offer.getDescription();
        offerTypeSnapshot = offer.getOfferType();
        offerDiscountPercentageSnapshot = offer.getDiscountPercentage();
        offerDiscountAmountSnapshot = offer.getDiscountAmount();
        offerMinimumSpendSnapshot = offer.getMinimumSpend();
        offerMaximumDiscountSnapshot = offer.getMaximumDiscount();
        offerValidFromSnapshot = offer.getValidFrom();
        offerValidUntilSnapshot = offer.getValidUntil();
        offerTermsSnapshot = offer.getTerms();
        appliedDiscountAmount = discountAmount;
        expectedFinalAmount = finalAmount;
        offerProposedAt = proposedAt;
        transitionTo(PurchaseStatus.PENDING_APPROVAL);
    }

    public void approve(GroupMembership approver, Instant approvedAt) {
        requireActiveActorFromGroup(approver);
        requireTimestamp(approvedAt, "approvedAt");
        requireAllowedTransition(PurchaseStatus.APPROVED);
        if (offerProposedAt == null) {
            throw new IllegalStateException("A purchase proposal is required before approval");
        }
        approvedBy = approver;
        this.approvedAt = approvedAt;
        transitionTo(PurchaseStatus.APPROVED);
    }

    public void cancel(GroupMembership canceller, Instant cancelledAt) {
        requireActiveActorFromGroup(canceller);
        requireTimestamp(cancelledAt, "cancelledAt");
        requireAllowedTransition(PurchaseStatus.CANCELLED);
        cancelledBy = canceller;
        this.cancelledAt = cancelledAt;
        transitionTo(PurchaseStatus.CANCELLED);
    }

    public void completeSimulated(GroupMembership completer, Instant completedAt) {
        requireActiveActorFromGroup(completer);
        requireTimestamp(completedAt, "completedAt");
        requireAllowedTransition(PurchaseStatus.COMPLETED);
        completionType = PurchaseCompletionType.SIMULATED;
        completedBy = completer;
        this.completedAt = completedAt;
        transitionTo(PurchaseStatus.COMPLETED);
    }

    private boolean isAllowedTransition(PurchaseStatus next) {
        return switch (status) {
            case DRAFT -> next == PurchaseStatus.PENDING_APPROVAL || next == PurchaseStatus.CANCELLED;
            case PENDING_APPROVAL -> next == PurchaseStatus.APPROVED || next == PurchaseStatus.CANCELLED;
            case APPROVED -> next == PurchaseStatus.COMPLETED || next == PurchaseStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }

    private void requireAllowedTransition(PurchaseStatus next) {
        if (next == null || !isAllowedTransition(next)) {
            throw new IllegalStateException("Invalid purchase status transition: " + status + " -> " + next);
        }
    }

    private void transitionTo(PurchaseStatus nextStatus) {
        requireAllowedTransition(nextStatus);
        status = nextStatus;
    }

    private void requireActiveActorFromGroup(GroupMembership actor) {
        if (actor == null || actor.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("An active group membership is required for this action");
        }
        Group actorGroup = actor.getGroup();
        boolean sameGroup = actorGroup == group
            || (group.getId() != null && group.getId().equals(actorGroup.getId()));
        if (!sameGroup) {
            throw new IllegalArgumentException("The action member must belong to the purchase group");
        }
    }

    private static void requireTimestamp(Instant timestamp, String fieldName) {
        if (timestamp == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Offer getProposedOffer() {
        return proposedOffer;
    }

    public String getOfferMerchantSnapshot() {
        return offerMerchantSnapshot;
    }

    public String getOfferTitleSnapshot() {
        return offerTitleSnapshot;
    }

    public String getOfferDescriptionSnapshot() {
        return offerDescriptionSnapshot;
    }

    public OfferType getOfferTypeSnapshot() {
        return offerTypeSnapshot;
    }

    public BigDecimal getOfferDiscountPercentageSnapshot() {
        return offerDiscountPercentageSnapshot;
    }

    public BigDecimal getOfferDiscountAmountSnapshot() {
        return offerDiscountAmountSnapshot;
    }

    public BigDecimal getOfferMinimumSpendSnapshot() {
        return offerMinimumSpendSnapshot;
    }

    public BigDecimal getOfferMaximumDiscountSnapshot() {
        return offerMaximumDiscountSnapshot;
    }

    public Instant getOfferValidFromSnapshot() {
        return offerValidFromSnapshot;
    }

    public Instant getOfferValidUntilSnapshot() {
        return offerValidUntilSnapshot;
    }

    public String getOfferTermsSnapshot() {
        return offerTermsSnapshot;
    }

    public BigDecimal getAppliedDiscountAmount() {
        return appliedDiscountAmount;
    }

    public BigDecimal getExpectedFinalAmount() {
        return expectedFinalAmount;
    }

    public Instant getOfferProposedAt() {
        return offerProposedAt;
    }

    public GroupMembership getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public GroupMembership getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public GroupMembership getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public PurchaseCompletionType getCompletionType() {
        return completionType;
    }
}

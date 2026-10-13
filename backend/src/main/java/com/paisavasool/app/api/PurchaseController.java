package com.paisavasool.app.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisavasool.app.api.dto.CreatePurchaseRequest;
import com.paisavasool.app.api.dto.ProposePurchaseRequest;
import com.paisavasool.app.api.dto.PurchaseProposalResponse;
import com.paisavasool.app.api.dto.PurchaseResponse;
import com.paisavasool.app.domain.purchase.PurchaseService;

@RestController
@RequestMapping("/api/groups/{groupId}/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> createPurchase(
        @PathVariable UUID groupId,
        @Valid @RequestBody CreatePurchaseRequest request
    ) {
        PurchaseResponse response = purchaseService.createPurchase(groupId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + groupId + "/purchases/" + response.id()))
            .body(response);
    }

    @GetMapping
    public List<PurchaseResponse> listPurchases(@PathVariable UUID groupId) {
        return purchaseService.listPurchases(groupId);
    }

    @PostMapping("/{purchaseId}/proposal")
    public ResponseEntity<PurchaseProposalResponse> proposeOffer(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId,
        @Valid @RequestBody ProposePurchaseRequest request
    ) {
        PurchaseProposalResponse response = purchaseService.proposeOffer(groupId, purchaseId, request);
        return ResponseEntity.created(URI.create(
            "/api/groups/" + groupId + "/purchases/" + purchaseId + "/proposal"
        )).body(response);
    }

    @GetMapping("/{purchaseId}/proposal")
    public PurchaseProposalResponse getProposal(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return purchaseService.getProposal(groupId, purchaseId);
    }

    @PostMapping("/{purchaseId}/approve")
    public PurchaseResponse approvePurchase(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return purchaseService.approvePurchase(groupId, purchaseId);
    }

    @PostMapping("/{purchaseId}/cancel")
    public PurchaseResponse cancelPurchase(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return purchaseService.cancelPurchase(groupId, purchaseId);
    }

    @PostMapping("/{purchaseId}/complete-simulated")
    public PurchaseResponse completePurchaseSimulated(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return purchaseService.completePurchaseSimulated(groupId, purchaseId);
    }
}

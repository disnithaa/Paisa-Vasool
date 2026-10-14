package com.paisavasool.app.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisavasool.app.api.dto.PurchaseOptimizationResponse;
import com.paisavasool.app.integration.optimizer.PurchaseOptimizationService;

@RestController
@RequestMapping("/api/groups/{groupId}/purchases/{purchaseId}/optimization")
public class PurchaseOptimizationController {

    private final PurchaseOptimizationService purchaseOptimizationService;

    public PurchaseOptimizationController(
        PurchaseOptimizationService purchaseOptimizationService
    ) {
        this.purchaseOptimizationService = purchaseOptimizationService;
    }

    @PostMapping
    public PurchaseOptimizationResponse optimize(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return purchaseOptimizationService.optimize(groupId, purchaseId);
    }
}

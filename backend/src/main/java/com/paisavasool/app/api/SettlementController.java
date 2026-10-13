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

import com.paisavasool.app.api.dto.CreateSettlementRequest;
import com.paisavasool.app.api.dto.SettlementResponse;
import com.paisavasool.app.domain.expense.SettlementService;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    public ResponseEntity<SettlementResponse> recordSettlement(
        @PathVariable UUID groupId,
        @Valid @RequestBody CreateSettlementRequest request
    ) {
        SettlementResponse response = settlementService.recordSettlement(groupId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + groupId + "/settlements/" + response.id()))
            .body(response);
    }

    @GetMapping
    public List<SettlementResponse> listSettlements(@PathVariable UUID groupId) {
        return settlementService.listSettlements(groupId);
    }
}

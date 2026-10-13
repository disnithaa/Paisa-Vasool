package com.paisavasool.app.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisavasool.app.api.dto.OfferEligibilityResponse;
import com.paisavasool.app.api.dto.OfferResponse;
import com.paisavasool.app.domain.offer.OfferCatalogService;

@RestController
@RequestMapping("/api/groups/{groupId}")
public class OfferController {

    private final OfferCatalogService offerCatalogService;

    public OfferController(OfferCatalogService offerCatalogService) {
        this.offerCatalogService = offerCatalogService;
    }

    @GetMapping("/offers")
    public List<OfferResponse> listOffers(@PathVariable UUID groupId) {
        return offerCatalogService.listOffers(groupId);
    }

    @GetMapping("/offers/{offerId}")
    public OfferResponse getOffer(@PathVariable UUID groupId, @PathVariable UUID offerId) {
        return offerCatalogService.getOffer(groupId, offerId);
    }

    @GetMapping("/purchases/{purchaseId}/eligible-offers")
    public List<OfferEligibilityResponse> getEligibleOffers(
        @PathVariable UUID groupId,
        @PathVariable UUID purchaseId
    ) {
        return offerCatalogService.getEligibleOffers(groupId, purchaseId);
    }
}

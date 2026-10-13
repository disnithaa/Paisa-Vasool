package com.paisavasool.app.domain.offer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findAllByActiveTrueOrderByMerchantNameAscTitleAsc();

    List<Offer> findAllByActiveTrueAndValidFromLessThanEqualAndValidUntilGreaterThanOrderByMerchantNameAscTitleAsc(
        Instant validFrom,
        Instant validUntil
    );
}

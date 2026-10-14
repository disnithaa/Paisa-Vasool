package com.paisavasool.app.integration.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.paisavasool.app.api.dto.OfferResponse;
import com.paisavasool.app.api.dto.PurchaseOptimizationResponse;
import com.paisavasool.app.domain.offer.OfferType;
import com.paisavasool.app.integration.optimizer.PurchaseOptimizationData.EligibleOffer;
import com.paisavasool.app.integration.optimizer.dto.OptimizerOfferCandidate;
import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;
import com.paisavasool.app.integration.optimizer.dto.OptimizerSolverMetadata;

@ExtendWith(MockitoExtension.class)
class PurchaseOptimizationServiceTest {

    private static final UUID PURCHASE_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID OFFER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID UNKNOWN_OFFER_ID = UUID.fromString(
        "00000000-0000-0000-0000-000000000099"
    );
    private static final Instant EVALUATED_AT = Instant.parse("2026-10-14T00:00:00Z");

    @Mock
    private PurchaseOptimizationDataService dataService;

    @Mock
    private OptimizerClient optimizerClient;

    @Captor
    private ArgumentCaptor<OptimizerRequest> requestCaptor;

    private PurchaseOptimizationService optimizationService;
    private PurchaseOptimizationData optimizationData;

    @BeforeEach
    void setUp() {
        optimizationService = new PurchaseOptimizationService(dataService, optimizerClient);
        OptimizerOfferCandidate candidate = OptimizerOfferCandidate.from(
            OFFER_ID,
            OfferType.PERCENTAGE_DISCOUNT,
            true,
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-11-01T00:00:00Z"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            new BigDecimal("10.00"),
            null
        );
        optimizationData = new PurchaseOptimizationData(
            PURCHASE_ID,
            new BigDecimal("250.00"),
            EVALUATED_AT,
            List.of(new EligibleOffer(
                candidate,
                offerResponse(),
                new BigDecimal("25.00"),
                new BigDecimal("225.00")
            ))
        );
        when(dataService.load(UUID.fromString("20000000-0000-0000-0000-000000000001"), PURCHASE_ID))
            .thenReturn(optimizationData);
    }

    @Test
    void sendsPurchaseAndEligibleOffersAsDecimalStringsAndMapsRecommendation() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250.00",
            OFFER_ID,
            "25.00",
            "225.00",
            "25.00"
        ));

        PurchaseOptimizationResponse result = optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        );

        verify(optimizerClient).optimize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().purchaseAmount()).isEqualTo("250.00");
        assertThat(requestCaptor.getValue().currency()).isEqualTo("INR");
        assertThat(requestCaptor.getValue().evaluatedAt()).isEqualTo(EVALUATED_AT.toString());
        assertThat(requestCaptor.getValue().offers()).hasSize(1);
        assertThat(requestCaptor.getValue().offers().get(0).minimumSpend()).isEqualTo("100.00");
        assertThat(result.purchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(result.selectedOfferId()).isEqualTo(OFFER_ID);
        assertThat(result.selectedOffer().title()).isEqualTo("Sample offer");
        assertThat(result.discountAmount()).isEqualByComparingTo("25.00");
        assertThat(result.finalPayableAmount()).isEqualByComparingTo("225.00");
        assertThat(result.savingsAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void acceptsConsistentBaselineRecommendationWithNoSelectedOffer() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250.00",
            null,
            "0.00",
            "250.00",
            "0.00"
        ));

        PurchaseOptimizationResponse result = optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        );

        assertThat(result.selectedOfferId()).isNull();
        assertThat(result.selectedOffer()).isNull();
        assertThat(result.finalPayableAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void rejectsSelectedOfferNotSuppliedAsEligible() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250.00",
            UNKNOWN_OFFER_ID,
            "25.00",
            "225.00",
            "25.00"
        ));

        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("outside the eligible candidates");
    }

    @Test
    void rejectsCurrencyMismatchAndInconsistentMoney() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "USD",
            "250.00",
            null,
            "0.00",
            "250.00",
            "0.00"
        ));
        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("currency");

        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250.00",
            null,
            "1.00",
            "249.00",
            "0.00"
        ));
        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("do not reconcile");
    }

    @Test
    void rejectsPriceThatDoesNotMatchAuthoritativeJavaCalculation() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250.00",
            OFFER_ID,
            "24.99",
            "225.01",
            "24.99"
        ));

        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("does not match Java offer pricing");
    }

    @Test
    void rejectsNonOptimalStatusAndMalformedDecimalStrings() {
        when(optimizerClient.optimize(any())).thenReturn(response(
            "FEASIBLE",
            "INR",
            "250.00",
            null,
            "0.00",
            "250.00",
            "0.00"
        ));
        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("optimal");

        when(optimizerClient.optimize(any())).thenReturn(response(
            "OPTIMAL",
            "INR",
            "250",
            null,
            "0.00",
            "250.00",
            "0.00"
        ));
        assertThatThrownBy(() -> optimizationService.optimize(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            PURCHASE_ID
        ))
            .isInstanceOf(InvalidOptimizerResponseException.class)
            .hasMessageContaining("two decimals");
    }

    private static OptimizerResponse response(
        String status,
        String currency,
        String baseAmount,
        UUID selectedOfferId,
        String discountAmount,
        String payableAmount,
        String savingsAmount
    ) {
        return new OptimizerResponse(
            status,
            currency,
            baseAmount,
            selectedOfferId,
            discountAmount,
            payableAmount,
            savingsAmount,
            "Recommendation from optimizer.",
            List.of(),
            new OptimizerSolverMetadata("ortools-cp-sat", "OPTIMAL", true)
        );
    }

    private static OfferResponse offerResponse() {
        return new OfferResponse(
            OFFER_ID,
            "Sample merchant",
            "Sample offer",
            "Fictional offer for testing.",
            OfferType.PERCENTAGE_DISCOUNT,
            new BigDecimal("10.00"),
            null,
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-11-01T00:00:00Z"),
            true,
            true,
            "Fictional sample only."
        );
    }
}

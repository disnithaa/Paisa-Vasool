package com.paisavasool.app.integration.optimizer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.paisavasool.app.api.dto.OptimizationSolverResponse;
import com.paisavasool.app.api.dto.PurchaseOptimizationResponse;
import com.paisavasool.app.integration.optimizer.dto.OptimizerRequest;
import com.paisavasool.app.integration.optimizer.dto.OptimizerResponse;

@Service
public class PurchaseOptimizationService {

    private static final String CURRENCY_INR = "INR";
    private static final String OPTIMAL = "OPTIMAL";
    private static final String ALGORITHM = "ortools-cp-sat";
    private static final Pattern MONEY_STRING = Pattern.compile("(?:0|[1-9][0-9]{0,9})\\.[0-9]{2}");

    private final PurchaseOptimizationDataService dataService;
    private final OptimizerClient optimizerClient;

    public PurchaseOptimizationService(
        PurchaseOptimizationDataService dataService,
        OptimizerClient optimizerClient
    ) {
        this.dataService = dataService;
        this.optimizerClient = optimizerClient;
    }

    public PurchaseOptimizationResponse optimize(UUID groupId, UUID purchaseId) {
        PurchaseOptimizationData data = dataService.load(groupId, purchaseId);
        Map<UUID, PurchaseOptimizationData.EligibleOffer> eligibleOffers = data.eligibleOffers()
            .stream()
            .collect(Collectors.toUnmodifiableMap(
                offer -> offer.optimizerCandidate().offerId(),
                Function.identity()
            ));
        OptimizerRequest request = new OptimizerRequest(
            OptimizerRequest.decimalString(data.baseAmount()),
            CURRENCY_INR,
            OptimizerRequest.instantString(data.evaluatedAt()),
            data.eligibleOffers().stream()
                .map(PurchaseOptimizationData.EligibleOffer::optimizerCandidate)
                .toList()
        );
        OptimizerResponse response = optimizerClient.optimize(request);
        return validateAndMap(data, eligibleOffers, response);
    }

    private static PurchaseOptimizationResponse validateAndMap(
        PurchaseOptimizationData data,
        Map<UUID, PurchaseOptimizationData.EligibleOffer> eligibleOffers,
        OptimizerResponse response
    ) {
        if (response == null) {
            throw invalidResponse("Optimizer response is missing");
        }
        if (!OPTIMAL.equals(response.status())) {
            throw invalidResponse("Optimizer did not return an optimal recommendation");
        }
        if (!CURRENCY_INR.equals(response.currency())) {
            throw invalidResponse("Optimizer response currency does not match INR");
        }
        if (!StringUtils.hasText(response.explanation())) {
            throw invalidResponse("Optimizer response explanation is missing");
        }
        if (response.rejectedOffers() == null || !response.rejectedOffers().isEmpty()) {
            throw invalidResponse("Optimizer rejected offers Java marked eligible");
        }
        if (response.solver() == null
            || !ALGORITHM.equals(response.solver().algorithm())
            || !OPTIMAL.equals(response.solver().solverStatus())
            || !Boolean.TRUE.equals(response.solver().baselineAgrees())) {
            throw invalidResponse("Optimizer solver metadata is invalid");
        }

        BigDecimal baseAmount = parseMoney(response.baseAmount(), "base_amount");
        BigDecimal discountAmount = parseMoney(response.discountAmount(), "discount_amount");
        BigDecimal finalPayableAmount = parseMoney(
            response.finalPayableAmount(),
            "final_payable_amount"
        );
        BigDecimal savingsAmount = parseMoney(response.savingsAmount(), "savings_amount");
        if (baseAmount.compareTo(data.baseAmount()) != 0) {
            throw invalidResponse("Optimizer response base amount does not match the purchase");
        }
        if (discountAmount.compareTo(baseAmount) > 0
            || discountAmount.compareTo(savingsAmount) != 0
            || finalPayableAmount.add(savingsAmount).compareTo(baseAmount) != 0) {
            throw invalidResponse("Optimizer response amounts do not reconcile");
        }

        PurchaseOptimizationData.EligibleOffer selectedOffer = null;
        if (response.selectedOfferId() == null) {
            if (discountAmount.signum() != 0 || finalPayableAmount.compareTo(baseAmount) != 0) {
                throw invalidResponse("No-offer recommendation must preserve the base price");
            }
        } else {
            selectedOffer = eligibleOffers.get(response.selectedOfferId());
            if (selectedOffer == null) {
                throw invalidResponse("Optimizer selected an offer outside the eligible candidates");
            }
            if (discountAmount.signum() <= 0
                || discountAmount.compareTo(selectedOffer.expectedDiscount()) != 0
                || finalPayableAmount.compareTo(selectedOffer.expectedFinalAmount()) != 0) {
                throw invalidResponse("Optimizer price does not match Java offer pricing");
            }
        }

        return new PurchaseOptimizationResponse(
            data.purchaseId(),
            response.status(),
            response.currency(),
            baseAmount,
            response.selectedOfferId(),
            selectedOffer == null ? null : selectedOffer.offer(),
            discountAmount,
            finalPayableAmount,
            savingsAmount,
            response.explanation(),
            new OptimizationSolverResponse(
                response.solver().algorithm(),
                response.solver().solverStatus(),
                response.solver().baselineAgrees()
            )
        );
    }

    private static BigDecimal parseMoney(String value, String fieldName) {
        if (value == null || !MONEY_STRING.matcher(value).matches()) {
            throw invalidResponse(fieldName + " must be a non-negative INR amount with two decimals");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new InvalidOptimizerResponseException(fieldName + " is not a valid decimal amount", exception);
        }
    }

    private static InvalidOptimizerResponseException invalidResponse(String message) {
        return new InvalidOptimizerResponseException(message);
    }
}

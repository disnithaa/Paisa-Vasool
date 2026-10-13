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

import com.paisavasool.app.api.dto.CreateExpenseRequest;
import com.paisavasool.app.api.dto.ExpenseResponse;
import com.paisavasool.app.domain.expense.ExpenseService;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
        @PathVariable UUID groupId,
        @Valid @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.createExpense(groupId, request);
        return ResponseEntity.created(URI.create("/api/groups/" + groupId + "/expenses/" + response.id()))
            .body(response);
    }

    @GetMapping
    public List<ExpenseResponse> listExpenses(@PathVariable UUID groupId) {
        return expenseService.listExpenses(groupId);
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse getExpense(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return expenseService.getExpense(groupId, expenseId);
    }
}

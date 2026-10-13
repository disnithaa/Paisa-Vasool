package com.paisavasool.app.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisavasool.app.api.dto.GroupBalancesResponse;
import com.paisavasool.app.domain.expense.GroupBalanceService;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
public class GroupBalanceController {

    private final GroupBalanceService groupBalanceService;

    public GroupBalanceController(GroupBalanceService groupBalanceService) {
        this.groupBalanceService = groupBalanceService;
    }

    @GetMapping
    public GroupBalancesResponse getBalances(@PathVariable UUID groupId) {
        return groupBalanceService.getBalances(groupId);
    }
}

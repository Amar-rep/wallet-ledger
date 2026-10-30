package com.walletledger.walletbackend.controller;

import java.security.Principal;
import java.util.List;

import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.request.TransactionFilter;
import com.walletledger.walletbackend.dto.response.TransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.TransactionSummaryResponse;
import com.walletledger.walletbackend.dto.response.UserStatsResponse;
import com.walletledger.walletbackend.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/mini-statement")
    public List<TransactionSummaryResponse> miniStatement(Principal principal) {
        return transactionHistoryService.miniStatement(principal.getName());
    }

    /** ?fromDate=2026-10-01&toDate=2026-10-29&type=SENT&status=PENDING&page=0&size=10 */
    @GetMapping
    public PageResponse<TransactionSummaryResponse> history(Principal principal,
                                                            @ModelAttribute TransactionFilter filter) {
        return transactionHistoryService.history(principal.getName(), filter);
    }

    @GetMapping("/stats")
    public UserStatsResponse stats(Principal principal) {
        return transactionHistoryService.stats(principal.getName());
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse detail(Principal principal, @PathVariable Long id) {
        return transactionHistoryService.detail(principal.getName(), id);
    }
}

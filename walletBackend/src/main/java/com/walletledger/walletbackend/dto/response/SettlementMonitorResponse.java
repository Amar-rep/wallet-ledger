package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record SettlementMonitorResponse(
        long dueToday,
        long dueTomorrow,
        long overdue,
        BigDecimal totalPendingAmount,
        List<AdminTransactionResponse> pendingTransactions
) {
}

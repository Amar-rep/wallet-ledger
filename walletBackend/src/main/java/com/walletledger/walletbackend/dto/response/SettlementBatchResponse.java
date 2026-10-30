package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;

public record SettlementBatchResponse(
        Long id,
        int settledCount,
        int failedCount,
        int declinedCount,
        BigDecimal totalAmountSettled
) {
}

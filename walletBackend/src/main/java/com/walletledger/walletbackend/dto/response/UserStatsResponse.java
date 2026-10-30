package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;

public record UserStatsResponse(
        BigDecimal totalSent,
        BigDecimal totalReceived,
        long thisMonthTransactionCount
) {
}

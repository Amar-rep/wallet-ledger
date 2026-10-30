package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationResponse(
        LocalDate date,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal pendingSettlementAmount,
        boolean mismatch,
        BigDecimal difference
) {
}

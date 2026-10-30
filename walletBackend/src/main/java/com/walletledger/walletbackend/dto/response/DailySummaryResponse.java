package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySummaryResponse(
        LocalDate summaryDate,
        int transfersInitiated,
        BigDecimal initiatedAmount,
        int transfersSettled,
        BigDecimal settledAmount,
        int fraudAlertsRaised,
        int newRegistrations,
        BigDecimal pendingSettlementAmount,
        boolean reconciliationOk
) {
}

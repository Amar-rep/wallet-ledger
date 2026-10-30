package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;

public record FraudAlertResponse(
        Long id,
        Long userId,
        String userName,
        Long transactionId,
        FraudAlertType alertType,
        BigDecimal amount,
        String description,
        FraudAlertStatus status,
        boolean readOnly,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {
}

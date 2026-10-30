package com.walletledger.walletbackend.dto.response;

import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;

public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationSeverity severity,
        String message,
        Long transactionId,
        boolean read,
        Instant createdAt
) {
}

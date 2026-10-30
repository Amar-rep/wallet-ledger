package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;

public record AdminTransactionResponse(
        Long id,
        String senderName,
        String receiverName,
        BigDecimal amount,
        TransactionStatus status,
        boolean flagged,
        Instant initiatedAt,
        LocalDate settlementDueDate
) {
}

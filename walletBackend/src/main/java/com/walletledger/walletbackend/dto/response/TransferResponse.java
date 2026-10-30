package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;

public record TransferResponse(
        Long transactionId,
        String requestId,
        TransactionStatus status,
        BigDecimal amount,
        String receiverName,
        LocalDate settlementDueDate,
        String message
) {
}

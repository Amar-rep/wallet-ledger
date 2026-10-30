package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;

public record TransactionDetailResponse(
        Long id,
        String requestId,
        TransactionType type,
        TransactionDirection direction,
        String senderName,
        String receiverName,
        BigDecimal amount,
        String remark,
        TransactionStatus status,
        String failureReason,
        Instant initiatedAt,
        LocalDate settlementDueDate,
        Long daysRemaining,
        Instant settledAt,
        List<LedgerEntryResponse> ledgerEntries
) {
}

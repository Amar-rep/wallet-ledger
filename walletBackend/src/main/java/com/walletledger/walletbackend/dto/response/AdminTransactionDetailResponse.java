package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;

public record AdminTransactionDetailResponse(
        Long id,
        String requestId,
        TransactionType type,
        Long senderUserId,
        String senderName,
        Long receiverUserId,
        String receiverName,
        BigDecimal amount,
        String remark,
        TransactionStatus status,
        String failureReason,
        boolean flagged,
        Instant initiatedAt,
        LocalDate settlementDueDate,
        Instant settledAt,
        Long settlementBatchId,
        List<LedgerEntryResponse> ledgerEntries
) {
}

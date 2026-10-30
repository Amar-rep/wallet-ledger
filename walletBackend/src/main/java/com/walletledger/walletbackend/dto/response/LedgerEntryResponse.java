package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.LedgerEntryType;

public record LedgerEntryResponse(
        Long id,
        Long walletId,
        LedgerEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}

package com.walletledger.walletbackend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;

public record AdminTransactionFilter(
        LocalDate fromDate,
        LocalDate toDate,
        TransactionStatusFilter status,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer page,
        Integer size
) {
}

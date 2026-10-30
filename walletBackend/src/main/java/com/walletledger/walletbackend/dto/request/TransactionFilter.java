package com.walletledger.walletbackend.dto.request;

import java.time.LocalDate;

import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;

public record TransactionFilter(
        LocalDate fromDate,
        LocalDate toDate,
        TransactionDirection type,
        TransactionStatusFilter status,
        Integer page,
        Integer size
) {
}

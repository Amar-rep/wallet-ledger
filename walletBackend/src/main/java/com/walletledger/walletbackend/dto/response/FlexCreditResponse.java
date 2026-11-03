package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;

public record FlexCreditResponse(
        Long id,
        Long transactionId,
        BigDecimal amount,
        BigDecimal amountRecovered,
        BigDecimal amountOutstanding,
        FlexCreditStatus status,
        String beneficiaryName,
        Instant disbursedAt,
        Instant settledAt,
        Instant recoveredAt,
        Instant writtenOffAt,
        String writeOffReason,
        String writtenOffBy
) {}

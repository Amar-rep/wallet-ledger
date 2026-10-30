package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;

import com.walletledger.walletbackend.dto.enums.WalletStatus;

public record WalletResponse(
        Long walletId,
        BigDecimal availableBalance,
        BigDecimal todaySpending,
        WalletStatus status
) {
}

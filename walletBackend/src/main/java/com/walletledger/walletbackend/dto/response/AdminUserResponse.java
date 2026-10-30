package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;

public record AdminUserResponse(
        Long userId,
        String name,
        String email,
        String phone,
        UserRole role,
        BigDecimal walletBalance,
        WalletStatus walletStatus,
        Instant registeredAt
) {
}

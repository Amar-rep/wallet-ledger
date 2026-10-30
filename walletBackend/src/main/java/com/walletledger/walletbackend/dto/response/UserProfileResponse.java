package com.walletledger.walletbackend.dto.response;

import com.walletledger.walletbackend.dto.enums.UserRole;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role
) {
}

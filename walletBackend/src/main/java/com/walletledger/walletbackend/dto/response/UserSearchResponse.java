package com.walletledger.walletbackend.dto.response;

public record UserSearchResponse(
        Long userId,
        String name,
        String phone,
        String email
) {
}

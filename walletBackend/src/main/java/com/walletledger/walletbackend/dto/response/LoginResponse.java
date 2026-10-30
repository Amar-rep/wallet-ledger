package com.walletledger.walletbackend.dto.response;

public record LoginResponse(
        String token,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserProfileResponse user
) {
    public String accessToken() {
        return token;
    }
}

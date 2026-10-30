package com.walletledger.walletbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        String testSecret = "0123456789012345678901234567890101234567890123456789012345678901";
        jwtTokenProvider = new JwtTokenProvider(testSecret, 60000, 3600000);

        testUser = new User();
        testUser.setId(101L);
        testUser.setEmail("alice@example.com");
        testUser.setRole(UserRole.USER);
    }

    @Test
    void generatesValidAccessTokenWithCorrectClaims() {
        String accessToken = jwtTokenProvider.generateAccessToken(testUser);

        assertNotNull(accessToken);
        assertTrue(jwtTokenProvider.validateToken(accessToken));
        assertEquals("alice@example.com", jwtTokenProvider.getEmailFromToken(accessToken));
        assertEquals(JwtTokenProvider.TOKEN_TYPE_ACCESS, jwtTokenProvider.getTokenType(accessToken));
        assertEquals(101L, jwtTokenProvider.getUserIdFromToken(accessToken));
    }

    @Test
    void generatesValidRefreshTokenWithCorrectClaims() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
        assertEquals("alice@example.com", jwtTokenProvider.getEmailFromToken(refreshToken));
        assertEquals(JwtTokenProvider.TOKEN_TYPE_REFRESH, jwtTokenProvider.getTokenType(refreshToken));
        assertEquals(101L, jwtTokenProvider.getUserIdFromToken(refreshToken));
    }

    @Test
    void rejectsInvalidOrTamperedToken() {
        String validToken = jwtTokenProvider.generateAccessToken(testUser);
        String tamperedToken = validToken + "tampered";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
        assertFalse(jwtTokenProvider.validateToken("not-a-jwt"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void rejectsExpiredToken() {
        String testSecret = "0123456789012345678901234567890101234567890123456789012345678901";
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(testSecret, -1000, -1000);

        String expiredToken = shortLivedProvider.generateAccessToken(testUser);
        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }
}

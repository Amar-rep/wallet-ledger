package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.request.LoginRequest;
import com.walletledger.walletbackend.dto.request.RefreshTokenRequest;
import com.walletledger.walletbackend.dto.request.RegisterRequest;
import com.walletledger.walletbackend.dto.response.LoginResponse;
import com.walletledger.walletbackend.dto.response.TokenRefreshResponse;
import com.walletledger.walletbackend.dto.response.UserProfileResponse;
import com.walletledger.walletbackend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthControllerTest {

    private AuthService authService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authController = new AuthController(authService);
    }

    @Test
    void registerReturnsCreatedWithTokens() {
        RegisterRequest request = new RegisterRequest("Bob", "bob@example.com", "9876543210", "password123", "1234");
        UserProfileResponse profile = new UserProfileResponse(1L, "Bob", "bob@example.com", "9876543210", UserRole.USER);
        LoginResponse expected = new LoginResponse("access-token", "refresh-token", "Bearer", 900, profile);

        when(authService.register(any(RegisterRequest.class))).thenReturn(expected);

        ResponseEntity<LoginResponse> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().token());
        assertEquals("refresh-token", response.getBody().refreshToken());
    }

    @Test
    void loginReturnsOkWithTokens() {
        LoginRequest request = new LoginRequest("bob@example.com", "password123");
        UserProfileResponse profile = new UserProfileResponse(1L, "Bob", "bob@example.com", "9876543210", UserRole.USER);
        LoginResponse expected = new LoginResponse("access-token", "refresh-token", "Bearer", 900, profile);

        when(authService.login(eq(request), any())).thenReturn(expected);

        ResponseEntity<LoginResponse> response = authController.login(request, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().token());
    }

    @Test
    void refreshReturnsNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        TokenRefreshResponse expected = new TokenRefreshResponse("new-access-token", "new-refresh-token", "Bearer", 900);

        when(authService.refreshToken(request)).thenReturn(expected);

        ResponseEntity<TokenRefreshResponse> response = authController.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("new-access-token", response.getBody().accessToken());
        assertEquals("new-refresh-token", response.getBody().refreshToken());
    }

    @Test
    void meReturnsUserProfile() {
        Principal principal = () -> "bob@example.com";
        UserProfileResponse profile = new UserProfileResponse(1L, "Bob", "bob@example.com", "9876543210", UserRole.USER);

        when(authService.getCurrentUserProfile("bob@example.com")).thenReturn(profile);

        ResponseEntity<UserProfileResponse> response = authController.me(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("bob@example.com", response.getBody().email());
    }
}

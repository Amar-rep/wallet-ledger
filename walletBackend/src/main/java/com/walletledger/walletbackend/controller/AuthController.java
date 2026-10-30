package com.walletledger.walletbackend.controller;

import java.security.Principal;

import com.walletledger.walletbackend.dto.request.LoginRequest;
import com.walletledger.walletbackend.dto.request.RefreshTokenRequest;
import com.walletledger.walletbackend.dto.request.RegisterRequest;
import com.walletledger.walletbackend.dto.response.LoginResponse;
import com.walletledger.walletbackend.dto.response.TokenRefreshResponse;
import com.walletledger.walletbackend.dto.response.UserProfileResponse;
import com.walletledger.walletbackend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Register a new ADMIN account. Requires an existing ADMIN token. */
    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoginResponse> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(Principal principal) {
        UserProfileResponse response = authService.getCurrentUserProfile(principal.getName());
        return ResponseEntity.ok(response);
    }
}

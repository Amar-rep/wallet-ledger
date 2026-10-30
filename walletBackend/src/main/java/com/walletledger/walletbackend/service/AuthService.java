package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.request.LoginRequest;
import com.walletledger.walletbackend.dto.request.RefreshTokenRequest;
import com.walletledger.walletbackend.dto.request.RegisterRequest;
import com.walletledger.walletbackend.dto.response.LoginResponse;
import com.walletledger.walletbackend.dto.response.TokenRefreshResponse;
import com.walletledger.walletbackend.dto.response.UserProfileResponse;
import com.walletledger.walletbackend.entity.LoginAttempt;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.exception.DuplicateResourceException;
import com.walletledger.walletbackend.exception.InvalidTokenException;
import com.walletledger.walletbackend.repository.LoginAttemptRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import com.walletledger.walletbackend.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId INDIA_TIME = ZoneId.of("Asia/Kolkata");
    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCK_MINUTES = 30;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final NotificationService notificationService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        return doRegister(request, UserRole.USER);
    }

    @Transactional
    public LoginResponse registerAdmin(RegisterRequest request) {
        return doRegister(request, UserRole.ADMIN);
    }

    @Transactional
    private LoginResponse doRegister(RegisterRequest request, UserRole role) {
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered: " + email);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPinHash(passwordEncoder.encode(request.pin()));
        user.setRole(role);
        user.setFailedLoginCount(0);
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(savedUser);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setDailySpent(BigDecimal.ZERO);
        wallet.setDailySpentDate(LocalDate.now(INDIA_TIME));
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setCreatedAt(OffsetDateTime.now());
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser);

        return new LoginResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(), mapToUserProfile(savedUser));
    }

    /**
     * Failed-attempt changes intentionally commit before the login error is
     * returned.
     * Otherwise Spring would roll them back along with the rejected login request.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : null;
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            recordLoginAttempt(null, email, false, clientIp);
            throw new BusinessRuleException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (isCurrentlyLocked(user, now)) {
            recordLoginAttempt(user, email, false, clientIp);
            long minutesRemaining = java.time.Duration.between(now, user.getLockedAt().plusMinutes(LOCK_MINUTES))
                    .toMinutes() + 1;
            throw new BusinessRuleException(HttpStatus.LOCKED,
                    "Account is locked. Try again in " + minutesRemaining + " minute(s)");
        }

        if (user.getLockedAt() != null) {
            user.setLockedAt(null);
            user.setFailedLoginCount(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            user.setFailedLoginCount(failed);
            if (failed >= MAX_FAILED_LOGINS) {
                user.setLockedAt(now);
                notificationService.notifyUser(user.getId(), NotificationType.ACCOUNT_LOCKED, NotificationSeverity.INFO,
                        "Your account has been locked after multiple failed login attempts", null);
            }
            userRepository.save(user);
            recordLoginAttempt(user, email, false, clientIp);
            throw new BusinessRuleException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        user.setFailedLoginCount(0);
        user.setLockedAt(null);
        userRepository.save(user);
        recordLoginAttempt(user, email, true, clientIp);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds(), mapToUserProfile(user));
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(refreshToken);
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equalsIgnoreCase(tokenType)) {
            throw new InvalidTokenException("Provided token is not a refresh token");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        if (isCurrentlyLocked(user, OffsetDateTime.now())) {
            throw new BusinessRuleException(HttpStatus.LOCKED, "Account is locked");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new TokenRefreshResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationSeconds());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return mapToUserProfile(user);
    }

    private void recordLoginAttempt(User user, String email, boolean success, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setEmail(email);
        attempt.setSuccess(success);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(OffsetDateTime.now());
        loginAttemptRepository.save(attempt);
    }

    private boolean isCurrentlyLocked(User user, OffsetDateTime now) {
        return user.getLockedAt() != null && user.getLockedAt().plusMinutes(LOCK_MINUTES).isAfter(now);
    }

    private UserProfileResponse mapToUserProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole());
    }
}

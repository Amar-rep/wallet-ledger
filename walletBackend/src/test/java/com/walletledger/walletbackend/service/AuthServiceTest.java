package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.LoginRequest;
import com.walletledger.walletbackend.dto.request.RegisterRequest;
import com.walletledger.walletbackend.dto.response.LoginResponse;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.exception.DuplicateResourceException;
import com.walletledger.walletbackend.exception.InvalidTokenException;
import com.walletledger.walletbackend.repository.LoginAttemptRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import com.walletledger.walletbackend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String JWT_SECRET =
            "0123456789012345678901234567890101234567890123456789012345678901";

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private LoginAttemptRepository loginAttemptRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private NotificationService notificationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository         = mock(UserRepository.class);
        walletRepository       = mock(WalletRepository.class);
        loginAttemptRepository = mock(LoginAttemptRepository.class);
        passwordEncoder        = new BCryptPasswordEncoder();
        notificationService    = mock(NotificationService.class);
        jwtTokenProvider       = new JwtTokenProvider(JWT_SECRET, 900_000L, 604_800_000L);

        authService = new AuthService(
                userRepository, walletRepository, loginAttemptRepository,
                passwordEncoder, jwtTokenProvider, notificationService);

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(1L);
            return u;
        });
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void registerCreatesUserAndWalletWithUserRole() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);

        LoginResponse response = authService.register(validRegisterRequest());

        assertNotNull(response.token());
        assertNotNull(response.refreshToken());
        assertEquals("ada@example.com", response.user().email());
        assertEquals(UserRole.USER, response.user().role());
        verify(walletRepository).save(argThat(w ->
                w.getBalance().compareTo(java.math.BigDecimal.ZERO) == 0
                        && w.getStatus() == WalletStatus.ACTIVE));
    }

    @Test
    void registerAdminCreatesUserWithAdminRole() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);

        LoginResponse response = authService.registerAdmin(validRegisterRequest());

        assertEquals(UserRole.ADMIN, response.user().role());
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(validRegisterRequest()));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void registerThrowsWhenPhoneAlreadyExists() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(validRegisterRequest()));
        verify(walletRepository, never()).save(any());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void loginSucceedsWithValidCredentialsAndResetsFailedCount() {
        User user = savedUser();
        user.setFailedLoginCount(2);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("ada@example.com", "password123"), null);

        assertNotNull(response.token());
        assertEquals(0, user.getFailedLoginCount());
        assertNull(user.getLockedAt());
    }

    @Test
    void loginReturns401WhenUserNotFound() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "pass"), null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void loginReturns401WhenPasswordIsWrong() {
        User user = savedUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "wrong-password"), null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(1, user.getFailedLoginCount());
    }

    @Test
    void loginLocksAccountAfterFiveFailedAttempts() {
        User user = savedUser();
        user.setFailedLoginCount(4); // one more will lock
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "wrong"), null));

        assertNotNull(user.getLockedAt());
        verify(notificationService).notifyUser(
                eq(1L), eq(com.walletledger.walletbackend.dto.enums.NotificationType.ACCOUNT_LOCKED),
                any(), any(), isNull());
    }

    @Test
    void loginReturns423WhenAccountIsLocked() {
        User user = savedUser();
        user.setLockedAt(OffsetDateTime.now().minusMinutes(5)); // locked 5 mins ago
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "password123"), null));
        assertEquals(HttpStatus.LOCKED, ex.getStatus());
    }

    @Test
    void loginSucceedsWhenLockPeriodHasExpired() {
        User user = savedUser();
        user.setLockedAt(OffsetDateTime.now().minusMinutes(31)); // lock expired
        user.setFailedLoginCount(5);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("ada@example.com", "password123"), null);

        assertNotNull(response.token());
        assertNull(user.getLockedAt());
        assertEquals(0, user.getFailedLoginCount());
    }

    // ── Parameterized login failure cases ────────────────────────────────────

    @ParameterizedTest
    @MethodSource("invalidLoginInputs")
    void loginRejectsInvalidInputWithUnauthorized(String email, String password) {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest(email, password), null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    static Stream<Arguments> invalidLoginInputs() {
        return Stream.of(
                Arguments.of("nouser@example.com", "somepass"),
                Arguments.of("ghost@example.com", ""),
                Arguments.of("nobody@x.com", "12345678"));
    }

    // ─── Login with Client IP ──────────────────────────────────────────────────

    @Test
    void loginRecordsClientIpAddress() {
        User user = savedUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        jakarta.servlet.http.HttpServletRequest httpRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        LoginResponse response = authService.login(new LoginRequest("ada@example.com", "password123"), httpRequest);

        assertNotNull(response.token());
        verify(loginAttemptRepository).save(argThat(a -> "192.168.1.100".equals(a.getIpAddress()) && a.getSuccess()));
    }

    // ─── Refresh Token ─────────────────────────────────────────────────────────

    @Test
    void refreshTokenSucceedsForValidRefreshToken() {
        User user = savedUser();
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        com.walletledger.walletbackend.dto.response.TokenRefreshResponse response =
                authService.refreshToken(new com.walletledger.walletbackend.dto.request.RefreshTokenRequest(validRefreshToken));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
    }

    @Test
    void refreshTokenThrowsWhenTokenIsInvalid() {
        InvalidTokenException ex = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(new com.walletledger.walletbackend.dto.request.RefreshTokenRequest("invalid-token")));
        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void refreshTokenThrowsWhenProvidedTokenIsAccessToken() {
        User user = savedUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        InvalidTokenException ex = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(new com.walletledger.walletbackend.dto.request.RefreshTokenRequest(accessToken)));
        assertTrue(ex.getMessage().contains("not a refresh token"));
    }

    @Test
    void refreshTokenThrowsWhenUserNotFound() {
        User user = savedUser();
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> authService.refreshToken(new com.walletledger.walletbackend.dto.request.RefreshTokenRequest(validRefreshToken)));
    }

    @Test
    void refreshTokenThrowsWhenUserIsLocked() {
        User user = savedUser();
        user.setLockedAt(OffsetDateTime.now());
        String validRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.refreshToken(new com.walletledger.walletbackend.dto.request.RefreshTokenRequest(validRefreshToken)));
        assertEquals(HttpStatus.LOCKED, ex.getStatus());
    }

    // ─── Profile ───────────────────────────────────────────────────────────────

    @Test
    void getCurrentUserProfileReturnsProfileWhenUserExists() {
        User user = savedUser();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        com.walletledger.walletbackend.dto.response.UserProfileResponse profile =
                authService.getCurrentUserProfile("ada@example.com");

        assertNotNull(profile);
        assertEquals("ada@example.com", profile.email());
        assertEquals(UserRole.USER, profile.role());
    }

    @Test
    void getCurrentUserProfileThrowsWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> authService.getCurrentUserProfile("unknown@example.com"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RegisterRequest validRegisterRequest() {
        return new RegisterRequest("Ada Lovelace", "ada@example.com", "9876543210", "password123", "1234");
    }

    private User savedUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setPinHash(passwordEncoder.encode("1234"));
        user.setRole(UserRole.USER);
        user.setFailedLoginCount(0);
        return user;
    }
}

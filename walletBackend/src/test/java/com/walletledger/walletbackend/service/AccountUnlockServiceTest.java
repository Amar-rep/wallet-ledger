package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AccountUnlockServiceTest {

    private UserRepository userRepository;
    private NotificationService notificationService;
    private AccountUnlockService accountUnlockService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        accountUnlockService = new AccountUnlockService(userRepository, notificationService);
    }

    @Test
    void unlockExpiredReturnsZeroWhenNoLockedAccounts() {
        when(userRepository.findByLockedAtLessThanEqual(any())).thenReturn(List.of());

        int count = accountUnlockService.unlockExpired();

        assertEquals(0, count);
        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    @Test
    void unlockExpiredClearsLockAndResetsFailedCount() {
        User user = lockedUser(1L, OffsetDateTime.now().minusMinutes(35));
        when(userRepository.findByLockedAtLessThanEqual(any())).thenReturn(List.of(user));

        int count = accountUnlockService.unlockExpired();

        assertEquals(1, count);
        assertEquals(null, user.getLockedAt());
        assertEquals(0, user.getFailedLoginCount());
    }

    @Test
    void unlockExpiredSendsNotificationForEachUnlockedUser() {
        User user1 = lockedUser(1L, OffsetDateTime.now().minusMinutes(35));
        User user2 = lockedUser(2L, OffsetDateTime.now().minusMinutes(60));
        when(userRepository.findByLockedAtLessThanEqual(any())).thenReturn(List.of(user1, user2));

        int count = accountUnlockService.unlockExpired();

        assertEquals(2, count);
        verify(notificationService, times(2)).notifyUser(
                any(), any(), any(), any(), any());
    }

    @Test
    void unlockExpiredNotifiesWithCorrectType() {
        User user = lockedUser(7L, OffsetDateTime.now().minusMinutes(35));
        when(userRepository.findByLockedAtLessThanEqual(any())).thenReturn(List.of(user));

        accountUnlockService.unlockExpired();

        verify(notificationService).notifyUser(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(NotificationType.ACCOUNT_UNLOCKED),
                org.mockito.ArgumentMatchers.eq(NotificationSeverity.INFO),
                any(),
                any());
    }

    // ─── Parameterized: various lock ages ────────────────────────────────────

    @ParameterizedTest
    @MethodSource("expiredLockAges")
    void unlockExpiredHandlesVariousExpiredAges(int minutesAgo) {
        User user = lockedUser(1L, OffsetDateTime.now().minusMinutes(minutesAgo));
        when(userRepository.findByLockedAtLessThanEqual(any())).thenReturn(List.of(user));

        int count = accountUnlockService.unlockExpired();

        assertEquals(1, count);
        assertEquals(null, user.getLockedAt());
    }

    static Stream<Arguments> expiredLockAges() {
        return Stream.of(
                Arguments.of(31),
                Arguments.of(60),
                Arguments.of(120));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static User lockedUser(Long id, OffsetDateTime lockedAt) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setLockedAt(lockedAt);
        user.setFailedLoginCount(5);
        return user;
    }
}

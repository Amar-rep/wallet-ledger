package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.response.NotificationResponse;
import com.walletledger.walletbackend.dto.response.UnreadCountResponse;
import com.walletledger.walletbackend.service.NotificationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationControllerTest {

    private NotificationQueryService notificationQueryService;
    private NotificationController notificationController;
    private Principal principal;

    @BeforeEach
    void setUp() {
        notificationQueryService = mock(NotificationQueryService.class);
        notificationController = new NotificationController(notificationQueryService);
        principal = () -> "ada@example.com";
    }

    @Test
    void listReturnsPagedNotifications() {
        PageResponse<NotificationResponse> expected =
                new PageResponse<>(List.of(notification()), 0, 20, 1L, 1);
        when(notificationQueryService.list("ada@example.com", false, 0, 20)).thenReturn(expected);

        PageResponse<NotificationResponse> actual = notificationController.list(principal, false, 0, 20);

        assertNotNull(actual);
        assertEquals(1, actual.content().size());
    }

    @Test
    void listUnreadOnlyFilters() {
        PageResponse<NotificationResponse> expected =
                new PageResponse<>(List.of(notification()), 0, 20, 1L, 1);
        when(notificationQueryService.list("ada@example.com", true, 0, 20)).thenReturn(expected);

        PageResponse<NotificationResponse> actual = notificationController.list(principal, true, 0, 20);

        assertNotNull(actual);
        verify(notificationQueryService).list("ada@example.com", true, 0, 20);
    }

    @Test
    void unreadCountDelegatesToService() {
        when(notificationQueryService.unreadCount("ada@example.com")).thenReturn(new UnreadCountResponse(3L));

        UnreadCountResponse actual = notificationController.unreadCount(principal);

        assertEquals(3L, actual.unreadCount());
    }

    @Test
    void markReadDelegatesToService() {
        NotificationResponse expected = notification();
        when(notificationQueryService.markRead("ada@example.com", 5L)).thenReturn(expected);

        NotificationResponse actual = notificationController.markRead(principal, 5L);

        assertNotNull(actual);
        verify(notificationQueryService).markRead("ada@example.com", 5L);
    }

    @Test
    void markAllReadDelegatesToService() {
        when(notificationQueryService.markAllRead("ada@example.com"))
                .thenReturn(new MessageResponse("5 notification(s) marked as read"));

        MessageResponse actual = notificationController.markAllRead(principal);

        assertNotNull(actual);
        assertEquals("5 notification(s) marked as read", actual.message());
    }

    // ─── Parameterized: various page/size combos ──────────────────────────────

    @ParameterizedTest
    @MethodSource("pageCombinations")
    void listHandlesVariousPagination(int page, int size) {
        PageResponse<NotificationResponse> expected =
                new PageResponse<>(List.of(), page, size, 0L, 0);
        when(notificationQueryService.list(eq("ada@example.com"), eq(false), eq(page), eq(size)))
                .thenReturn(expected);

        PageResponse<NotificationResponse> actual =
                notificationController.list(principal, false, page, size);

        assertNotNull(actual);
    }

    static Stream<Arguments> pageCombinations() {
        return Stream.of(
                Arguments.of(0, 10),
                Arguments.of(1, 20),
                Arguments.of(0, 100));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static NotificationResponse notification() {
        return new NotificationResponse(1L, NotificationType.TRANSFER_SETTLED,
                NotificationSeverity.INFO, "Transfer settled", null, false, Instant.now());
    }
}

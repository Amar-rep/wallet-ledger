package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.response.NotificationResponse;
import com.walletledger.walletbackend.dto.response.UnreadCountResponse;
import com.walletledger.walletbackend.entity.Notification;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.NotificationRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class NotificationQueryServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationQueryService service;

    private User user;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new NotificationQueryService(notificationRepository, userRepository);

        user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
    }

    @ParameterizedTest
    @MethodSource("listParameters")
    void listReturnsPagedNotifications(boolean unreadOnly, int page, int size) {
        Notification notification = createNotification(10L, user, false);
        Transaction txn = new Transaction();
        txn.setId(100L);
        notification.setTransaction(txn);

        if (unreadOnly) {
            when(notificationRepository.findByUserIdAndReadFalse(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(notification)));
        } else {
            when(notificationRepository.findByUserId(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(notification)));
        }

        PageResponse<NotificationResponse> result = service.list("ada@example.com", unreadOnly, page, size);

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(10L, result.content().get(0).id());
        assertEquals(100L, result.content().get(0).transactionId());
    }

    static Stream<Arguments> listParameters() {
        return Stream.of(
                Arguments.of(false, 0, 20),
                Arguments.of(true, 0, 10),
                Arguments.of(false, -1, 200)); // bounds check
    }

    @Test
    void unreadCountReturnsCountFromRepository() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(7L);

        UnreadCountResponse response = service.unreadCount("ada@example.com");

        assertEquals(7L, response.unreadCount());
    }

    @Test
    void markReadUpdatesReadFlagAndSaves() {
        Notification notification = createNotification(10L, user, false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = service.markRead("ada@example.com", 10L);

        assertTrue(response.read());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markReadThrowsNotFoundWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.markRead("ada@example.com", 99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void markReadRejectsAnotherUsersNotification() {
        User other = new User();
        other.setId(2L);
        Notification notification = createNotification(10L, other, false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.markRead("ada@example.com", 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void markReadThrowsWhenNotificationUserIsNull() {
        Notification notification = createNotification(10L, null, false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.markRead("ada@example.com", 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void markAllReadReturnsMessageResponse() {
        when(notificationRepository.markAllRead(1L)).thenReturn(5);

        MessageResponse response = service.markAllRead("ada@example.com");

        assertEquals("5 notification(s) marked as read", response.message());
        verify(notificationRepository).markAllRead(1L);
    }

    @Test
    void userIdThrowsUnauthorizedWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.unreadCount("unknown@example.com"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    private static Notification createNotification(Long id, User user, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUser(user);
        n.setType(NotificationType.TRANSFER_SETTLED);
        n.setSeverity(NotificationSeverity.INFO);
        n.setMessage("Transfer completed");
        n.setRead(read);
        n.setCreatedAt(OffsetDateTime.now());
        return n;
    }
}

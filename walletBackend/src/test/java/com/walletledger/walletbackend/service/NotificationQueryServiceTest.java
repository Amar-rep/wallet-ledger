package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.walletledger.walletbackend.entity.Notification;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.NotificationRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NotificationQueryServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new NotificationQueryService(notificationRepository, userRepository);
    }

    @Test
    void markReadRejectsAnotherUsersNotification() {
        User owner = new User();
        owner.setId(2L);
        User caller = new User();
        caller.setId(1L);
        caller.setEmail("ada@example.com");
        Notification notification = new Notification();
        notification.setId(9L);
        notification.setUser(owner);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(caller));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class, () -> service.markRead("ada@example.com", 9L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void markReadUpdatesOwnedNotification() {
        User caller = new User();
        caller.setId(1L);
        caller.setEmail("ada@example.com");
        Notification notification = new Notification();
        notification.setId(9L);
        notification.setUser(caller);
        notification.setRead(false);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(caller));
        when(notificationRepository.findById(9L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        assertTrue(service.markRead("ada@example.com", 9L).read());
    }
}

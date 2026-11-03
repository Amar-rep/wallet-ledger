package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.entity.Notification;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.repository.NotificationRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void notifyUserPersistsEveryNotificationType(NotificationType type) {
        NotificationRepository notifications = mock(NotificationRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(7L);
        when(users.getReferenceById(7L)).thenReturn(user);

        new NotificationService(notifications, users).notifyUser(7L, type, NotificationSeverity.INFO, "message", null);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertEquals(type, saved.getValue().getType());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(false, saved.getValue().getRead());
    }

    @ParameterizedTest
    @MethodSource("adminCounts")
    void notifyAdminsCreatesOneNotificationForEachAdmin(int adminCount) {
        NotificationRepository notifications = mock(NotificationRepository.class);
        UserRepository users = mock(UserRepository.class);
        List<User> admins = Stream.iterate(1L, id -> id + 1).limit(adminCount).map(id -> {
            User user = new User(); user.setId(id); return user;
        }).toList();
        when(users.findByRole(UserRole.ADMIN)).thenReturn(admins);
        when(users.getReferenceById(any())).thenAnswer(invocation -> admins.stream()
                .filter(user -> user.getId().equals(invocation.getArgument(0))).findFirst().orElseThrow());

        new NotificationService(notifications, users).notifyAdmins(NotificationType.FRAUD_ALERT,
                NotificationSeverity.CRITICAL, "review", null);

        verify(notifications, org.mockito.Mockito.times(adminCount)).save(any(Notification.class));
    }

    static Stream<Integer> adminCounts() {
        return Stream.of(0, 1, 3);
    }
}

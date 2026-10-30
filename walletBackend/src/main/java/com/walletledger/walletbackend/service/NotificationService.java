package com.walletledger.walletbackend.service;

import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.entity.Notification;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.repository.NotificationRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void notifyUser(Long userId, NotificationType type, NotificationSeverity severity,
                           String message, Transaction transaction) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setSeverity(severity);
        notification.setMessage(message);
        notification.setTransaction(transaction);
        notification.setRead(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);
    }

    public void notifyAdmins(NotificationType type, NotificationSeverity severity,
                             String message, Transaction transaction) {
        userRepository.findByRole(UserRole.ADMIN).forEach(admin ->
                notifyUser(admin.getId(), type, severity, message, transaction));
    }
}

package com.walletledger.walletbackend.service;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.response.NotificationResponse;
import com.walletledger.walletbackend.dto.response.UnreadCountResponse;
import com.walletledger.walletbackend.entity.Notification;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.NotificationRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String email, boolean unreadOnly, int page, int size) {
        Long userId = userId(email);
        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalse(userId, pageRequest)
                : notificationRepository.findByUserId(userId, pageRequest);
        return new PageResponse<>(
                result.map(this::toResponse).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String email) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId(email)));
    }

    @Transactional
    public NotificationResponse markRead(String email, Long notificationId) {
        Long userId = userId(email);
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getUser() == null
                || !notification.getUser().getId().equals(userId)) {
            throw new BusinessRuleException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public MessageResponse markAllRead(String email) {
        int updated = notificationRepository.markAllRead(userId(email));
        return new MessageResponse(updated + " notification(s) marked as read");
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getSeverity(),
                notification.getMessage(),
                notification.getTransaction() == null ? null : notification.getTransaction().getId(),
                Boolean.TRUE.equals(notification.getRead()),
                Optional.ofNullable(notification.getCreatedAt()).map(OffsetDateTime::toInstant).orElse(null));
    }
}

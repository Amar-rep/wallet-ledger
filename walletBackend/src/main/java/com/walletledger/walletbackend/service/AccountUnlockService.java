package com.walletledger.walletbackend.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountUnlockService {

    private static final int LOCK_MINUTES = 30;

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public int unlockExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(LOCK_MINUTES);
        List<User> users = userRepository.findByLockedAtLessThanEqual(cutoff);
        for (User user : users) {
            user.setLockedAt(null);
            user.setFailedLoginCount(0);
            notificationService.notifyUser(
                    user.getId(),
                    NotificationType.ACCOUNT_UNLOCKED,
                    NotificationSeverity.INFO,
                    "Your account has been automatically unlocked",
                    null);
        }
        return users.size();
    }
}

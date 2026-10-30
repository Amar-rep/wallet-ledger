package com.walletledger.walletbackend.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.entity.FraudAlert;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FraudAdminService {

    private final FraudAlertRepository fraudAlertRepository;
    private final UserRepository userRepository;
    private final SettlementService settlementService;

    @Transactional(readOnly = true)
    public List<FraudAlertResponse> list(FraudAlertStatus status) {
        return fraudAlertRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FraudAlertResponse review(String adminEmail, Long alertId, FraudReviewRequest request) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED, "User not found"));
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Fraud alert not found"));

        if (alert.getStatus() != FraudAlertStatus.PENDING) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Fraud alert has already been reviewed");
        }
        if (isReadOnly(alert)) {
            throw new BusinessRuleException(HttpStatus.CONFLICT,
                    "Transaction is already " + alert.getTransaction().getStatus() + "; this alert is read-only");
        }

        if (request.action() == FraudAction.BLOCK) {
            if (alert.getAlertType() != FraudAlertType.HIGH_VALUE || alert.getTransaction() == null) {
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                        "Only a high-value alert linked to a pending transfer can be blocked");
            }
            settlementService.blockByAdmin(alert.getTransaction().getId());
            alert.setStatus(FraudAlertStatus.BLOCKED);
        } else {
            alert.setStatus(FraudAlertStatus.DISMISSED);
        }
        alert.setReviewedBy(admin);
        alert.setReviewedAt(OffsetDateTime.now());
        fraudAlertRepository.save(alert);
        return toResponse(alert);
    }

    private boolean isReadOnly(FraudAlert alert) {
        return alert.getTransaction() != null && alert.getTransaction().getStatus() != TransactionStatus.PENDING;
    }

    private FraudAlertResponse toResponse(FraudAlert alert) {
        return new FraudAlertResponse(alert.getId(), alert.getUser().getId(), alert.getUser().getName(),
                alert.getTransaction() == null ? null : alert.getTransaction().getId(), alert.getAlertType(),
                alert.getAmount(), alert.getDescription(), alert.getStatus(), isReadOnly(alert),
                alert.getReviewedBy() == null ? null : alert.getReviewedBy().getName(),
                alert.getReviewedAt() == null ? null : alert.getReviewedAt().toInstant(),
                alert.getCreatedAt() == null ? null : alert.getCreatedAt().toInstant());
    }
}

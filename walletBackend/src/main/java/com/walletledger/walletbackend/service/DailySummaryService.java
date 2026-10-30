package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.entity.DailySummary;
import com.walletledger.walletbackend.repository.DailySummaryRepository;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailySummaryService {

    private static final ZoneId INDIA_TIME = ZoneId.of("Asia/Kolkata");

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final UserRepository userRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final NotificationService notificationService;

    /** Safe to re-run for the same date: the existing row is overwritten. */
    @Transactional
    public DailySummary generate(LocalDate date) {
        OffsetDateTime from = date.atStartOfDay(INDIA_TIME).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(INDIA_TIME).toOffsetDateTime();
        TransactionType transfer = TransactionType.TRANSFER;
        TransactionStatus success = TransactionStatus.SUCCESS;

        DailySummary summary = dailySummaryRepository.findBySummaryDate(date).orElseGet(DailySummary::new);
        summary.setSummaryDate(date);
        summary.setTransfersInitiated((int) transactionRepository.countInitiated(transfer, from, to));
        summary.setInitiatedAmount(transactionRepository.sumInitiated(transfer, from, to));
        summary.setTransfersSettled((int) transactionRepository.countSettled(transfer, success, from, to));
        summary.setSettledAmount(transactionRepository.sumSettled(transfer, success, from, to));
        summary.setFraudAlertsRaised(
                (int) fraudAlertRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
        summary.setNewRegistrations(
                (int) userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to));
        summary.setPendingSettlementAmount(transactionRepository.sumByStatus(transfer, TransactionStatus.PENDING));

        BigDecimal debits = ledgerEntryRepository.sumForSettled(LedgerEntryType.DEBIT, transfer, success, from, to);
        BigDecimal credits = ledgerEntryRepository.sumForSettled(LedgerEntryType.CREDIT, transfer, success, from, to);
        summary.setTotalDebitsSettled(debits);
        summary.setTotalCreditsSettled(credits);
        boolean match = debits.compareTo(credits) == 0;
        summary.setReconciliationOk(match);
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(OffsetDateTime.now());
        }
        summary = dailySummaryRepository.save(summary);

        if (!match) {
            notificationService.notifyAdmins(
                    NotificationType.RECON_MISMATCH,
                    NotificationSeverity.CRITICAL,
                    "Reconciliation mismatch on " + date + ": debits " + money(debits)
                            + " vs credits " + money(credits),
                    null);
        }
        return summary;
    }

    private String money(BigDecimal amount) {
        return String.format(java.util.Locale.forLanguageTag("en-IN"), "₹%,.2f", amount);
    }
}

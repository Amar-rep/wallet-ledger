package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailySummaryServiceTest {

    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private FraudAlertRepository fraudAlertRepository;
    private UserRepository userRepository;
    private DailySummaryRepository dailySummaryRepository;
    private NotificationService notificationService;
    private DailySummaryService service;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        fraudAlertRepository = mock(FraudAlertRepository.class);
        userRepository = mock(UserRepository.class);
        dailySummaryRepository = mock(DailySummaryRepository.class);
        notificationService = mock(NotificationService.class);
        service = new DailySummaryService(
                transactionRepository, ledgerEntryRepository, fraudAlertRepository,
                userRepository, dailySummaryRepository, notificationService);
        when(dailySummaryRepository.save(any(DailySummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.countInitiated(any(), any(), any())).thenReturn(1L);
        when(transactionRepository.sumInitiated(any(), any(), any())).thenReturn(BigDecimal.TEN);
        when(transactionRepository.countSettled(any(), any(), any(), any())).thenReturn(1L);
        when(transactionRepository.sumSettled(any(), any(), any(), any())).thenReturn(BigDecimal.TEN);
        when(fraudAlertRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(transactionRepository.sumByStatus(any(), any())).thenReturn(BigDecimal.ZERO);
        when(dailySummaryRepository.findBySummaryDate(any())).thenReturn(Optional.empty());
    }

    @Test
    void mismatchNotifiesAdminsAndLeavesTheSummarySuccessful() {
        when(ledgerEntryRepository.sumForSettled(eq(LedgerEntryType.DEBIT), eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("100"));
        when(ledgerEntryRepository.sumForSettled(eq(LedgerEntryType.CREDIT), eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("90"));

        DailySummary summary = service.generate(LocalDate.of(2026, 10, 29));

        assertFalse(summary.getReconciliationOk());
        verify(notificationService).notifyAdmins(
                eq(NotificationType.RECON_MISMATCH),
                eq(NotificationSeverity.CRITICAL),
                any(),
                isNull());
        assertEquals(1, summary.getTransfersInitiated());
    }

    @Test
    void matchingTotalsDoNotNotifyAdmins() {
        when(ledgerEntryRepository.sumForSettled(eq(LedgerEntryType.DEBIT), eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("100"));
        when(ledgerEntryRepository.sumForSettled(eq(LedgerEntryType.CREDIT), eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("100"));

        DailySummary summary = service.generate(LocalDate.of(2026, 10, 29));

        assertEquals(Boolean.TRUE, summary.getReconciliationOk());
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
    }
}

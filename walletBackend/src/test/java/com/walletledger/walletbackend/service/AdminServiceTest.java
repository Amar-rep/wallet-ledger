package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AdminTransactionFilter;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.response.AdminTransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminTransactionResponse;
import com.walletledger.walletbackend.dto.response.AdminUserDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminUserResponse;
import com.walletledger.walletbackend.dto.response.DailySummaryResponse;
import com.walletledger.walletbackend.dto.response.DashboardResponse;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.dto.response.JobRunResponse;
import com.walletledger.walletbackend.dto.response.ReconciliationResponse;
import com.walletledger.walletbackend.dto.response.SettlementMonitorResponse;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.entity.DailySummary;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.AuditLogRepository;
import com.walletledger.walletbackend.repository.DailySummaryRepository;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.ScheduledJobLogRepository;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class AdminServiceTest {

    private FraudAdminService fraudAdminService;
    private WalletService walletService;
    private SettlementService settlementService;
    private TransactionRepository transactionRepository;
    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private ScheduledJobLogRepository scheduledJobLogRepository;
    private DailySummaryRepository dailySummaryRepository;
    private FraudAlertRepository fraudAlertRepository;
    private SettlementBatchRepository settlementBatchRepository;
    private AuditLogRepository auditLogRepository;
    private AdminService adminService;

    private Principal principal;
    private User admin;
    private User ada;
    private Wallet adaWallet;
    private User bob;
    private Wallet bobWallet;

    @BeforeEach
    void setUp() {
        fraudAdminService = mock(FraudAdminService.class);
        walletService = mock(WalletService.class);
        settlementService = mock(SettlementService.class);
        transactionRepository = mock(TransactionRepository.class);
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        scheduledJobLogRepository = mock(ScheduledJobLogRepository.class);
        dailySummaryRepository = mock(DailySummaryRepository.class);
        fraudAlertRepository = mock(FraudAlertRepository.class);
        settlementBatchRepository = mock(SettlementBatchRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);

        adminService = new AdminService(
                fraudAdminService, walletService, settlementService, transactionRepository,
                userRepository, walletRepository, ledgerEntryRepository, scheduledJobLogRepository,
                dailySummaryRepository, fraudAlertRepository, settlementBatchRepository, auditLogRepository);

        principal = () -> "admin@example.com";
        admin = user(99L, "Admin", "admin@example.com", "9000000000", UserRole.ADMIN);
        ada = user(1L, "Ada", "ada@example.com", "9876543210", UserRole.USER);
        bob = user(2L, "Bob", "bob@example.com", "9123456789", UserRole.USER);
        adaWallet = wallet(10L, ada, new BigDecimal("1000.00"));
        bobWallet = wallet(20L, bob, new BigDecimal("250.00"));

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(auditLogRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dashboardAggregatesTodayCountsAndLast30Days() {
        when(userRepository.count()).thenReturn(12L);
        when(transactionRepository.countInitiated(eq(TransactionType.TRANSFER), any(), any())).thenReturn(4L);
        when(transactionRepository.countSettled(eq(TransactionType.TRANSFER), eq(TransactionStatus.SUCCESS), any(),
                any()))
                .thenReturn(3L);
        when(transactionRepository.sumSettled(eq(TransactionType.TRANSFER), eq(TransactionStatus.SUCCESS), any(),
                any()))
                .thenReturn(new BigDecimal("50.00"));
        when(transactionRepository.sumByStatus(TransactionType.TRANSFER, TransactionStatus.PENDING)).thenReturn(null);
        when(fraudAlertRepository.findByStatusOrderByCreatedAtDesc(FraudAlertStatus.PENDING))
                .thenReturn(List.of(mock(com.walletledger.walletbackend.entity.FraudAlert.class),
                        mock(com.walletledger.walletbackend.entity.FraudAlert.class)));

        DashboardResponse result = adminService.dashboard();

        assertEquals(12L, result.totalUsers());
        assertEquals(4L, result.todayTransfersInitiated());
        assertEquals(3L, result.todayTransfersSettled());
        assertEquals(new BigDecimal("50.00"), result.todayVolume());
        assertEquals(BigDecimal.ZERO, result.totalPendingAmount());
        assertEquals(2L, result.pendingFraudAlerts());
        assertEquals(30, result.last30DaysVolume().size());
        assertEquals(WalletService.today(), result.last30DaysVolume().get(29).date());
    }

    @Test
    void usersReturnsPageAndFiltersBySearch() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ada, bob),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 2));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(adaWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.empty());

        PageResponse<AdminUserResponse> filtered = adminService.users("Ada", 0, 20);
        assertEquals(1, filtered.content().size());
        assertEquals("Ada", filtered.content().get(0).name());

        PageResponse<AdminUserResponse> all = adminService.users("  ", 0, 20);
        assertEquals(2, all.content().size());
        assertEquals(WalletStatus.INACTIVE, all.content().get(1).walletStatus());
        assertEquals(BigDecimal.ZERO, all.content().get(1).walletBalance());
    }

    @Test
    void userDetailReturnsRecentTransactionsForSenderAndReceiver() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(adaWallet));

        Transaction sent = transfer(5L, adaWallet, bobWallet, new BigDecimal("10.00"), TransactionStatus.PENDING);
        sent.setSettlementDueDate(WalletService.today().plusDays(2));
        Transaction received = transfer(6L, bobWallet, adaWallet, new BigDecimal("5.00"), TransactionStatus.SUCCESS);
        Transaction topUp = transfer(7L, null, adaWallet, new BigDecimal("20.00"), TransactionStatus.SUCCESS);
        topUp.setType(TransactionType.ADD_MONEY);

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sent, received, topUp)));

        AdminUserDetailResponse detail = adminService.userDetail(1L);

        assertEquals("Ada", detail.user().name());
        assertEquals(3, detail.recentTransactions().size());
        assertEquals(TransactionDirection.SENT, detail.recentTransactions().get(0).direction());
        assertEquals("Bob", detail.recentTransactions().get(0).counterpartyName());
        assertEquals(2L, detail.recentTransactions().get(0).daysRemaining());
        assertEquals(TransactionDirection.RECEIVED, detail.recentTransactions().get(1).direction());
        assertEquals("Top-up", detail.recentTransactions().get(2).counterpartyName());
    }

    @Test
    void userDetailThrowsWhenUserOrWalletMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        BusinessRuleException missingUser = assertThrows(BusinessRuleException.class,
                () -> adminService.userDetail(1L));
        assertEquals(HttpStatus.NOT_FOUND, missingUser.getStatus());

        when(userRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());
        BusinessRuleException missingWallet = assertThrows(BusinessRuleException.class,
                () -> adminService.userDetail(1L));
        assertEquals(HttpStatus.NOT_FOUND, missingWallet.getStatus());
    }

    @Test
    void freezeAndUnfreezeWalletAuditTheAction() {
        MessageResponse frozen = adminService.freezeWallet(principal, 1L, null);
        verify(walletService).setFrozenByAdmin(1L, true);
        assertEquals("Wallet frozen", frozen.message());

        MessageResponse unfrozen = adminService.unfreezeWallet(principal, 1L, null);
        verify(walletService).setFrozenByAdmin(1L, false);
        assertEquals("Wallet unfrozen", unfrozen.message());
        verify(auditLogRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test
    void freezeContinuesWhenAuditPersistenceFails() {
        doThrow(new RuntimeException("db down")).when(auditLogRepository).saveAndFlush(any());

        MessageResponse frozen = adminService.freezeWallet(principal, 1L, null);

        assertEquals("Wallet frozen", frozen.message());
        verify(walletService).setFrozenByAdmin(1L, true);
    }

    @Test
    void transactionsClampsPagingAndMapsSenderlessTransfers() {
        Transaction withSender = transfer(8L, adaWallet, bobWallet, new BigDecimal("15.00"), TransactionStatus.SUCCESS);
        Transaction noSender = transfer(9L, null, bobWallet, new BigDecimal("30.00"), TransactionStatus.PENDING);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withSender, noSender), PageRequest.of(0, 100), 2));

        AdminTransactionFilter filter = new AdminTransactionFilter(
                WalletService.today().minusDays(1), WalletService.today(), TransactionStatusFilter.SUCCESS,
                BigDecimal.ONE, new BigDecimal("100.00"), -3, 1000);

        PageResponse<AdminTransactionResponse> result = adminService.transactions(filter);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(2, result.content().size());
        assertEquals("Ada", result.content().get(0).senderName());
        assertNull(result.content().get(1).senderName());
        assertEquals("Bob", result.content().get(1).receiverName());
    }

    @Test
    void transactionsUsesDefaultPagingWhenFilterValuesAreNull() {
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.transactions(
                new AdminTransactionFilter(null, null, TransactionStatusFilter.ALL, null, null, null, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(20, pageable.getValue().getPageSize());
    }

    @Test
    void transactionDetailMapsLedgerEntriesAndOptionalSender() {
        Transaction txn = transfer(11L, adaWallet, bobWallet, new BigDecimal("40.00"), TransactionStatus.SUCCESS);
        txn.setRequestId("req-11");
        txn.setRemark("rent");
        txn.setFailureReason(null);
        txn.setFlagged(true);
        SettlementBatch batch = new SettlementBatch();
        batch.setId(77L);
        txn.setSettlementBatch(batch);
        txn.setSettledAt(OffsetDateTime.parse("2026-01-01T10:00:00+05:30"));

        LedgerEntry entry = new LedgerEntry();
        entry.setId(3L);
        entry.setWallet(adaWallet);
        entry.setEntryType(LedgerEntryType.DEBIT);
        entry.setAmount(new BigDecimal("40.00"));
        entry.setBalanceAfter(new BigDecimal("960.00"));
        entry.setCreatedAt(OffsetDateTime.parse("2026-01-01T10:00:00+05:30"));

        when(transactionRepository.findById(11L)).thenReturn(Optional.of(txn));
        when(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(entry));

        AdminTransactionDetailResponse detail = adminService.transactionDetail(11L);

        assertEquals("req-11", detail.requestId());
        assertEquals(1L, detail.senderUserId());
        assertTrue(detail.flagged());
        assertEquals(77L, detail.settlementBatchId());
        assertEquals(1, detail.ledgerEntries().size());
        assertEquals(10L, detail.ledgerEntries().get(0).walletId());
    }

    @Test
    void transactionDetailThrowsWhenMissing() {
        when(transactionRepository.findById(404L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> adminService.transactionDetail(404L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void settlementsGroupsDueTodayTomorrowAndOverdue() {
        LocalDate today = WalletService.today();
        Transaction dueToday = transfer(1L, adaWallet, bobWallet, new BigDecimal("10.00"), TransactionStatus.PENDING);
        dueToday.setSettlementDueDate(today);
        Transaction dueTomorrow = transfer(2L, adaWallet, bobWallet, new BigDecimal("20.00"),
                TransactionStatus.PENDING);
        dueTomorrow.setSettlementDueDate(today.plusDays(1));
        Transaction overdue = transfer(3L, adaWallet, bobWallet, new BigDecimal("30.00"), TransactionStatus.PENDING);
        overdue.setSettlementDueDate(today.minusDays(2));
        Transaction noDue = transfer(4L, null, bobWallet, new BigDecimal("5.00"), TransactionStatus.PENDING);
        noDue.setSettlementDueDate(null);

        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(dueToday, dueTomorrow, overdue, noDue));

        SettlementMonitorResponse result = adminService.settlements();

        assertEquals(1L, result.dueToday());
        assertEquals(1L, result.dueTomorrow());
        assertEquals(1L, result.overdue());
        assertEquals(new BigDecimal("65.00"), result.totalPendingAmount());
        assertEquals(4, result.pendingTransactions().size());
        assertEquals(overdue.getId(), result.pendingTransactions().get(0).id());
        assertNull(result.pendingTransactions().get(3).senderName());
    }

    @ParameterizedTest
    @MethodSource("settleOutcomes")
    void manualSettleMapsOutcomeToMessage(SettlementOutcome outcome, String expected) {
        when(settlementService.settle(12L, null)).thenReturn(outcome);

        MessageResponse response = adminService.manualSettle(principal, 12L);

        assertEquals(expected, response.message());
        verify(settlementService).settle(12L, null);
        verify(auditLogRepository).saveAndFlush(any());
    }

    static Stream<Arguments> settleOutcomes() {
        return Stream.of(
                Arguments.of(SettlementOutcome.SETTLED, "Transaction settled successfully"),
                Arguments.of(SettlementOutcome.DECLINED, "Settlement declined: receiver wallet is not active"),
                Arguments.of(SettlementOutcome.SKIPPED, "Transaction was already processed"));
    }

    @ParameterizedTest
    @EnumSource(FraudAlertStatus.class)
    void fraudAlertsDelegateToFraudAdminService(FraudAlertStatus status) {
        when(fraudAdminService.list(status)).thenReturn(List.of());

        assertEquals(List.of(), adminService.fraudAlerts(status));
        verify(fraudAdminService).list(status);
    }

    @Test
    void reviewFraudAlertDelegatesAndAudits() {
        FraudReviewRequest request = new FraudReviewRequest(FraudAction.BLOCK, "block it");
        FraudAlertResponse expected = new FraudAlertResponse(
                1L, 2L, "Ada", 3L, FraudAlertType.HIGH_VALUE, BigDecimal.TEN, "desc",
                FraudAlertStatus.BLOCKED, true, "admin@example.com", null, null);
        when(fraudAdminService.review("admin@example.com", 1L, request)).thenReturn(expected);

        FraudAlertResponse actual = adminService.reviewFraudAlert(principal, 1L, request);

        assertEquals(expected, actual);
        verify(auditLogRepository).saveAndFlush(any());
    }

    @Test
    void jobsMapsLogsAndOptionalSettlementBatches() {
        ScheduledJobLog withBatch = jobLog(1L, "SETTLEMENT");
        ScheduledJobLog withoutBatch = jobLog(2L, "SUMMARY");
        ScheduledJobLog other = jobLog(3L, "UNLOCK");
        when(scheduledJobLogRepository.findAll(any(Sort.class))).thenReturn(List.of(withBatch, withoutBatch, other));

        SettlementBatch batch = new SettlementBatch();
        batch.setId(50L);
        batch.setSettledCount(null);
        batch.setFailedCount(null);
        batch.setDeclinedCount(null);
        batch.setTotalAmountSettled(null);
        when(settlementBatchRepository.findByJobLogId(1L)).thenReturn(Optional.of(batch));
        when(settlementBatchRepository.findByJobLogId(2L)).thenReturn(Optional.empty());
        when(settlementBatchRepository.findByJobLogId(3L)).thenReturn(Optional.empty());

        List<JobRunResponse> all = adminService.jobs(null);
        assertEquals(3, all.size());
        assertEquals(0, all.get(0).settlementBatch().settledCount());
        assertNull(all.get(1).settlementBatch());

        List<JobRunResponse> filtered = adminService.jobs("SETTLEMENT");
        assertEquals(1, filtered.size());
        assertEquals("SETTLEMENT", filtered.get(0).jobName());
        verify(settlementService, never()).settle(any(), any());
    }

    @Test
    void reconciliationReportsMismatchAgainstTheRequestedDate() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        when(ledgerEntryRepository.sumForSettled(eq(LedgerEntryType.DEBIT), eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("20.00"));
        when(ledgerEntryRepository.sumCreditSideForSettled(eq(TransactionType.TRANSFER),
                eq(TransactionStatus.SUCCESS), any(), any())).thenReturn(new BigDecimal("15.00"));
        when(transactionRepository.sumByStatus(TransactionType.TRANSFER, TransactionStatus.PENDING))
                .thenReturn(new BigDecimal("4.00"));

        ReconciliationResponse result = adminService.reconciliation(date);

        assertEquals(date, result.date());
        assertTrue(result.mismatch());
        assertEquals(new BigDecimal("5.00"), result.difference());
        assertEquals(new BigDecimal("4.00"), result.pendingSettlementAmount());
    }

    @Test
    void reconciliationUsesTodayAndTreatsNullSumsAsZero() {
        when(ledgerEntryRepository.sumForSettled(any(), any(), any(), any(), any())).thenReturn(null);
        when(ledgerEntryRepository.sumCreditSideForSettled(any(), any(), any(), any())).thenReturn(null);
        when(transactionRepository.sumByStatus(any(), any())).thenReturn(null);

        ReconciliationResponse result = adminService.reconciliation(null);

        assertEquals(WalletService.today(), result.date());
        assertFalse(result.mismatch());
        assertEquals(BigDecimal.ZERO, result.difference());
    }

    // ── dailySummaries ────────────────────────────────────────────────────────

    @Test
    void dailySummariesMapsPopulatedRowCorrectly() {
        DailySummary s = dailySummary(LocalDate.of(2026, 11, 1),
                5, new BigDecimal("1000.00"),
                4, new BigDecimal("900.00"),
                2, 3,
                new BigDecimal("100.00"), true);
        when(dailySummaryRepository.findAll(any(Sort.class))).thenReturn(List.of(s));

        List<DailySummaryResponse> result = adminService.dailySummaries(10);

        assertEquals(1, result.size());
        DailySummaryResponse r = result.get(0);
        assertEquals(LocalDate.of(2026, 11, 1), r.summaryDate());
        assertEquals(5, r.transfersInitiated());
        assertEquals(new BigDecimal("1000.00"), r.initiatedAmount());
        assertEquals(4, r.transfersSettled());
        assertEquals(new BigDecimal("900.00"), r.settledAmount());
        assertEquals(2, r.fraudAlertsRaised());
        assertEquals(3, r.newRegistrations());
        assertEquals(new BigDecimal("100.00"), r.pendingSettlementAmount());
        assertTrue(r.reconciliationOk());
    }

    @Test
    void dailySummariesMapsNullFieldsToDefaults() {
        DailySummary s = new DailySummary();
        s.setSummaryDate(LocalDate.of(2026, 10, 31));
        // All counts/amounts intentionally left null
        when(dailySummaryRepository.findAll(any(Sort.class))).thenReturn(List.of(s));

        List<DailySummaryResponse> result = adminService.dailySummaries(5);

        assertEquals(1, result.size());
        DailySummaryResponse r = result.get(0);
        assertEquals(0, r.transfersInitiated());
        assertEquals(BigDecimal.ZERO, r.initiatedAmount());
        assertEquals(0, r.transfersSettled());
        assertEquals(BigDecimal.ZERO, r.settledAmount());
        assertEquals(0, r.fraudAlertsRaised());
        assertEquals(0, r.newRegistrations());
        assertEquals(BigDecimal.ZERO, r.pendingSettlementAmount());
        assertFalse(r.reconciliationOk());
    }

    @Test
    void dailySummariesClampsLimitToMaxOf90() {
        List<DailySummary> hundred = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            DailySummary s = dailySummary(LocalDate.now().minusDays(i),
                    1, BigDecimal.ONE, 1, BigDecimal.ONE, 0, 0, BigDecimal.ZERO, true);
            hundred.add(s);
        }
        when(dailySummaryRepository.findAll(any(Sort.class))).thenReturn(hundred);

        List<DailySummaryResponse> result = adminService.dailySummaries(200);

        assertEquals(90, result.size());
    }

    @Test
    void dailySummariesClampsLimitToMinOf1() {
        DailySummary s = dailySummary(LocalDate.now(), 1, BigDecimal.ONE, 1, BigDecimal.ONE, 0, 0, BigDecimal.ZERO,
                true);
        when(dailySummaryRepository.findAll(any(Sort.class))).thenReturn(List.of(s));

        List<DailySummaryResponse> result = adminService.dailySummaries(0);

        assertEquals(1, result.size());
    }

    @Test
    void dailySummariesReturnsEmptyWhenNoRows() {
        when(dailySummaryRepository.findAll(any(Sort.class))).thenReturn(List.of());

        List<DailySummaryResponse> result = adminService.dailySummaries(30);

        assertTrue(result.isEmpty());
    }

    // ── users — search by email and phone ────────────────────────────────────

    @Test
    void usersFiltersMatchesByEmail() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ada, bob),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 2));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(adaWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(bobWallet));

        PageResponse<AdminUserResponse> result = adminService.users("ada@example.com", 0, 20);

        assertEquals(1, result.content().size());
        assertEquals("Ada", result.content().get(0).name());
    }

    @Test
    void usersFiltersMatchesByPhone() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ada, bob),
                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 2));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(adaWallet));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(bobWallet));

        PageResponse<AdminUserResponse> result = adminService.users("9876543210", 0, 20);

        assertEquals(1, result.content().size());
        assertEquals("Ada", result.content().get(0).name());
    }

    // ── jobs — non-null recordsProcessed ─────────────────────────────────────

    @Test
    void jobsMapsNonNullRecordsProcessed() {
        ScheduledJobLog log = jobLog(1L, "SETTLEMENT");
        log.setRecordsProcessed(7);
        when(scheduledJobLogRepository.findAll(any(Sort.class))).thenReturn(List.of(log));
        when(settlementBatchRepository.findByJobLogId(1L)).thenReturn(Optional.empty());

        List<JobRunResponse> result = adminService.jobs(null);

        assertEquals(7, result.get(0).recordsProcessed());
    }

    // ── audit — null principal does not look up user ─────────────────────────

    @Test
    void freezeWalletWithNullPrincipalDoesNotThrow() {
        // audit() swallows exceptions, but also gracefully handles null principal
        MessageResponse response = adminService.freezeWallet(null, 1L, null);

        assertEquals("Wallet frozen", response.message());
        verify(walletService).setFrozenByAdmin(1L, true);
        // userRepository.findByEmail must never be called (no principal to look up)
        verify(userRepository, never()).findByEmail(any());
    }

    private static DailySummary dailySummary(LocalDate date,
            int initiated, BigDecimal initiatedAmt,
            int settled, BigDecimal settledAmt,
            int fraud, int newUsers,
            BigDecimal pending, boolean reconOk) {
        DailySummary s = new DailySummary();
        s.setSummaryDate(date);
        s.setTransfersInitiated(initiated);
        s.setInitiatedAmount(initiatedAmt);
        s.setTransfersSettled(settled);
        s.setSettledAmount(settledAmt);
        s.setFraudAlertsRaised(fraud);
        s.setNewRegistrations(newUsers);
        s.setPendingSettlementAmount(pending);
        s.setReconciliationOk(reconOk);
        return s;
    }

    private static User user(Long id, String name, String email, String phone, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        user.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00+05:30"));
        return user;
    }

    private static Wallet wallet(Long id, User user, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setBalance(balance);
        wallet.setStatus(WalletStatus.ACTIVE);
        return wallet;
    }

    private static Transaction transfer(Long id, Wallet sender, Wallet receiver, BigDecimal amount,
            TransactionStatus status) {
        Transaction txn = new Transaction();
        txn.setId(id);
        txn.setType(TransactionType.TRANSFER);
        txn.setSenderWallet(sender);
        txn.setReceiverWallet(receiver);
        txn.setAmount(amount);
        txn.setStatus(status);
        txn.setFlagged(false);
        txn.setInitiatedAt(OffsetDateTime.parse("2026-01-01T09:00:00+05:30"));
        txn.setSettlementDueDate(WalletService.today());
        return txn;
    }

    private static ScheduledJobLog jobLog(Long id, String name) {
        ScheduledJobLog log = new ScheduledJobLog();
        log.setId(id);
        log.setJobName(name);
        log.setStartedAt(OffsetDateTime.parse("2026-01-01T08:00:00+05:30"));
        log.setEndedAt(OffsetDateTime.parse("2026-01-01T08:01:00+05:30"));
        log.setStatus(ScheduledJobStatus.SUCCESS);
        log.setRecordsProcessed(null);
        return log;
    }
}

package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.config.FlexProperties;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class SettlementServiceTest {

    private TransactionRepository transactionRepository;
    private WalletRepository walletRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private NotificationService notificationService;
    private SettlementBatchRepository settlementBatchRepository;
    private WalletLockService walletLockService;
    private FlexService flexService;
    private FlexProperties flexProperties;
    private SettlementService settlementService;

    private Transaction pendingTxn;
    private Wallet sender;
    private Wallet receiver;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        walletRepository = mock(WalletRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        notificationService = mock(NotificationService.class);
        settlementBatchRepository = mock(SettlementBatchRepository.class);
        walletLockService = new WalletLockService(walletRepository);
        flexService = mock(FlexService.class);
        flexProperties = new FlexProperties(); // Flex disabled

        settlementService = new SettlementService(
                transactionRepository, walletRepository, ledgerEntryRepository,
                notificationService, settlementBatchRepository, walletLockService,
                flexService, flexProperties);

        sender = wallet(1L, user(10L, "Amarnath"), new BigDecimal("50.00"));
        receiver = wallet(2L, user(20L, "Sam"), new BigDecimal("100.00"));

        pendingTxn = transaction(99L, sender, receiver, new BigDecimal("25.00"), TransactionStatus.PENDING);

        when(transactionRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(pendingTxn));
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiver));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── settle — happy path ───────────────────────────────────────────────────

    @Test
    void settleCreditsReceiverAndMarksTransactionSuccess() {
        SettlementOutcome outcome = settlementService.settle(99L, null);

        assertEquals(SettlementOutcome.SETTLED, outcome);
        assertEquals(TransactionStatus.SUCCESS, pendingTxn.getStatus());
        assertEquals(new BigDecimal("125.00"), receiver.getBalance());
        assertNotNull(pendingTxn.getSettledAt());
    }

    @Test
    void settleSkipsAlreadyProcessedTransactions() {
        pendingTxn.setStatus(TransactionStatus.SUCCESS);

        assertEquals(SettlementOutcome.SKIPPED, settlementService.settle(99L, null));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void settleAttachesBatchBeforeCreditingReceiver() {
        SettlementBatch batch = new SettlementBatch();
        batch.setId(77L);
        when(settlementBatchRepository.getReferenceById(77L)).thenReturn(batch);

        settlementService.settle(99L, 77L);

        assertEquals(batch, pendingTxn.getSettlementBatch());
    }

    @Test
    void settleNotifiesBothPartiesOnSuccess() {
        settlementService.settle(99L, null);

        verify(notificationService).notifyUser(eq(10L), any(), any(), any(), eq(pendingTxn));
        verify(notificationService).notifyUser(eq(20L), any(), any(), any(), eq(pendingTxn));
    }

    // ── settle — declined ─────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = { "INACTIVE", "FROZEN" })
    void settleDeclinesWhenReceiverWalletIsNotActive(WalletStatus status) {
        receiver.setStatus(status);

        SettlementOutcome outcome = settlementService.settle(99L, null);

        assertEquals(SettlementOutcome.DECLINED, outcome);
        assertEquals(TransactionStatus.DECLINED, pendingTxn.getStatus());
        // Sender is refunded
        assertEquals(new BigDecimal("75.00"), sender.getBalance());
    }

    @Test
    void settleDeclinesNotifiesSenderAboutRefund() {
        receiver.setStatus(WalletStatus.FROZEN);

        settlementService.settle(99L, null);

        verify(notificationService).notifyUser(eq(10L), any(), any(), any(), eq(pendingTxn));
    }

    // ── settle — Flex integration ─────────────────────────────────────────────

    @Test
    void settleRepaysSystemWalletWhenFlexWasUsed() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("900.00"));
        systemWallet.setWalletKind(WalletKind.SYSTEM);
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));
        when(flexService.repayOnSettlement(eq(pendingTxn), eq(systemWallet))).thenReturn(true);

        SettlementOutcome outcome = settlementService.settle(99L, null);

        assertEquals(SettlementOutcome.SETTLED, outcome);
        // receiver is NOT credited directly since Flex handled it
        assertEquals(new BigDecimal("100.00"), receiver.getBalance());
        verify(flexService).repayOnSettlement(eq(pendingTxn), eq(systemWallet));
    }

    @Test
    void settleCreditsReceiverNormallyWhenFlexWasNotUsed() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("900.00"));
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));
        when(flexService.repayOnSettlement(any(), any())).thenReturn(false);
        when(flexService.repayDebtFromInbound(eq(receiver), any(), any()))
                .thenReturn(pendingTxn.getAmount()); // no debt

        settlementService.settle(99L, null);

        assertEquals(new BigDecimal("125.00"), receiver.getBalance());
    }

    // ── fail ──────────────────────────────────────────────────────────────────

    @Test
    void failRefundsSenderAndMarksTransactionFailed() {
        settlementService.fail(99L, "System error", null);

        assertEquals(TransactionStatus.FAILED, pendingTxn.getStatus());
        assertEquals("System error", pendingTxn.getFailureReason());
        assertEquals(new BigDecimal("75.00"), sender.getBalance());
    }

    @Test
    void failIsIdempotentForAlreadyClosedTransactions() {
        pendingTxn.setStatus(TransactionStatus.FAILED);

        settlementService.fail(99L, "re-try", null);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void failCallsFlexRecoveryWhenEnabled() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("500.00"));
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));

        settlementService.fail(99L, "error", null);

        verify(flexService).recoverOnFailure(eq(pendingTxn), eq(receiver), eq(systemWallet));
    }

    @Test
    void failAttachesSettlementBatchWhenProvided() {
        SettlementBatch batch = new SettlementBatch();
        batch.setId(10L);
        when(settlementBatchRepository.getReferenceById(10L)).thenReturn(batch);

        settlementService.fail(99L, "error with batch", 10L);

        assertEquals(batch, pendingTxn.getSettlementBatch());
    }

    // ── blockByAdmin ──────────────────────────────────────────────────────────

    @Test
    void blockByAdminRefundsSenderAndMarksTransactionFailed() {
        settlementService.blockByAdmin(99L);

        assertEquals(TransactionStatus.FAILED, pendingTxn.getStatus());
        assertEquals("Blocked by admin", pendingTxn.getFailureReason());
        assertEquals(new BigDecimal("75.00"), sender.getBalance());
    }

    @Test
    void blockByAdminThrowsConflictForNonPendingTransaction() {
        pendingTxn.setStatus(TransactionStatus.SUCCESS);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> settlementService.blockByAdmin(99L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void blockByAdminNotifiesReceiverAboutCancellation() {
        settlementService.blockByAdmin(99L);

        verify(notificationService).notifyUser(eq(20L), any(), any(), any(), eq(pendingTxn));
    }

    // ── Parameterized: all non-pending statuses are skipped by settle ─────────

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = { "SUCCESS", "FAILED", "DECLINED" })
    void settleSkipsAllTerminalStatuses(TransactionStatus status) {
        pendingTxn.setStatus(status);

        assertEquals(SettlementOutcome.SKIPPED, settlementService.settle(99L, null));
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void settleDeclinesAndCallsFlexRecoveryWhenFlexEnabledAndReceiverInactive() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("900.00"));
        systemWallet.setWalletKind(WalletKind.SYSTEM);
        receiver.setStatus(WalletStatus.FROZEN);
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));

        SettlementOutcome outcome = settlementService.settle(99L, null);

        assertEquals(SettlementOutcome.DECLINED, outcome);
        verify(flexService).recoverOnFailure(eq(pendingTxn), eq(receiver), eq(systemWallet));
        assertEquals(new BigDecimal("75.00"), sender.getBalance()); // refunded
    }

    @Test
    void creditReceiverAppliesPartialDebtRepayment() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("900.00"));
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));
        when(flexService.repayOnSettlement(any(), any())).thenReturn(false);
        // Receiver has ₹10 debt — repayDebtFromInbound returns net = 25 - 10 = 15
        when(flexService.repayDebtFromInbound(eq(receiver), eq(new BigDecimal("25.00")), eq(pendingTxn)))
                .thenReturn(new BigDecimal("15.00"));

        settlementService.settle(99L, null);

        // Receiver started at 100, gets 15 credited (not 25)
        assertEquals(new BigDecimal("115.00"), receiver.getBalance());
    }

    @Test
    void refundAndCloseDoesNotDecrementDailySpentWhenInitiatedOnDifferentDay() {
        // Transaction initiated yesterday — daily-spent rollback should NOT fire
        pendingTxn.setInitiatedAt(OffsetDateTime.now().minusDays(1));
        sender.setDailySpent(new BigDecimal("50.00"));
        sender.setDailySpentDate(WalletService.today());

        settlementService.fail(99L, "System error", null);

        // dailySpent is unchanged because initiatedAt is a different day
        assertEquals(new BigDecimal("50.00"), sender.getDailySpent());
        assertEquals(TransactionStatus.FAILED, pendingTxn.getStatus());
    }

    @Test
    void settleThrowsNotFoundWhenTransactionMissing() {
        when(transactionRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> settlementService.settle(404L, null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void blockByAdminCallsFlexRecoveryWhenFlexEnabled() {
        flexProperties.setEnabled(true);
        flexProperties.setSystemWalletId(99L);
        Wallet systemWallet = wallet(99L, null, new BigDecimal("900.00"));
        systemWallet.setWalletKind(WalletKind.SYSTEM);
        when(walletRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(systemWallet));

        settlementService.blockByAdmin(99L);

        verify(flexService).recoverOnFailure(eq(pendingTxn), eq(receiver), eq(systemWallet));
        assertEquals(TransactionStatus.FAILED, pendingTxn.getStatus());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Transaction transaction(Long id, Wallet sender, Wallet receiver,
            BigDecimal amount, TransactionStatus status) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setType(TransactionType.TRANSFER);
        t.setSenderWallet(sender);
        t.setReceiverWallet(receiver);
        t.setAmount(amount);
        t.setStatus(status);
        t.setInitiatedAt(OffsetDateTime.now());
        return t;
    }

    private static Wallet wallet(Long id, User user, BigDecimal balance) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setUser(user);
        w.setBalance(balance);
        w.setDailySpent(BigDecimal.ZERO);
        w.setDailySpentDate(WalletService.today());
        w.setStatus(WalletStatus.ACTIVE);
        w.setWalletKind(WalletKind.CUSTOMER);
        w.setFlexDebt(BigDecimal.ZERO);
        return w;
    }

    private static User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }
}

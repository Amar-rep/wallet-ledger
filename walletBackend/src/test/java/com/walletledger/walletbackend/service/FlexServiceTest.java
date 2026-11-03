package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.config.FlexProperties;
import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FlexCreditRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlexServiceTest {
    private FlexProperties properties;
    private FlexCreditRepository flexCredits;
    private LedgerEntryRepository ledgerEntries;
    private WalletRepository wallets;
    private WalletLockService locks;
    private NotificationService notifications;
    private FlexService service;

    @BeforeEach
    void setUp() {
        properties = new FlexProperties();
        properties.setEnabled(true);
        properties.setSystemWalletId(99L);
        properties.setMaxTransferAmount(new BigDecimal("500.00"));
        flexCredits = mock(FlexCreditRepository.class);
        ledgerEntries = mock(LedgerEntryRepository.class);
        wallets = mock(WalletRepository.class);
        locks = mock(WalletLockService.class);
        notifications = mock(NotificationService.class);
        service = new FlexService(properties, flexCredits, ledgerEntries, wallets, locks, notifications);
    }

    @ParameterizedTest
    @MethodSource("ineligibleCases")
    void isEligibleRejectsTransfersThatBreakAnEligibilityRule(boolean enabled, boolean optedIn,
            WalletKind walletKind, BigDecimal debt, BigDecimal amount, boolean flagged,
            boolean allowFlagged) {
        properties.setEnabled(enabled);
        properties.setAllowFlaggedTransfers(allowFlagged);
        Wallet receiver = wallet(2L, new BigDecimal("100.00"));
        receiver.setFlexEnabled(optedIn);
        receiver.setWalletKind(walletKind);
        receiver.setFlexDebt(debt);
        when(wallets.findById(99L)).thenReturn(Optional.of(wallet(99L, new BigDecimal("1000.00"))));

        assertFalse(service.isEligible(receiver, amount, flagged));
    }

    static Stream<Arguments> ineligibleCases() {
        return Stream.of(
                Arguments.of(false, true, WalletKind.CUSTOMER, BigDecimal.ZERO, new BigDecimal("50"), false, false),
                Arguments.of(true, false, WalletKind.CUSTOMER, BigDecimal.ZERO, new BigDecimal("50"), false, false),
                Arguments.of(true, true, WalletKind.SYSTEM, BigDecimal.ZERO, new BigDecimal("50"), false, false),
                Arguments.of(true, true, WalletKind.CUSTOMER, BigDecimal.ONE, new BigDecimal("50"), false, false),
                Arguments.of(true, true, WalletKind.CUSTOMER, BigDecimal.ZERO, new BigDecimal("501"), false, false),
                Arguments.of(true, true, WalletKind.CUSTOMER, BigDecimal.ZERO, new BigDecimal("50"), true, false));
    }

    @Test
    void isEligibleAcceptsAnOptedInCustomerWithinLimitsAndWithSystemFloat() {
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(true);
        receiver.setWalletKind(WalletKind.CUSTOMER);
        receiver.setFlexDebt(BigDecimal.ZERO);
        when(wallets.findById(99L)).thenReturn(Optional.of(wallet(99L, new BigDecimal("100.00"))));

        assertTrue(service.isEligible(receiver, new BigDecimal("100.00"), false));
    }

    @Test
    void disburseMovesFloatToReceiverAndCreatesOpenCredit() {
        Transaction transaction = transaction(10L, new BigDecimal("75.00"));
        Wallet sender = wallet(1L, new BigDecimal("200.00"));
        Wallet receiver = wallet(2L, new BigDecimal("25.00"));
        receiver.setFlexEnabled(true);
        receiver.setFlexDebt(BigDecimal.ZERO);
        Wallet system = wallet(99L, new BigDecimal("500.00"));
        when(locks.lockInOrder(1L, 2L, 99L)).thenReturn(Map.of(1L, sender, 2L, receiver, 99L, system));

        service.disburse(transaction, sender, receiver);

        assertEquals(new BigDecimal("425.00"), system.getBalance());
        assertEquals(new BigDecimal("100.00"), receiver.getBalance());
        verify(flexCredits).save(any(FlexCredit.class));
        verify(ledgerEntries, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void disburseDoesNothingWhenEligibilityIsLostWhileLocked() {
        Transaction transaction = transaction(10L, new BigDecimal("75.00"));
        Wallet sender = wallet(1L, new BigDecimal("200.00"));
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(false);
        Wallet system = wallet(99L, new BigDecimal("500.00"));
        when(locks.lockInOrder(1L, 2L, 99L)).thenReturn(Map.of(1L, sender, 2L, receiver, 99L, system));

        service.disburse(transaction, sender, receiver);

        assertEquals(new BigDecimal("500.00"), system.getBalance());
        verify(flexCredits, never()).save(any());
    }

    @Test
    void repayOnSettlementReturnsSystemFloatOnlyOnce() {
        Transaction transaction = transaction(10L, new BigDecimal("75.00"));
        FlexCredit credit = new FlexCredit();
        credit.setStatus(FlexCreditStatus.OPEN);
        Wallet system = wallet(99L, new BigDecimal("425.00"));
        when(flexCredits.findByTransactionIdForUpdate(10L)).thenReturn(Optional.of(credit));

        assertTrue(service.repayOnSettlement(transaction, system));
        assertEquals(new BigDecimal("500.00"), system.getBalance());
        assertEquals(FlexCreditStatus.REPAID, credit.getStatus());
        assertTrue(credit.getSettledAt().isBefore(OffsetDateTime.now().plusSeconds(1)));

        assertFalse(service.repayOnSettlement(transaction, system));
    }

    @Test
    void recoverOnFailureRecordsOutstandingDebtWhenReceiverCannotRepayInFull() {
        Transaction transaction = transaction(10L, new BigDecimal("100.00"));
        FlexCredit credit = new FlexCredit();
        credit.setStatus(FlexCreditStatus.OPEN);
        credit.setAmount(new BigDecimal("100.00"));
        Wallet beneficiary = wallet(2L, new BigDecimal("40.00"));
        beneficiary.setFlexDebt(BigDecimal.ZERO);
        Wallet system = wallet(99L, new BigDecimal("900.00"));
        when(flexCredits.findByTransactionIdForUpdate(10L)).thenReturn(Optional.of(credit));

        service.recoverOnFailure(transaction, beneficiary, system);

        assertEquals(0, beneficiary.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("940.00"), system.getBalance());
        assertEquals(new BigDecimal("40.00"), credit.getAmountRecovered());
        assertEquals(new BigDecimal("60.00"), credit.getAmountOutstanding());
        assertEquals(new BigDecimal("60.00"), beneficiary.getFlexDebt());
        assertEquals(FlexCreditStatus.RECOVERING, credit.getStatus());
    }

    @Test
    void repayDebtFromInboundAppliesRepaymentToOldestCredits() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(new BigDecimal("70.00"));
        Wallet system = wallet(99L, new BigDecimal("930.00"));
        FlexCredit oldest = recoveringCredit(new BigDecimal("40.00"));
        FlexCredit newest = recoveringCredit(new BigDecimal("30.00"));
        when(locks.lockInOrder(99L)).thenReturn(Map.of(99L, system));
        when(flexCredits.findByBeneficiaryWalletIdAndStatusOrderByDisbursedAt(2L, FlexCreditStatus.RECOVERING))
                .thenReturn(List.of(oldest, newest));

        BigDecimal net = service.repayDebtFromInbound(beneficiary, new BigDecimal("50.00"),
                transaction(11L, new BigDecimal("50.00")));

        assertEquals(0, net.compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("980.00"), system.getBalance());
        assertEquals(new BigDecimal("20.00"), beneficiary.getFlexDebt());
        assertEquals(FlexCreditStatus.RECOVERED, oldest.getStatus());
        assertEquals(new BigDecimal("20.00"), newest.getAmountOutstanding());
    }

    // ─── List & Write-off ──────────────────────────────────────────────────────

    @Test
    void listReturnsAllWhenStatusIsNull() {
        FlexCredit fc1 = recoveringCredit(new BigDecimal("10.00"));
        FlexCredit fc2 = recoveringCredit(new BigDecimal("20.00"));
        fc2.setStatus(FlexCreditStatus.OPEN);
        when(flexCredits.findAll()).thenReturn(List.of(fc1, fc2));

        List<FlexCredit> all = service.list(null);
        assertEquals(2, all.size());

        List<FlexCredit> recoveringOnly = service.list(FlexCreditStatus.RECOVERING);
        assertEquals(1, recoveringOnly.size());
    }

    @Test
    void writeOffReducesBeneficiaryDebtAndEmitsLedgerEntry() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(new BigDecimal("100.00"));
        Wallet system = wallet(99L, new BigDecimal("900.00"));

        FlexCredit credit = recoveringCredit(new BigDecimal("100.00"));
        credit.setId(5L);
        credit.setBeneficiaryWallet(beneficiary);
        credit.setSystemWallet(system);
        credit.setTransaction(transaction(1L, new BigDecimal("100.00")));

        when(flexCredits.findById(5L)).thenReturn(Optional.of(credit));
        when(locks.lockInOrder(2L)).thenReturn(Map.of(2L, beneficiary));
        when(wallets.findById(99L)).thenReturn(Optional.of(system));

        service.writeOff(5L, "admin@example.com", "Defaulted loan");

        assertEquals(0, beneficiary.getFlexDebt().compareTo(BigDecimal.ZERO));
        assertEquals(FlexCreditStatus.WRITTEN_OFF, credit.getStatus());
        assertEquals("Defaulted loan", credit.getWriteOffReason());
        assertEquals("admin@example.com", credit.getWrittenOffBy());
        verify(ledgerEntries).save(any());
        verify(flexCredits).save(credit);
    }

    @Test
    void writeOffThrowsNotFoundWhenCreditNotFound() {
        when(flexCredits.findById(999L)).thenReturn(Optional.empty());

        BusinessRuleException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessRuleException.class, () -> service.writeOff(999L, "admin@example.com", "reason"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void writeOffThrowsConflictWhenStatusIsNotRecovering() {
        FlexCredit credit = recoveringCredit(new BigDecimal("100.00"));
        credit.setStatus(FlexCreditStatus.OPEN);
        when(flexCredits.findById(1L)).thenReturn(Optional.of(credit));

        BusinessRuleException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessRuleException.class, () -> service.writeOff(1L, "admin@example.com", "reason"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void writeOffThrowsNotFoundWhenSystemWalletNotFound() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(new BigDecimal("100.00"));
        Wallet system = wallet(99L, new BigDecimal("900.00"));

        FlexCredit credit = recoveringCredit(new BigDecimal("100.00"));
        credit.setId(5L);
        credit.setBeneficiaryWallet(beneficiary);
        credit.setSystemWallet(system);
        credit.setTransaction(transaction(1L, new BigDecimal("100.00")));

        when(flexCredits.findById(5L)).thenReturn(Optional.of(credit));
        when(locks.lockInOrder(2L)).thenReturn(Map.of(2L, beneficiary));
        when(wallets.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessRuleException.class, () -> service.writeOff(5L, "admin@example.com", "reason"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ─── Additional isEligible edge cases ────────────────────────────────────

    @Test
    void isEligibleReturnsTrueWhenFlaggedButConfigAllowsIt() {
        properties.setAllowFlaggedTransfers(true);
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(true);
        receiver.setWalletKind(WalletKind.CUSTOMER);
        receiver.setFlexDebt(BigDecimal.ZERO);
        when(wallets.findById(99L)).thenReturn(Optional.of(wallet(99L, new BigDecimal("1000.00"))));

        assertTrue(service.isEligible(receiver, new BigDecimal("100.00"), true));
    }

    @Test
    void isEligibleTreatsNullFlexDebtAsZero() {
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(true);
        receiver.setWalletKind(WalletKind.CUSTOMER);
        receiver.setFlexDebt(null); // null — should be treated as zero
        when(wallets.findById(99L)).thenReturn(Optional.of(wallet(99L, new BigDecimal("1000.00"))));

        assertTrue(service.isEligible(receiver, new BigDecimal("100.00"), false));
    }

    // ─── Additional recoverOnFailure paths ───────────────────────────────────

    @Test
    void recoverOnFailureFullyRecoverWhenReceiverHasEnoughBalance() {
        Transaction transaction = transaction(10L, new BigDecimal("100.00"));
        FlexCredit credit = new FlexCredit();
        credit.setStatus(FlexCreditStatus.OPEN);
        credit.setAmount(new BigDecimal("100.00"));
        Wallet beneficiary = wallet(2L, new BigDecimal("150.00")); // more than flex amount
        beneficiary.setFlexDebt(BigDecimal.ZERO);
        Wallet system = wallet(99L, new BigDecimal("900.00"));
        when(flexCredits.findByTransactionIdForUpdate(10L)).thenReturn(Optional.of(credit));

        service.recoverOnFailure(transaction, beneficiary, system);

        assertEquals(new BigDecimal("50.00"), beneficiary.getBalance()); // 150 - 100
        assertEquals(new BigDecimal("1000.00"), system.getBalance()); // 900 + 100
        assertEquals(new BigDecimal("100.00"), credit.getAmountRecovered());
        assertEquals(0, credit.getAmountOutstanding().compareTo(BigDecimal.ZERO));
        assertEquals(FlexCreditStatus.RECOVERED, credit.getStatus());
        assertNotNull(credit.getRecoveredAt());
        assertEquals(0, beneficiary.getFlexDebt().compareTo(BigDecimal.ZERO)); // no debt added
    }

    @Test
    void recoverOnFailureDoesNothingWhenNoFlexCreditExists() {
        Transaction transaction = transaction(10L, new BigDecimal("100.00"));
        Wallet beneficiary = wallet(2L, new BigDecimal("150.00"));
        Wallet system = wallet(99L, new BigDecimal("900.00"));
        when(flexCredits.findByTransactionIdForUpdate(10L)).thenReturn(Optional.empty());

        service.recoverOnFailure(transaction, beneficiary, system);

        assertEquals(new BigDecimal("150.00"), beneficiary.getBalance()); // unchanged
        assertEquals(new BigDecimal("900.00"), system.getBalance()); // unchanged
        verify(wallets, never()).save(any());
    }

    @Test
    void recoverOnFailureIsIdempotentWhenCreditAlreadyClosed() {
        Transaction transaction = transaction(10L, new BigDecimal("100.00"));
        FlexCredit credit = new FlexCredit();
        credit.setStatus(FlexCreditStatus.REPAID); // already closed
        Wallet beneficiary = wallet(2L, new BigDecimal("150.00"));
        Wallet system = wallet(99L, new BigDecimal("900.00"));
        when(flexCredits.findByTransactionIdForUpdate(10L)).thenReturn(Optional.of(credit));

        service.recoverOnFailure(transaction, beneficiary, system);

        assertEquals(new BigDecimal("150.00"), beneficiary.getBalance()); // unchanged
        verify(wallets, never()).save(any());
    }

    // ─── Additional repayDebtFromInbound paths ────────────────────────────────

    @Test
    void repayDebtFromInboundFastPathWhenDebtIsZero() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(BigDecimal.ZERO);

        BigDecimal net = service.repayDebtFromInbound(beneficiary, new BigDecimal("200.00"),
                transaction(11L, new BigDecimal("200.00")));

        // No debt — full amount returned, system wallet never touched
        assertEquals(0, net.compareTo(new BigDecimal("200.00")));
        verify(wallets, never()).save(any());
    }

    @Test
    void repayDebtFromInboundFullyRepaysWhenIncomingExceedsDebt() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(new BigDecimal("30.00"));
        Wallet system = wallet(99L, new BigDecimal("970.00"));
        FlexCredit recovering = recoveringCredit(new BigDecimal("30.00"));
        when(locks.lockInOrder(99L)).thenReturn(Map.of(99L, system));
        when(flexCredits.findByBeneficiaryWalletIdAndStatusOrderByDisbursedAt(2L, FlexCreditStatus.RECOVERING))
                .thenReturn(List.of(recovering));

        BigDecimal net = service.repayDebtFromInbound(beneficiary, new BigDecimal("100.00"),
                transaction(11L, new BigDecimal("100.00")));

        assertEquals(0, net.compareTo(new BigDecimal("70.00"))); // 100 - 30
        assertEquals(new BigDecimal("1000.00"), system.getBalance());
        assertEquals(0, beneficiary.getFlexDebt().compareTo(BigDecimal.ZERO));
        assertEquals(FlexCreditStatus.RECOVERED, recovering.getStatus());
    }

    // ─── writeOff — negative debt clamped to zero ─────────────────────────────

    @Test
    void writeOffClampsBeneficiaryDebtToZeroWhenOutstandingExceedsRecordedDebt() {
        Wallet beneficiary = wallet(2L, BigDecimal.ZERO);
        beneficiary.setFlexDebt(new BigDecimal("50.00")); // debt is 50
        Wallet system = wallet(99L, new BigDecimal("900.00"));

        FlexCredit credit = recoveringCredit(new BigDecimal("80.00")); // outstanding is 80 > debt
        credit.setId(5L);
        credit.setBeneficiaryWallet(beneficiary);
        credit.setSystemWallet(system);
        credit.setTransaction(transaction(1L, new BigDecimal("80.00")));

        when(flexCredits.findById(5L)).thenReturn(Optional.of(credit));
        when(locks.lockInOrder(2L)).thenReturn(Map.of(2L, beneficiary));
        when(wallets.findById(99L)).thenReturn(Optional.of(system));

        service.writeOff(5L, "admin@example.com", "Debt forgiven");

        // 50 - 80 = -30 → clamped to 0
        assertEquals(0, beneficiary.getFlexDebt().compareTo(BigDecimal.ZERO));
        assertEquals(FlexCreditStatus.WRITTEN_OFF, credit.getStatus());
    }

    // ─── disburse — silent skip under lock ────────────────────────────────────

    @Test
    void disburseDoesNothingWhenDebtAccruedUnderLock() {
        Transaction transaction = transaction(10L, new BigDecimal("75.00"));
        Wallet sender = wallet(1L, new BigDecimal("200.00"));
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(true);
        receiver.setFlexDebt(new BigDecimal("10.00")); // debt appeared under lock
        Wallet system = wallet(99L, new BigDecimal("500.00"));
        when(locks.lockInOrder(1L, 2L, 99L)).thenReturn(Map.of(1L, sender, 2L, receiver, 99L, system));

        service.disburse(transaction, sender, receiver);

        assertEquals(new BigDecimal("500.00"), system.getBalance());
        verify(flexCredits, never()).save(any());
    }

    @Test
    void disburseDoesNothingWhenSystemBalanceTooLowUnderLock() {
        Transaction transaction = transaction(10L, new BigDecimal("75.00"));
        Wallet sender = wallet(1L, new BigDecimal("200.00"));
        Wallet receiver = wallet(2L, BigDecimal.ZERO);
        receiver.setFlexEnabled(true);
        receiver.setFlexDebt(BigDecimal.ZERO);
        Wallet system = wallet(99L, new BigDecimal("50.00")); // only 50 but need 75
        when(locks.lockInOrder(1L, 2L, 99L)).thenReturn(Map.of(1L, sender, 2L, receiver, 99L, system));

        service.disburse(transaction, sender, receiver);

        assertEquals(new BigDecimal("50.00"), system.getBalance());
        verify(flexCredits, never()).save(any());
    }

    private static FlexCredit recoveringCredit(BigDecimal outstanding) {
        FlexCredit credit = new FlexCredit();
        credit.setStatus(FlexCreditStatus.RECOVERING);
        credit.setAmountOutstanding(outstanding);
        credit.setAmountRecovered(BigDecimal.ZERO);
        credit.setDisbursedAt(OffsetDateTime.now());
        return credit;
    }

    private static Transaction transaction(Long id, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAmount(amount);
        return transaction;
    }

    private static Wallet wallet(Long id, BigDecimal balance) {
        User user = new User();
        user.setId(id * 100); // distinct user ID per wallet
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setBalance(balance);
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setWalletKind(WalletKind.CUSTOMER);
        wallet.setFlexDebt(BigDecimal.ZERO);
        return wallet;
    }
}

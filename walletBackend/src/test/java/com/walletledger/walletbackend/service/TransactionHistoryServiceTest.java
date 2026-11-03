package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.request.TransactionFilter;
import com.walletledger.walletbackend.dto.response.TransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.TransactionSummaryResponse;
import com.walletledger.walletbackend.dto.response.UserStatsResponse;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

class TransactionHistoryServiceTest {

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private TransactionHistoryService service;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        service = new TransactionHistoryService(
                userRepository, walletRepository, transactionRepository, ledgerEntryRepository);

        user = user(1L, "ada@example.com");
        wallet = wallet(10L, user);
        stubWallet("ada@example.com", wallet);
    }

    // ─── Mini-statement & History ──────────────────────────────────────────────

    @Test
    void miniStatementReturnsFirstPageSummaries() {
        Transaction t = transfer(wallet, wallet, TransactionStatus.SUCCESS);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        List<TransactionSummaryResponse> result = service.miniStatement("ada@example.com");

        assertEquals(1, result.size());
        assertEquals(TransactionDirection.SENT, result.get(0).direction());
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void historyHandlesVariousFilters(TransactionDirection direction, TransactionStatusFilter statusFilter,
            LocalDate fromDate, LocalDate toDate, Integer page, Integer size) {
        TransactionFilter filter = new TransactionFilter(fromDate, toDate, direction, statusFilter, page, size);
        Transaction t = transfer(wallet, wallet, TransactionStatus.SUCCESS);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        PageResponse<TransactionSummaryResponse> response = service.history("ada@example.com", filter);

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    static Stream<Arguments> filterCombinations() {
        return Stream.of(
                Arguments.of(TransactionDirection.SENT, TransactionStatusFilter.SUCCESS, LocalDate.now().minusDays(5),
                        LocalDate.now(), 0, 10),
                Arguments.of(TransactionDirection.RECEIVED, TransactionStatusFilter.PENDING, null, null, null, null),
                Arguments.of(TransactionDirection.ALL, TransactionStatusFilter.FAILED, LocalDate.now(), null, -1, 500),
                Arguments.of(null, TransactionStatusFilter.ALL, null, LocalDate.now(), 1, 20),
                Arguments.of(TransactionDirection.ALL, null, null, null, 0, 10));
    }

    @Test
    void toSummaryHandlesTopUpTransaction() {
        Transaction topUp = transfer(null, wallet, TransactionStatus.SUCCESS);
        topUp.setType(TransactionType.ADD_MONEY);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(topUp)));

        List<TransactionSummaryResponse> result = service.miniStatement("ada@example.com");

        assertEquals(1, result.size());
        assertEquals("Wallet top-up", result.get(0).counterpartyName());
        assertEquals(TransactionDirection.RECEIVED, result.get(0).direction());
    }

    // ─── Detail ────────────────────────────────────────────────────────────────

    @Test
    void senderCanSeePendingTransfer() {
        User receiver = user(2L, "receiver@example.com");
        Wallet receiverWallet = wallet(20L, receiver);
        Transaction t = transfer(wallet, receiverWallet, TransactionStatus.PENDING);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        TransactionDetailResponse detail = service.detail("ada@example.com", 10L);

        assertEquals(10L, detail.id());
        assertEquals(TransactionDirection.SENT, detail.direction());
        assertNotNull(detail.settlementDueDate());
    }

    @Test
    void receiverCannotSeeAPendingTransfer() {
        User sender = user(2L, "sender@example.com");
        Wallet senderWallet = wallet(20L, sender);
        Transaction t = transfer(senderWallet, wallet, TransactionStatus.PENDING);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(t));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.detail("ada@example.com", 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void receiverCanSeeASettledTransferWithLedgerEntries() {
        User sender = user(2L, "sender@example.com");
        Wallet senderWallet = wallet(20L, sender);
        Transaction t = transfer(senderWallet, wallet, TransactionStatus.SUCCESS);
        t.setSettledAt(OffsetDateTime.now());
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(t));

        LedgerEntry entry = new LedgerEntry();
        entry.setId(1L);
        entry.setWallet(wallet);
        entry.setEntryType(LedgerEntryType.CREDIT);
        entry.setAmount(new BigDecimal("25.00"));
        entry.setBalanceAfter(new BigDecimal("125.00"));
        entry.setCreatedAt(OffsetDateTime.now());
        when(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(entry));

        TransactionDetailResponse detail = service.detail("ada@example.com", 10L);

        assertEquals(10L, detail.id());
        assertEquals(TransactionDirection.RECEIVED, detail.direction());
        assertEquals(1, detail.ledgerEntries().size());
    }

    @Test
    void detailThrowsNotFoundWhenTransactionDoesNotExist() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.detail("ada@example.com", 999L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void detailThrowsUnauthorizedWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.detail("unknown@example.com", 10L));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void detailThrowsNotFoundWhenWalletNotFound() {
        User userNoWallet = user(99L, "nowallet@example.com");
        when(userRepository.findByEmail("nowallet@example.com")).thenReturn(Optional.of(userNoWallet));
        when(walletRepository.findByUserId(99L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.detail("nowallet@example.com", 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ─── Stats & Days remaining ────────────────────────────────────────────────

    @Test
    void statsCalculatesSentReceivedAndCount() {
        when(transactionRepository.sumSent(eq(10L), eq(List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS))))
                .thenReturn(new BigDecimal("120.00"));
        when(transactionRepository.sumReceived(10L, TransactionType.TRANSFER, TransactionStatus.SUCCESS))
                .thenReturn(new BigDecimal("50.00"));
        when(transactionRepository.countVisibleSince(eq(10L), eq(TransactionStatus.SUCCESS), any()))
                .thenReturn(3L);

        UserStatsResponse stats = service.stats("ada@example.com");

        assertEquals(new BigDecimal("120.00"), stats.totalSent());
        assertEquals(new BigDecimal("50.00"), stats.totalReceived());
        assertEquals(3L, stats.thisMonthTransactionCount());
    }

    @Test
    void daysRemainingReturnsNullForNonPendingTransaction() {
        Transaction success = new Transaction();
        success.setStatus(TransactionStatus.SUCCESS);
        success.setSettlementDueDate(LocalDate.now());

        assertNull(service.daysRemaining(success));
    }

    @Test
    void daysRemainingReturnsNullWhenDueDateIsNull() {
        Transaction pending = new Transaction();
        pending.setStatus(TransactionStatus.PENDING);
        pending.setSettlementDueDate(null);

        assertNull(service.daysRemaining(pending));
    }

    @Test
    void daysRemainingIsZeroWhenOverdue() {
        Transaction pending = new Transaction();
        pending.setStatus(TransactionStatus.PENDING);
        pending.setSettlementDueDate(WalletService.today().minusDays(2));

        assertEquals(0L, service.daysRemaining(pending));
    }

    @Test
    void daysRemainingReturnsFutureDaysWhenDueDateIsAhead() {
        Transaction pending = new Transaction();
        pending.setStatus(TransactionStatus.PENDING);
        pending.setSettlementDueDate(WalletService.today().plusDays(5));

        assertEquals(5L, service.daysRemaining(pending));
    }

    @Test
    void historyHandlesDeclinedStatusFilter() {
        TransactionFilter filter = new TransactionFilter(null, null, TransactionDirection.ALL,
                TransactionStatusFilter.DECLINED, 0, 10);
        Transaction t = transfer(wallet, wallet, TransactionStatus.DECLINED);
        when(transactionRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(t)));

        PageResponse<TransactionSummaryResponse> response = service.history("ada@example.com", filter);

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    void detailNullSenderWalletIsHandledGracefully() {
        // Top-up scenario: senderWallet is null, receiver is ada, status SUCCESS
        Transaction topUp = transfer(null, wallet, TransactionStatus.SUCCESS);
        topUp.setType(TransactionType.ADD_MONEY);
        topUp.setSettledAt(OffsetDateTime.now());
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(topUp));
        when(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        TransactionDetailResponse detail = service.detail("ada@example.com", 10L);

        assertEquals(TransactionDirection.RECEIVED, detail.direction());
        assertNull(detail.senderName());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void stubWallet(String email, Wallet wallet) {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(wallet.getUser()));
        when(walletRepository.findByUserId(wallet.getUser().getId())).thenReturn(Optional.of(wallet));
    }

    private static Transaction transfer(Wallet sender, Wallet receiver, TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setId(10L);
        transaction.setRequestId("req-1");
        transaction.setType(TransactionType.TRANSFER);
        transaction.setSenderWallet(sender);
        transaction.setReceiverWallet(receiver);
        transaction.setAmount(new BigDecimal("25.00"));
        transaction.setStatus(status);
        transaction.setInitiatedAt(OffsetDateTime.now());
        transaction.setSettlementDueDate(LocalDate.now().plusDays(3));
        return transaction;
    }

    private static Wallet wallet(Long id, User user) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(user);
        return wallet;
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        return user;
    }
}

package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.response.TransactionDetailResponse;
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
import org.springframework.http.HttpStatus;

class TransactionHistoryServiceTest {

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private TransactionHistoryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        service = new TransactionHistoryService(
                userRepository, walletRepository, transactionRepository, ledgerEntryRepository);
    }

    @Test
    void receiverCannotSeeAPendingTransfer() {
        Wallet receiverWallet = wallet(2L, user(2L, "receiver@example.com"));
        stubWallet("receiver@example.com", receiverWallet);
        Transaction transfer = transfer(receiverWallet, TransactionStatus.PENDING);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transfer));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class, () -> service.detail("receiver@example.com", 10L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void receiverCanSeeASettledTransfer() {
        Wallet receiverWallet = wallet(2L, user(2L, "receiver@example.com"));
        stubWallet("receiver@example.com", receiverWallet);
        Transaction transfer = transfer(receiverWallet, TransactionStatus.SUCCESS);
        transfer.setSettledAt(OffsetDateTime.now());
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transfer));
        when(ledgerEntryRepository.findByTransactionIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        TransactionDetailResponse detail = service.detail("receiver@example.com", 10L);
        assertEquals(10L, detail.id());
        assertEquals(TransactionStatus.SUCCESS, detail.status());
        assertNull(detail.settlementDueDate());
    }

    @Test
    void daysRemainingIsZeroWhenTheDueDateHasPassed() {
        Transaction pending = new Transaction();
        pending.setStatus(TransactionStatus.PENDING);
        pending.setSettlementDueDate(WalletService.today().minusDays(1));
        assertEquals(0L, service.daysRemaining(pending));
    }

    @Test
    void statsCountPendingAndSettledAsSentAndOnlySettledTransfersAsReceived() {
        Wallet wallet = wallet(1L, user(1L, "ada@example.com"));
        stubWallet("ada@example.com", wallet);
        when(transactionRepository.sumSent(eq(1L), eq(List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS))))
                .thenReturn(new BigDecimal("120.00"));
        when(transactionRepository.sumReceived(1L, TransactionType.TRANSFER, TransactionStatus.SUCCESS))
                .thenReturn(new BigDecimal("50.00"));
        when(transactionRepository.countVisibleSince(eq(1L), eq(TransactionStatus.SUCCESS), any()))
                .thenReturn(3L);

        var stats = service.stats("ada@example.com");
        assertEquals(new BigDecimal("120.00"), stats.totalSent());
        assertEquals(new BigDecimal("50.00"), stats.totalReceived());
        assertEquals(3L, stats.thisMonthTransactionCount());
        verify(transactionRepository).sumSent(1L, List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS));
        verify(transactionRepository).sumReceived(1L, TransactionType.TRANSFER, TransactionStatus.SUCCESS);
    }

    private void stubWallet(String email, Wallet wallet) {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(wallet.getUser()));
        when(walletRepository.findByUserId(wallet.getUser().getId())).thenReturn(Optional.of(wallet));
    }

    private static Transaction transfer(Wallet receiverWallet, TransactionStatus status) {
        User sender = user(1L, "sender@example.com");
        Wallet senderWallet = wallet(1L, sender);
        Transaction transaction = new Transaction();
        transaction.setId(10L);
        transaction.setRequestId("req-1");
        transaction.setType(TransactionType.TRANSFER);
        transaction.setSenderWallet(senderWallet);
        transaction.setReceiverWallet(receiverWallet);
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

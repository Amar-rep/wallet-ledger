package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementServiceTest {

    private TransactionRepository transactionRepository;
    private WalletRepository walletRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private NotificationService notificationService;
    private SettlementBatchRepository settlementBatchRepository;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        walletRepository = mock(WalletRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        notificationService = mock(NotificationService.class);
        settlementBatchRepository = mock(SettlementBatchRepository.class);
        settlementService = new SettlementService(
                transactionRepository, walletRepository, ledgerEntryRepository,
                notificationService, settlementBatchRepository);
    }

    @Test
    void settleSkipsNonPendingTransfersWithoutAttachingABatch() {
        Transaction transaction = pendingTransfer();
        transaction.setStatus(TransactionStatus.SUCCESS);
        when(transactionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(transaction));

        assertEquals(SettlementOutcome.SKIPPED, settlementService.settle(10L, 99L));
        verify(settlementBatchRepository, never()).getReferenceById(any());
        assertNull(transaction.getSettlementBatch());
    }

    @Test
    void settleAttachesTheBatchBeforeCreditingTheReceiver() {
        Transaction transaction = pendingTransfer();
        SettlementBatch batch = new SettlementBatch();
        batch.setId(99L);
        when(transactionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(transaction));
        when(settlementBatchRepository.getReferenceById(99L)).thenReturn(batch);
        when(walletRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(transaction.getSenderWallet()));
        when(walletRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(transaction.getReceiverWallet()));

        assertEquals(SettlementOutcome.SETTLED, settlementService.settle(10L, 99L));
        assertEquals(batch, transaction.getSettlementBatch());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(new BigDecimal("125.00"), transaction.getReceiverWallet().getBalance());
    }

    private static Transaction pendingTransfer() {
        User sender = user(1L, "Ada");
        User receiver = user(2L, "Bob");
        Wallet senderWallet = wallet(1L, sender, new BigDecimal("50.00"));
        Wallet receiverWallet = wallet(2L, receiver, new BigDecimal("100.00"));
        Transaction transaction = new Transaction();
        transaction.setId(10L);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setSenderWallet(senderWallet);
        transaction.setReceiverWallet(receiverWallet);
        transaction.setAmount(new BigDecimal("25.00"));
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setInitiatedAt(OffsetDateTime.now());
        return transaction;
    }

    private static Wallet wallet(Long id, User user, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setBalance(balance);
        wallet.setDailySpent(BigDecimal.ZERO);
        wallet.setStatus(WalletStatus.ACTIVE);
        return wallet;
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}

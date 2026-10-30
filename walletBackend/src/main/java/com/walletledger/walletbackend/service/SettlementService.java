package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NotificationService notificationService;
    private final SettlementBatchRepository settlementBatchRepository;

    /** Each transaction settles in its own database transaction, isolating failures in a batch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementOutcome settle(Long transactionId, Long batchId) {
        Transaction transaction = getTransactionForUpdate(transactionId);
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return SettlementOutcome.SKIPPED;
        }
        attachBatch(transaction, batchId);

        Map<Long, Wallet> wallets = lockWallets(
                transaction.getSenderWallet().getId(), transaction.getReceiverWallet().getId());
        Wallet sender = wallets.get(transaction.getSenderWallet().getId());
        Wallet receiver = wallets.get(transaction.getReceiverWallet().getId());

        if (receiver.getStatus() != WalletStatus.ACTIVE) {
            refundAndClose(transaction, sender, TransactionStatus.DECLINED,
                    "Receiver wallet is " + receiver.getStatus().name().toLowerCase());
            return SettlementOutcome.DECLINED;
        }

        receiver.setBalance(receiver.getBalance().add(transaction.getAmount()));
        receiver.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(receiver);
        saveLedger(transaction, receiver, LedgerEntryType.CREDIT, transaction.getAmount(), receiver.getBalance());

        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setSettledAt(OffsetDateTime.now());
        transactionRepository.save(transaction);

        notificationService.notifyUser(sender.getUser().getId(), NotificationType.TRANSFER_SETTLED,
                NotificationSeverity.INFO, money(transaction.getAmount()) + " transfer to "
                        + receiver.getUser().getName() + " has settled", transaction);
        notificationService.notifyUser(receiver.getUser().getId(), NotificationType.TRANSFER_RECEIVED,
                NotificationSeverity.INFO, money(transaction.getAmount()) + " received from "
                        + sender.getUser().getName(), transaction);
        return SettlementOutcome.SETTLED;
    }

    /** Marks an unresolved settlement as failed and restores the sender's available balance. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long transactionId, String reason, Long batchId) {
        Transaction transaction = getTransactionForUpdate(transactionId);
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return;
        }
        attachBatch(transaction, batchId);
        Wallet sender = lockWallets(transaction.getSenderWallet().getId())
                .get(transaction.getSenderWallet().getId());
        refundAndClose(transaction, sender, TransactionStatus.FAILED, reason);
    }

    /** Admin review blocks only a still-pending high-value transfer. */
    @Transactional
    public void blockByAdmin(Long transactionId) {
        Transaction transaction = getTransactionForUpdate(transactionId);
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessRuleException(HttpStatus.CONFLICT,
                    "Transaction is already " + transaction.getStatus() + " and can no longer be blocked");
        }
        Wallet sender = lockWallets(transaction.getSenderWallet().getId())
                .get(transaction.getSenderWallet().getId());
        refundAndClose(transaction, sender, TransactionStatus.FAILED, "Blocked by admin");
        notificationService.notifyUser(transaction.getReceiverWallet().getUser().getId(), NotificationType.TRANSFER_FAILED,
                NotificationSeverity.INFO, "A transfer of " + money(transaction.getAmount())
                        + " to you was cancelled", transaction);
    }

    private void attachBatch(Transaction transaction, Long batchId) {
        if (batchId != null) {
            transaction.setSettlementBatch(settlementBatchRepository.getReferenceById(batchId));
        }
    }

    private Transaction getTransactionForUpdate(Long transactionId) {
        return transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private void refundAndClose(Transaction transaction, Wallet sender, TransactionStatus status, String reason) {
        sender.setBalance(sender.getBalance().add(transaction.getAmount()));
        if (transaction.getInitiatedAt().atZoneSameInstant(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()
                .equals(WalletService.today())
                && WalletService.today().equals(sender.getDailySpentDate())) {
            BigDecimal remaining = sender.getDailySpent().subtract(transaction.getAmount());
            sender.setDailySpent(remaining.signum() < 0 ? BigDecimal.ZERO : remaining);
        }
        sender.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(sender);
        saveLedger(transaction, sender, LedgerEntryType.REFUND, transaction.getAmount(), sender.getBalance());

        transaction.setStatus(status);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        String recipientName = transaction.getReceiverWallet().getUser().getName();
        NotificationType notificationType = status == TransactionStatus.DECLINED
                ? NotificationType.TRANSFER_DECLINED : NotificationType.TRANSFER_FAILED;
        String outcome = status == TransactionStatus.DECLINED ? "was declined" : "failed";
        notificationService.notifyUser(sender.getUser().getId(), notificationType, NotificationSeverity.INFO,
                money(transaction.getAmount()) + " transfer to " + recipientName + " " + outcome
                        + ". Amount refunded to your wallet", transaction);
    }

    /** Acquires locks by ascending id to avoid a deadlock between opposing settlements. */
    private Map<Long, Wallet> lockWallets(Long... walletIds) {
        Map<Long, Wallet> wallets = new TreeMap<>();
        for (Long walletId : new TreeSet<>(Arrays.asList(walletIds))) {
            wallets.put(walletId, walletRepository.findByIdForUpdate(walletId)
                    .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found")));
        }
        return wallets;
    }

    private void saveLedger(Transaction transaction, Wallet wallet, LedgerEntryType type,
                            BigDecimal amount, BigDecimal balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setWallet(wallet);
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setCreatedAt(OffsetDateTime.now());
        ledgerEntryRepository.save(entry);
    }

    private String money(BigDecimal amount) {
        return String.format(java.util.Locale.forLanguageTag("en-IN"), "\\u20b9%,.2f", amount);
    }
}

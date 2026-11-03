package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import com.walletledger.walletbackend.config.FlexProperties;
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
        private final WalletLockService walletLockService;
        private final FlexService flexService;
        private final FlexProperties flexProperties;

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public SettlementOutcome settle(Long transactionId, Long batchId) {
                Transaction transaction = getTransactionForUpdate(transactionId);
                if (transaction.getStatus() != TransactionStatus.PENDING) {
                        return SettlementOutcome.SKIPPED;
                }
                attachBatch(transaction, batchId);

                // Lock sender, receiver — and system wallet if Flex is enabled — in ascending
                // order
                Long senderId = transaction.getSenderWallet().getId();
                Long receiverId = transaction.getReceiverWallet().getId();
                Map<Long, Wallet> wallets = flexProperties.isEnabled()
                                ? walletLockService.lockInOrder(senderId, receiverId,
                                                flexProperties.getSystemWalletId())
                                : walletLockService.lockInOrder(senderId, receiverId);

                Wallet sender = wallets.get(senderId);
                Wallet receiver = wallets.get(receiverId);

                if (receiver.getStatus() != WalletStatus.ACTIVE) {
                        Wallet systemWallet = flexProperties.isEnabled()
                                        ? wallets.get(flexProperties.getSystemWalletId())
                                        : null;
                        if (systemWallet != null) {
                                flexService.recoverOnFailure(transaction, receiver, systemWallet);
                        }
                        refundAndClose(transaction, sender, TransactionStatus.DECLINED,
                                        "Receiver wallet is " + receiver.getStatus().name().toLowerCase());
                        return SettlementOutcome.DECLINED;
                }

                // If Flex was used, repay system wallet instead of crediting receiver again
                if (flexProperties.isEnabled()) {
                        Wallet systemWallet = wallets.get(flexProperties.getSystemWalletId());
                        boolean flexRepaid = flexService.repayOnSettlement(transaction, systemWallet);
                        if (!flexRepaid) {
                                // No Flex — normal credit path with debt repayment if any
                                creditReceiver(transaction, receiver);
                        }
                } else {
                        creditReceiver(transaction, receiver);
                }

                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setSettledAt(OffsetDateTime.now());
                transactionRepository.save(transaction);

                notificationService.notifyUser(sender.getUser().getId(), NotificationType.TRANSFER_SETTLED,
                                NotificationSeverity.INFO, money(transaction.getAmount()) + " transfer to "
                                                + receiver.getUser().getName() + " has settled",
                                transaction);
                notificationService.notifyUser(receiver.getUser().getId(), NotificationType.TRANSFER_RECEIVED,
                                NotificationSeverity.INFO, money(transaction.getAmount()) + " received from "
                                                + sender.getUser().getName(),
                                transaction);
                return SettlementOutcome.SETTLED;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void fail(Long transactionId, String reason, Long batchId) {
                Transaction transaction = getTransactionForUpdate(transactionId);
                if (transaction.getStatus() != TransactionStatus.PENDING)
                        return;
                attachBatch(transaction, batchId);

                Long senderId = transaction.getSenderWallet().getId();
                Long receiverId = transaction.getReceiverWallet().getId();
                Map<Long, Wallet> wallets = flexProperties.isEnabled()
                                ? walletLockService.lockInOrder(senderId, receiverId,
                                                flexProperties.getSystemWalletId())
                                : walletLockService.lockInOrder(senderId);

                Wallet sender = wallets.get(senderId);
                if (flexProperties.isEnabled()) {
                        Wallet receiver = wallets.get(receiverId);
                        Wallet systemWallet = wallets.get(flexProperties.getSystemWalletId());
                        flexService.recoverOnFailure(transaction, receiver, systemWallet);
                }
                refundAndClose(transaction, sender, TransactionStatus.FAILED, reason);
        }

        @Transactional
        public void blockByAdmin(Long transactionId) {
                Transaction transaction = getTransactionForUpdate(transactionId);
                if (transaction.getStatus() != TransactionStatus.PENDING) {
                        throw new BusinessRuleException(HttpStatus.CONFLICT,
                                        "Transaction is already " + transaction.getStatus()
                                                        + " and can no longer be blocked");
                }

                Long senderId = transaction.getSenderWallet().getId();
                Long receiverId = transaction.getReceiverWallet().getId();
                Map<Long, Wallet> wallets = flexProperties.isEnabled()
                                ? walletLockService.lockInOrder(senderId, receiverId,
                                                flexProperties.getSystemWalletId())
                                : walletLockService.lockInOrder(senderId);

                Wallet sender = wallets.get(senderId);
                if (flexProperties.isEnabled()) {
                        Wallet receiver = wallets.get(receiverId);
                        Wallet systemWallet = wallets.get(flexProperties.getSystemWalletId());
                        flexService.recoverOnFailure(transaction, receiver, systemWallet);
                }
                refundAndClose(transaction, sender, TransactionStatus.FAILED, "Blocked by admin");
                notificationService.notifyUser(transaction.getReceiverWallet().getUser().getId(),
                                NotificationType.TRANSFER_FAILED, NotificationSeverity.INFO,
                                "A transfer of " + money(transaction.getAmount()) + " to you was cancelled",
                                transaction);
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private void creditReceiver(Transaction transaction, Wallet receiver) {
                BigDecimal amount = transaction.getAmount();

                // Repay any flex debt from this inbound credit before crediting the remainder
                BigDecimal net = flexProperties.isEnabled()
                                ? flexService.repayDebtFromInbound(receiver, amount, transaction)
                                : amount;

                receiver.setBalance(receiver.getBalance().add(net));
                receiver.setUpdatedAt(OffsetDateTime.now());
                walletRepository.save(receiver);
                saveLedger(transaction, receiver, LedgerEntryType.CREDIT, net, receiver.getBalance());
        }

        private void attachBatch(Transaction transaction, Long batchId) {
                if (batchId != null) {
                        transaction.setSettlementBatch(settlementBatchRepository.getReferenceById(batchId));
                }
        }

        private Transaction getTransactionForUpdate(Long transactionId) {
                return transactionRepository.findByIdForUpdate(transactionId)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                                                "Transaction not found"));
        }

        private void refundAndClose(Transaction transaction, Wallet sender,
                        TransactionStatus status, String reason) {
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
                                ? NotificationType.TRANSFER_DECLINED
                                : NotificationType.TRANSFER_FAILED;
                String outcome = status == TransactionStatus.DECLINED ? "was declined" : "failed";
                notificationService.notifyUser(sender.getUser().getId(), notificationType, NotificationSeverity.INFO,
                                money(transaction.getAmount()) + " transfer to " + recipientName + " " + outcome
                                                + ". Amount refunded to your wallet",
                                transaction);
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
                NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
                fmt.setMinimumFractionDigits(2);
                fmt.setMaximumFractionDigits(2);
                return fmt.format(amount);
        }
}

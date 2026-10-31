package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.request.SendMoneyRequest;
import com.walletledger.walletbackend.dto.response.TransferResponse;
import com.walletledger.walletbackend.dto.response.UserSearchResponse;
import com.walletledger.walletbackend.entity.FraudAlert;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

        private static final BigDecimal DAILY_LIMIT = new BigDecimal("50000.00");
        private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("8000.00");
        private static final int DUPLICATE_WINDOW_MINUTES = 2;
        private static final int SETTLEMENT_DAYS = 3;
        private static final DateTimeFormatter SETTLEMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
        private final TransactionRepository transactionRepository;
        private final LedgerEntryRepository ledgerEntryRepository;
        private final FraudAlertRepository fraudAlertRepository;
        private final NotificationService notificationService;
        private final PasswordEncoder passwordEncoder;

        @Transactional(readOnly = true)
        public UserSearchResponse search(String currentUserEmail, String query) {
                String normalizedQuery = query == null ? "" : query.trim();
                if (normalizedQuery.isEmpty()) {
                        throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                                        "Enter an email address or phone number");
                }

                User user = (normalizedQuery.contains("@")
                                ? userRepository.findByEmail(normalizedQuery.toLowerCase())
                                : userRepository.findByPhone(normalizedQuery))
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "No user found"));
                if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
                        throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "You cannot send money to yourself");
                }
                return new UserSearchResponse(user.getId(), user.getName(), user.getPhone(), user.getEmail());
        }

        /**
         * Fraud rejections are deliberately committed so the alert and its
         * notifications are retained.
         * All balance mutations occur after those checks, so a rejected request cannot
         * debit the sender.
         */
        @Transactional(noRollbackFor = BusinessRuleException.class)
        public TransferResponse sendMoney(String email, SendMoneyRequest request) {
                User sender = userRepository.findByEmail(email)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED,
                                                "User not found"));
                Wallet senderWallet = walletRepository.findByUserIdForUpdate(sender.getId())
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                                                "Sender wallet not found"));

                Optional<Transaction> existing = transactionRepository
                                .findBySenderWalletIdAndRequestId(senderWallet.getId(), request.requestId());
                if (existing.isPresent()) {
                        return toResponse(existing.get(), "Duplicate request. Returning the original result");
                }

                if (!passwordEncoder.matches(request.pin(), sender.getPinHash())) {
                        throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "Invalid transaction PIN");
                }
                WalletService.requireActive(senderWallet, "send money from");
                WalletService.resetDailySpentIfNeeded(senderWallet);

                if (request.receiverUserId().equals(sender.getId())) {
                        throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "You cannot send money to yourself");
                }
                User receiver = userRepository.findById(request.receiverUserId())
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                                                "Recipient not found"));
                Wallet receiverWallet = walletRepository.findByUserId(receiver.getId())
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                                                "Recipient wallet not found"));

                BigDecimal amount = request.amount();
                if (senderWallet.getBalance().compareTo(amount) < 0) {
                        throw new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
                }
                if (senderWallet.getDailySpent().add(amount).compareTo(DAILY_LIMIT) > 0) {
                        throw blocked(sender, receiver, amount, FraudAlertType.DAILY_LIMIT,
                                        "Daily limit of " + money(DAILY_LIMIT) + " would be exceeded",
                                        "Transaction blocked: daily limit exceeded");
                }
                OffsetDateTime duplicateWindowStart = OffsetDateTime.now().minusMinutes(DUPLICATE_WINDOW_MINUTES);
                if (transactionRepository.existsBySenderWalletIdAndReceiverWalletIdAndAmountAndInitiatedAtAfter(
                                senderWallet.getId(), receiverWallet.getId(), amount, duplicateWindowStart)) {
                        throw blocked(sender, receiver, amount, FraudAlertType.DUPLICATE,
                                        "Same amount to the same recipient within " + DUPLICATE_WINDOW_MINUTES
                                                        + " minutes",
                                        "Transaction blocked: possible duplicate transaction");
                }

                OffsetDateTime now = OffsetDateTime.now();
                senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
                senderWallet.setDailySpent(senderWallet.getDailySpent().add(amount));
                senderWallet.setUpdatedAt(now);
                walletRepository.save(senderWallet);

                Transaction transaction = new Transaction();
                transaction.setRequestId(request.requestId());
                transaction.setType(TransactionType.TRANSFER);
                transaction.setSenderWallet(senderWallet);
                transaction.setReceiverWallet(receiverWallet);
                transaction.setAmount(amount);
                transaction.setRemark(request.remark());
                transaction.setStatus(TransactionStatus.PENDING);
                transaction.setFlagged(amount.compareTo(HIGH_VALUE_THRESHOLD) > 0);
                transaction.setInitiatedAt(now);
                // transaction.setSettlementDueDate(LocalDate.now());
                // System.out.println(LocalDate.now());
                transaction.setSettlementDueDate(WalletService.today().plusDays(SETTLEMENT_DAYS));
                transactionRepository.save(transaction);

                saveLedger(transaction, senderWallet, LedgerEntryType.DEBIT, amount, senderWallet.getBalance());
                notificationService.notifyUser(sender.getId(), NotificationType.TRANSFER_INITIATED,
                                NotificationSeverity.INFO,
                                money(amount) + " transfer to " + receiver.getName()
                                                + " initiated. Expected settlement: "
                                                + transaction.getSettlementDueDate().format(SETTLEMENT_DATE_FORMAT),
                                transaction);

                if (Boolean.TRUE.equals(transaction.getFlagged())) {
                        FraudAlert alert = newAlert(sender, receiver, amount, FraudAlertType.HIGH_VALUE,
                                        "Transaction above " + money(HIGH_VALUE_THRESHOLD));
                        alert.setTransaction(transaction);
                        fraudAlertRepository.save(alert);
                        notificationService.notifyAdmins(NotificationType.FRAUD_ALERT, NotificationSeverity.INFO,
                                        "High-value transaction flagged: " + money(amount) + " by user #"
                                                        + sender.getId(),
                                        transaction);
                }
                return toResponse(transaction,
                                "Transfer initiated. Expected settlement on " + transaction.getSettlementDueDate());
        }

        private BusinessRuleException blocked(User sender, User receiver, BigDecimal amount, FraudAlertType type,
                        String description, String userMessage) {
                fraudAlertRepository.save(newAlert(sender, receiver, amount, type, description));
                notificationService.notifyUser(sender.getId(), NotificationType.TXN_BLOCKED, NotificationSeverity.INFO,
                                userMessage, null);
                notificationService.notifyAdmins(NotificationType.FRAUD_ALERT, NotificationSeverity.INFO,
                                type + " alert: " + money(amount) + " by user #" + sender.getId(), null);
                return new BusinessRuleException(HttpStatus.UNPROCESSABLE_ENTITY, userMessage);
        }

        private FraudAlert newAlert(User sender, User receiver, BigDecimal amount, FraudAlertType type,
                        String description) {
                FraudAlert alert = new FraudAlert();
                alert.setUser(sender);
                alert.setReceiverUser(receiver);
                alert.setAlertType(type);
                alert.setAmount(amount);
                alert.setDescription(description);
                alert.setStatus(FraudAlertStatus.PENDING);
                alert.setCreatedAt(OffsetDateTime.now());
                return alert;
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

        private TransferResponse toResponse(Transaction transaction, String message) {
                return new TransferResponse(transaction.getId(), transaction.getRequestId(), transaction.getStatus(),
                                transaction.getAmount(), transaction.getReceiverWallet().getUser().getName(),
                                transaction.getSettlementDueDate(), message);
        }

        private String money(BigDecimal amount) {
                NumberFormat formatter = NumberFormat.getCurrencyInstance(
                                Locale.forLanguageTag("en-IN"));

                formatter.setMinimumFractionDigits(2);
                formatter.setMaximumFractionDigits(2);

                return formatter.format(amount);
        }
}

package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AddMoneyRequest;
import com.walletledger.walletbackend.dto.response.WalletResponse;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final ZoneId INDIA_TIME = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String email) {
        Wallet wallet = walletForUser(userByEmail(email).getId());
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse addMoney(String email, AddMoneyRequest request) {
        Wallet wallet = walletForUserForUpdate(userByEmail(email).getId());
        requireActive(wallet, "add money to");

        OffsetDateTime now = OffsetDateTime.now();
        wallet.setBalance(wallet.getBalance().add(request.amount()));
        wallet.setUpdatedAt(now);
        walletRepository.save(wallet);

        Transaction transaction = new Transaction();
        transaction.setRequestId(UUID.randomUUID().toString());
        transaction.setType(TransactionType.ADD_MONEY);
        transaction.setReceiverWallet(wallet);
        transaction.setAmount(request.amount());
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setFlagged(false);
        transaction.setInitiatedAt(now);
        transaction.setSettledAt(now);
        transactionRepository.save(transaction);

        saveLedger(transaction, wallet, LedgerEntryType.CREDIT, request.amount(), wallet.getBalance());
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse setOwnStatus(String email, WalletStatus targetStatus) {
        if (targetStatus == WalletStatus.FROZEN) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "Only an admin can freeze a wallet");
        }

        Wallet wallet = walletForUserForUpdate(userByEmail(email).getId());
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN,
                    "Your wallet is frozen. Contact support to unfreeze it");
        }
        wallet.setStatus(targetStatus);
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);
        return toResponse(wallet);
    }

    @Transactional
    public void setFrozenByAdmin(Long userId, boolean freeze) {
        Wallet wallet = walletForUserForUpdate(userId);
        if (freeze && wallet.getStatus() == WalletStatus.FROZEN) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Wallet is already frozen");
        }
        if (!freeze && wallet.getStatus() != WalletStatus.FROZEN) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "Wallet is not frozen");
        }
        wallet.setStatus(freeze ? WalletStatus.FROZEN : WalletStatus.ACTIVE);
        wallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(wallet);
    }

    /** Ensures a wallet can be debited or receive a simulated top-up. */
    public static void requireActive(Wallet wallet, String action) {
        if (wallet.getStatus() == WalletStatus.INACTIVE) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN,
                    "Your wallet is inactive. Reactivate it to " + action + " your wallet");
        }
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN,
                    "Your wallet is frozen. Contact support");
        }
    }

    /** Resets the persisted daily counter before a new day's first outgoing transfer. */
    public static void resetDailySpentIfNeeded(Wallet wallet) {
        LocalDate today = today();
        if (!today.equals(wallet.getDailySpentDate())) {
            wallet.setDailySpent(BigDecimal.ZERO);
            wallet.setDailySpentDate(today);
        }
    }

    public static LocalDate today() {
        return LocalDate.now(INDIA_TIME);
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Wallet walletForUser(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private Wallet walletForUserForUpdate(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private WalletResponse toResponse(Wallet wallet) {
        BigDecimal todaySpending = today().equals(wallet.getDailySpentDate())
                ? wallet.getDailySpent() : BigDecimal.ZERO;
        return new WalletResponse(wallet.getId(), wallet.getBalance(), todaySpending, wallet.getStatus());
    }

    private void saveLedger(Transaction transaction, Wallet wallet, LedgerEntryType type,
                            BigDecimal amount, BigDecimal balanceAfter) {
        LedgerEntry ledgerEntry = new LedgerEntry();
        ledgerEntry.setTransaction(transaction);
        ledgerEntry.setWallet(wallet);
        ledgerEntry.setEntryType(type);
        ledgerEntry.setAmount(amount);
        ledgerEntry.setBalanceAfter(balanceAfter);
        ledgerEntry.setCreatedAt(OffsetDateTime.now());
        ledgerEntryRepository.save(ledgerEntry);
    }
}

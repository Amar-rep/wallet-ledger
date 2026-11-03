package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.walletledger.walletbackend.config.FlexProperties;
import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.entity.LedgerEntry;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FlexCreditRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * All Flex Instant Credit business rules live here.
 * Callers (TransferService, SettlementService, WalletService) pass in
 * already-locked wallet instances and call into this service within the
 * same transaction.
 */
@Service
@RequiredArgsConstructor
public class FlexService {

    private final FlexProperties flexProperties;
    private final FlexCreditRepository flexCreditRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final WalletLockService walletLockService;

    // ── Eligibility ────────────────────────────────────────────────────────────

    /**
     * Returns true only when ALL conditions are met:
     * <ul>
     * <li>Flex is globally enabled.</li>
     * <li>The receiver has opted in.</li>
     * <li>The receiver is ACTIVE.</li>
     * <li>The receiver has no outstanding flex debt.</li>
     * <li>The amount is within the configured max.</li>
     * <li>The transfer is not flagged (or config allows flagged transfers).</li>
     * <li>The system wallet has sufficient balance.</li>
     * </ul>
     *
     * <p>
     * Does NOT require the caller to hold any locks — this is a read-only
     * pre-flight check. Locks are acquired inside {@link #disburse}.
     */
    public boolean isEligible(Wallet receiverWallet, BigDecimal amount, boolean flagged) {
        if (!flexProperties.isEnabled())
            return false;
        if (!receiverWallet.isFlexEnabled())
            return false;
        // Null-safe: walletKind may be null for rows predating the migration
        if (WalletKind.SYSTEM.equals(receiverWallet.getWalletKind()))
            return false;
        // Null-safe: flex_debt may be null for old rows despite DB default
        BigDecimal debt = receiverWallet.getFlexDebt() != null ? receiverWallet.getFlexDebt() : BigDecimal.ZERO;
        if (debt.compareTo(BigDecimal.ZERO) > 0)
            return false;
        if (amount.compareTo(flexProperties.getMaxTransferAmount()) > 0)
            return false;
        if (flagged && !flexProperties.isAllowFlaggedTransfers())
            return false;

        // Quick balance check — advisory only, real check happens under lock in
        // disburse()
        Wallet sysWallet = getSystemWallet();
        return sysWallet.getBalance().compareTo(amount) >= 0;
    }

    // ── Disbursement ───────────────────────────────────────────────────────────

    /**
     * Immediately credits the receiver and debits the system wallet.
     * Must be called within the same database transaction as transfer initiation.
     *
     * @param transaction    the PENDING transfer just created
     * @param senderWallet   the sender's wallet (already saved and balanced)
     * @param receiverWallet the receiver's wallet (not yet locked — lock acquired
     *                       here)
     */
    public void disburse(Transaction transaction, Wallet senderWallet, Wallet receiverWallet) {
        BigDecimal amount = transaction.getAmount();

        // Lock in ascending ID order: sender, receiver, system
        Map<Long, Wallet> locks = walletLockService.lockInOrder(
                senderWallet.getId(),
                receiverWallet.getId(),
                flexProperties.getSystemWalletId());

        Wallet lockedReceiver = locks.get(receiverWallet.getId());
        Wallet systemWallet = locks.get(flexProperties.getSystemWalletId());

        // Re-check eligibility under lock (state may have changed since pre-flight)
        BigDecimal debtUnderLock = lockedReceiver.getFlexDebt() != null ? lockedReceiver.getFlexDebt()
                : BigDecimal.ZERO;
        if (!lockedReceiver.isFlexEnabled()
                || debtUnderLock.compareTo(BigDecimal.ZERO) > 0
                || systemWallet.getBalance().compareTo(amount) < 0) {
            return; // silently skip — eligibility lost between check and lock
        }

        // Debit system wallet
        systemWallet.setBalance(systemWallet.getBalance().subtract(amount));
        systemWallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(systemWallet);
        saveLedger(transaction, systemWallet, LedgerEntryType.FLEX_DISBURSEMENT, amount,
                systemWallet.getBalance());

        // Credit receiver immediately
        lockedReceiver.setBalance(lockedReceiver.getBalance().add(amount));
        lockedReceiver.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(lockedReceiver);
        saveLedger(transaction, lockedReceiver, LedgerEntryType.FLEX_CREDIT, amount,
                lockedReceiver.getBalance());

        // Record the flex credit
        FlexCredit flex = new FlexCredit();
        flex.setTransaction(transaction);
        flex.setSystemWallet(systemWallet);
        flex.setBeneficiaryWallet(lockedReceiver);
        flex.setAmount(amount);
        flex.setAmountRecovered(BigDecimal.ZERO);
        flex.setAmountOutstanding(BigDecimal.ZERO);
        flex.setStatus(FlexCreditStatus.OPEN);
        flex.setDisbursedAt(OffsetDateTime.now());
        flexCreditRepository.save(flex);
    }

    // ── Settlement repayment ───────────────────────────────────────────────────

    /**
     * Called when the original transfer settles successfully.
     * Repays the system wallet with the held funds; does NOT credit the receiver
     * again.
     * Must be called within the same transaction as the settlement update.
     *
     * @param transaction  the transaction being settled
     * @param systemWallet already-locked system wallet
     * @return true if a Flex repayment was performed (caller must skip the normal
     *         receiver credit)
     */
    public boolean repayOnSettlement(Transaction transaction, Wallet systemWallet) {
        Optional<FlexCredit> opt = flexCreditRepository.findByTransactionIdForUpdate(transaction.getId());
        if (opt.isEmpty())
            return false;

        FlexCredit flex = opt.get();
        if (flex.getStatus() != FlexCreditStatus.OPEN)
            return false; // idempotent

        BigDecimal amount = transaction.getAmount();

        // Credit system wallet with the held funds
        systemWallet.setBalance(systemWallet.getBalance().add(amount));
        systemWallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(systemWallet);
        saveLedger(transaction, systemWallet, LedgerEntryType.FLEX_SETTLEMENT, amount,
                systemWallet.getBalance());

        flex.setStatus(FlexCreditStatus.REPAID);
        flex.setSettledAt(OffsetDateTime.now());
        flexCreditRepository.save(flex);
        return true;
    }

    // ── Recovery after original transfer failure ───────────────────────────────

    /**
     * Called when the original transfer fails (declined, failed, admin block).
     * Recovers as much as possible from the receiver's current balance.
     * Must be called within the same transaction as the refund/close.
     *
     * @param transaction       the failing transaction
     * @param beneficiaryWallet the receiver's wallet (already locked by caller)
     * @param systemWallet      the system wallet (already locked by caller)
     */
    public void recoverOnFailure(Transaction transaction, Wallet beneficiaryWallet, Wallet systemWallet) {
        Optional<FlexCredit> opt = flexCreditRepository.findByTransactionIdForUpdate(transaction.getId());
        if (opt.isEmpty())
            return;

        FlexCredit flex = opt.get();
        if (flex.getStatus() != FlexCreditStatus.OPEN)
            return; // idempotent

        BigDecimal flexAmount = flex.getAmount();
        BigDecimal recovered = beneficiaryWallet.getBalance().min(flexAmount);
        BigDecimal outstanding = flexAmount.subtract(recovered);

        // Debit receiver by recovered amount (ignores wallet status — recovery
        // overrides)
        beneficiaryWallet.setBalance(beneficiaryWallet.getBalance().subtract(recovered));
        beneficiaryWallet.setUpdatedAt(OffsetDateTime.now());

        // Credit system wallet
        systemWallet.setBalance(systemWallet.getBalance().add(recovered));
        systemWallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(beneficiaryWallet);
        walletRepository.save(systemWallet);

        saveLedger(transaction, systemWallet, LedgerEntryType.FLEX_RECOVERY, recovered,
                systemWallet.getBalance());

        // Update flex record
        flex.setAmountRecovered(recovered);
        flex.setAmountOutstanding(outstanding);

        if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
            flex.setStatus(FlexCreditStatus.RECOVERED);
            flex.setRecoveredAt(OffsetDateTime.now());
        } else {
            flex.setStatus(FlexCreditStatus.RECOVERING);
            beneficiaryWallet.setFlexDebt(beneficiaryWallet.getFlexDebt().add(outstanding));
            walletRepository.save(beneficiaryWallet);
        }
        flexCreditRepository.save(flex);
    }

    // ── Debt repayment from later inbound funds ────────────────────────────────

    /**
     * Intercepts an inbound credit to a wallet that has Flex debt.
     * Repays the debt first; returns the remainder that should be credited to the
     * customer.
     *
     * <p>
     * Must be called within the same transaction as the inbound credit.
     * The caller should credit ONLY the returned net amount, not the original
     * amount.
     *
     * @param wallet         the receiving wallet (already locked)
     * @param incomingAmount the gross incoming amount
     * @param transaction    the transaction that carries the inbound credit (for
     *                       ledger links)
     * @return the net amount to credit to the customer wallet (incomingAmount minus
     *         any debt repaid)
     */
    public BigDecimal repayDebtFromInbound(Wallet wallet, BigDecimal incomingAmount, Transaction transaction) {
        BigDecimal debt = wallet.getFlexDebt() != null ? wallet.getFlexDebt() : BigDecimal.ZERO;
        if (debt.compareTo(BigDecimal.ZERO) == 0) {
            return incomingAmount; // fast path — no debt
        }

        Wallet systemWallet = walletLockService.lockInOrder(flexProperties.getSystemWalletId())
                .get(flexProperties.getSystemWalletId());

        BigDecimal repayment = incomingAmount.min(debt);
        BigDecimal customerCredit = incomingAmount.subtract(repayment);

        // Credit system wallet
        systemWallet.setBalance(systemWallet.getBalance().add(repayment));
        systemWallet.setUpdatedAt(OffsetDateTime.now());
        walletRepository.save(systemWallet);
        saveLedger(transaction, systemWallet, LedgerEntryType.FLEX_RECOVERY, repayment,
                systemWallet.getBalance());

        // Reduce wallet debt
        wallet.setFlexDebt(debt.subtract(repayment));
        // Note: caller saves the wallet after adding customerCredit to balance

        // Apply repayment to RECOVERING Flex records oldest-first
        applyRepaymentToFlexRecords(wallet.getId(), repayment, transaction);

        return customerCredit;
    }

    // ── Admin write-off ────────────────────────────────────────────────────────

    /**
     * Lists all flex credits, optionally filtered by status.
     */
    public List<FlexCredit> list(FlexCreditStatus status) {
        if (status == null) {
            return flexCreditRepository.findAll();
        }
        return flexCreditRepository.findAll().stream()
                .filter(fc -> fc.getStatus() == status)
                .toList();
    }

    /**
     * Writes off unrecoverable outstanding Flex debt. Admin-only operation.
     * Records actor, reason, and timestamp. Emits a FLEX_WRITE_OFF ledger entry.
     *
     * @param flexCreditId the ID of the FlexCredit record
     * @param adminEmail   email of the admin performing the write-off
     * @param reason       mandatory reason text
     */
    public void writeOff(Long flexCreditId, String adminEmail, String reason) {
        FlexCredit flex = flexCreditRepository.findById(flexCreditId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "Flex credit record not found: " + flexCreditId));

        if (flex.getStatus() != FlexCreditStatus.RECOVERING) {
            throw new BusinessRuleException(HttpStatus.CONFLICT,
                    "Only RECOVERING Flex records can be written off. Current status: " + flex.getStatus());
        }

        BigDecimal outstanding = flex.getAmountOutstanding();

        // Reduce beneficiary debt — lock beneficiary wallet
        Wallet beneficiary = walletLockService.lockInOrder(flex.getBeneficiaryWallet().getId())
                .get(flex.getBeneficiaryWallet().getId());
        BigDecimal newDebt = beneficiary.getFlexDebt().subtract(outstanding);
        beneficiary.setFlexDebt(newDebt.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newDebt);
        walletRepository.save(beneficiary);

        // Emit a FLEX_WRITE_OFF ledger entry against the system wallet (read-only
        // balance snapshot)
        Wallet systemWallet = walletRepository.findById(flex.getSystemWallet().getId())
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "System wallet not found"));
        saveLedger(flex.getTransaction(), systemWallet, LedgerEntryType.FLEX_WRITE_OFF, outstanding,
                systemWallet.getBalance());

        flex.setStatus(FlexCreditStatus.WRITTEN_OFF);
        flex.setWrittenOffAt(OffsetDateTime.now());
        flex.setWrittenOffBy(adminEmail);
        flex.setWriteOffReason(reason);
        flexCreditRepository.save(flex);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void applyRepaymentToFlexRecords(Long walletId, BigDecimal totalRepayment,
            Transaction repayingTransaction) {
        List<FlexCredit> recovering = flexCreditRepository
                .findByBeneficiaryWalletIdAndStatusOrderByDisbursedAt(walletId, FlexCreditStatus.RECOVERING);

        BigDecimal remaining = totalRepayment;
        for (FlexCredit fc : recovering) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0)
                break;

            BigDecimal apply = remaining.min(fc.getAmountOutstanding());
            fc.setAmountRecovered(fc.getAmountRecovered().add(apply));
            fc.setAmountOutstanding(fc.getAmountOutstanding().subtract(apply));
            remaining = remaining.subtract(apply);

            if (fc.getAmountOutstanding().compareTo(BigDecimal.ZERO) == 0) {
                fc.setStatus(FlexCreditStatus.RECOVERED);
                fc.setRecoveredAt(OffsetDateTime.now());
            }
            flexCreditRepository.save(fc);
        }
    }

    private Wallet getSystemWallet() {
        return walletRepository.findById(flexProperties.getSystemWalletId())
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "System wallet not found"));
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
}

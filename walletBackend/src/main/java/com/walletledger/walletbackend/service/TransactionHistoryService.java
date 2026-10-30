package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.request.TransactionFilter;
import com.walletledger.walletbackend.dto.response.LedgerEntryResponse;
import com.walletledger.walletbackend.dto.response.TransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.TransactionSummaryResponse;
import com.walletledger.walletbackend.dto.response.UserStatsResponse;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private static final ZoneId INDIA_TIME = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public List<TransactionSummaryResponse> miniStatement(String email) {
        Wallet wallet = wallet(email);
        return page(wallet, new TransactionFilter(null, null, TransactionDirection.ALL, TransactionStatusFilter.ALL, 0, 10))
                .content();
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionSummaryResponse> history(String email, TransactionFilter filter) {
        return page(wallet(email), filter);
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse detail(String email, Long transactionId) {
        Wallet wallet = wallet(email);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Transaction not found"));

        boolean isSender = transaction.getSenderWallet() != null
                && transaction.getSenderWallet().getId().equals(wallet.getId());
        boolean isReceiver = transaction.getReceiverWallet().getId().equals(wallet.getId());
        if (!(isSender || (isReceiver && transaction.getStatus() == TransactionStatus.SUCCESS))) {
            throw new BusinessRuleException(HttpStatus.NOT_FOUND, "Transaction not found");
        }

        List<LedgerEntryResponse> entries = ledgerEntryRepository
                .findByTransactionIdOrderByCreatedAtAsc(transactionId).stream()
                .map(entry -> new LedgerEntryResponse(
                        entry.getId(),
                        entry.getWallet().getId(),
                        entry.getEntryType(),
                        entry.getAmount(),
                        entry.getBalanceAfter(),
                        toInstant(entry.getCreatedAt())))
                .toList();

        boolean pending = transaction.getStatus() == TransactionStatus.PENDING;
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getRequestId(),
                transaction.getType(),
                isSender ? TransactionDirection.SENT : TransactionDirection.RECEIVED,
                transaction.getSenderWallet() == null ? null : transaction.getSenderWallet().getUser().getName(),
                transaction.getReceiverWallet().getUser().getName(),
                transaction.getAmount(),
                transaction.getRemark(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                toInstant(transaction.getInitiatedAt()),
                pending ? transaction.getSettlementDueDate() : null,
                daysRemaining(transaction),
                toInstant(transaction.getSettledAt()),
                entries);
    }

    @Transactional(readOnly = true)
    public UserStatsResponse stats(String email) {
        Wallet wallet = wallet(email);
        BigDecimal sent = transactionRepository.sumSent(
                wallet.getId(), List.of(TransactionStatus.PENDING, TransactionStatus.SUCCESS));
        BigDecimal received = transactionRepository.sumReceived(
                wallet.getId(), TransactionType.TRANSFER, TransactionStatus.SUCCESS);
        OffsetDateTime monthStart = WalletService.today().withDayOfMonth(1)
                .atStartOfDay(INDIA_TIME)
                .toOffsetDateTime();
        long count = transactionRepository.countVisibleSince(wallet.getId(), TransactionStatus.SUCCESS, monthStart);
        return new UserStatsResponse(sent, received, count);
    }

    private PageResponse<TransactionSummaryResponse> page(Wallet wallet, TransactionFilter filter) {
        int page = filter.page() == null ? 0 : Math.max(0, filter.page());
        int size = filter.size() == null ? 10 : Math.min(Math.max(1, filter.size()), 100);
        Page<Transaction> result = transactionRepository.findAll(
                spec(wallet.getId(), filter),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "initiatedAt", "id")));
        return new PageResponse<>(
                result.map(transaction -> toSummary(transaction, wallet.getId())).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * The user sees every transaction they sent (any status), and received rows only once SUCCESS
     * (add-money top-ups count as received).
     */
    Specification<Transaction> spec(Long walletId, TransactionFilter filter) {
        return (root, query, cb) -> {
            Join<Transaction, Wallet> sender = root.join("senderWallet", JoinType.LEFT);
            Join<Transaction, Wallet> receiver = root.join("receiverWallet", JoinType.INNER);

            Predicate sent = cb.equal(sender.get("id"), walletId);
            Predicate received = cb.and(
                    cb.equal(receiver.get("id"), walletId),
                    cb.equal(root.get("status"), TransactionStatus.SUCCESS));

            List<Predicate> predicates = new ArrayList<>();
            TransactionDirection direction = filter.type() == null ? TransactionDirection.ALL : filter.type();
            predicates.add(switch (direction) {
                case SENT -> sent;
                case RECEIVED -> received;
                case ALL -> cb.or(sent, received);
            });

            if (filter.fromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("initiatedAt"),
                        filter.fromDate().atStartOfDay(INDIA_TIME).toOffsetDateTime()));
            }
            if (filter.toDate() != null) {
                predicates.add(cb.lessThan(root.get("initiatedAt"),
                        filter.toDate().plusDays(1).atStartOfDay(INDIA_TIME).toOffsetDateTime()));
            }

            TransactionStatusFilter statusFilter = filter.status() == null
                    ? TransactionStatusFilter.ALL : filter.status();
            switch (statusFilter) {
                case PENDING, SUCCESS, DECLINED ->
                        predicates.add(cb.equal(root.get("status"), TransactionStatus.valueOf(statusFilter.name())));
                case FAILED -> predicates.add(root.get("status").in(TransactionStatus.FAILED, TransactionStatus.DECLINED));
                case ALL -> { }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TransactionSummaryResponse toSummary(Transaction transaction, Long myWalletId) {
        boolean sent = transaction.getSenderWallet() != null
                && transaction.getSenderWallet().getId().equals(myWalletId);
        String counterparty = sent
                ? transaction.getReceiverWallet().getUser().getName()
                : (transaction.getSenderWallet() == null ? "Wallet top-up" : transaction.getSenderWallet().getUser().getName());
        boolean pending = transaction.getStatus() == TransactionStatus.PENDING;
        return new TransactionSummaryResponse(
                transaction.getId(),
                transaction.getType(),
                sent ? TransactionDirection.SENT : TransactionDirection.RECEIVED,
                counterparty,
                transaction.getAmount(),
                transaction.getRemark(),
                transaction.getStatus(),
                toInstant(transaction.getInitiatedAt()),
                pending ? transaction.getSettlementDueDate() : null,
                daysRemaining(transaction));
    }

    Long daysRemaining(Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.PENDING || transaction.getSettlementDueDate() == null) {
            return null;
        }
        return Math.max(0, ChronoUnit.DAYS.between(WalletService.today(), transaction.getSettlementDueDate()));
    }

    private Wallet wallet(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED, "User not found"));
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private Instant toInstant(OffsetDateTime value) {
        return Optional.ofNullable(value).map(OffsetDateTime::toInstant).orElse(null);
    }
}

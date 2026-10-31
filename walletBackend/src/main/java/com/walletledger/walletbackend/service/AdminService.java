package com.walletledger.walletbackend.service;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AdminTransactionFilter;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.request.WalletFreezeRequest;
import com.walletledger.walletbackend.dto.response.AdminTransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminTransactionResponse;
import com.walletledger.walletbackend.dto.response.AdminUserDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminUserResponse;
import com.walletledger.walletbackend.dto.response.DailyVolumePoint;
import com.walletledger.walletbackend.dto.response.DashboardResponse;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.dto.response.JobRunResponse;
import com.walletledger.walletbackend.dto.response.LedgerEntryResponse;
import com.walletledger.walletbackend.dto.response.ReconciliationResponse;
import com.walletledger.walletbackend.dto.response.SettlementBatchResponse;
import com.walletledger.walletbackend.dto.response.SettlementMonitorResponse;
import com.walletledger.walletbackend.dto.response.TransactionSummaryResponse;
import com.walletledger.walletbackend.entity.AuditLog;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.AuditLogRepository;
import com.walletledger.walletbackend.repository.DailySummaryRepository;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.ScheduledJobLogRepository;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import com.walletledger.walletbackend.service.FraudAdminService;
import com.walletledger.walletbackend.service.SettlementOutcome;
import com.walletledger.walletbackend.service.SettlementService;
import com.walletledger.walletbackend.service.WalletService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

        private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
        private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

        private final FraudAdminService fraudAdminService;
        private final WalletService walletService;
        private final SettlementService settlementService;
        private final TransactionRepository transactionRepository;
        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
        private final LedgerEntryRepository ledgerEntryRepository;
        private final ScheduledJobLogRepository scheduledJobLogRepository;
        private final DailySummaryRepository dailySummaryRepository;
        private final FraudAlertRepository fraudAlertRepository;
        private final SettlementBatchRepository settlementBatchRepository;
        private final AuditLogRepository auditLogRepository;

        // ── Dashboard ────────────────────────────────────────────────────────────────
        public DashboardResponse dashboard() {
                LocalDate today = WalletService.today();
                OffsetDateTime from = today.atStartOfDay(IST).toOffsetDateTime();
                OffsetDateTime to = today.plusDays(1).atStartOfDay(IST).toOffsetDateTime();
                TransactionType tf = TransactionType.TRANSFER;

                long totalUsers = userRepository.count();
                long initiated = transactionRepository.countInitiated(tf, from, to);
                long settled = transactionRepository.countSettled(tf, TransactionStatus.SUCCESS, from, to);
                BigDecimal volume = orZero(transactionRepository.sumSettled(tf, TransactionStatus.SUCCESS, from, to));
                BigDecimal pending = orZero(transactionRepository.sumByStatus(tf, TransactionStatus.PENDING));
                long fraudPending = fraudAlertRepository.findByStatusOrderByCreatedAtDesc(FraudAlertStatus.PENDING)
                                .size();

                List<DailyVolumePoint> last30 = new ArrayList<>();
                for (int i = 29; i >= 0; i--) {
                        LocalDate d = today.minusDays(i);
                        OffsetDateTime df = d.atStartOfDay(IST).toOffsetDateTime();
                        OffsetDateTime dt = d.plusDays(1).atStartOfDay(IST).toOffsetDateTime();
                        BigDecimal vol = orZero(
                                        transactionRepository.sumSettled(tf, TransactionStatus.SUCCESS, df, dt));
                        last30.add(new DailyVolumePoint(d, vol));
                }
                return new DashboardResponse(totalUsers, initiated, settled, volume, pending, fraudPending, last30);
        }

        // ── Users ────────────────────────────────────────────────────────────────────
        public PageResponse<AdminUserResponse> users(
                        @RequestParam(required = false) String search,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {

                PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
                Page<User> result = userRepository.findAll(pr);

                List<AdminUserResponse> content = result.getContent().stream()
                                .filter(u -> search == null || search.isBlank()
                                                || u.getName().toLowerCase().contains(search.toLowerCase())
                                                || u.getEmail().toLowerCase().contains(search.toLowerCase())
                                                || u.getPhone().contains(search))
                                .map(this::toAdminUserResponse)
                                .toList();

                return new PageResponse<>(content, result.getNumber(), result.getSize(),
                                result.getTotalElements(), result.getTotalPages());
        }

        public AdminUserDetailResponse userDetail(@PathVariable Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "User not found"));
                Wallet wallet = walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found"));

                List<TransactionSummaryResponse> recent = transactionRepository.findAll(
                                (root, query, cb) -> {
                                        Join<Transaction, Wallet> sender = root.join("senderWallet", JoinType.LEFT);
                                        Join<Transaction, Wallet> receiver = root.join("receiverWallet",
                                                        JoinType.INNER);
                                        return cb.or(cb.equal(sender.get("id"), wallet.getId()),
                                                        cb.equal(receiver.get("id"), wallet.getId()));
                                },
                                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "initiatedAt", "id")))
                                .getContent().stream()
                                .map(t -> toTxnSummary(t, wallet.getId()))
                                .toList();

                return new AdminUserDetailResponse(toAdminUserResponse(user), recent);
        }

        public MessageResponse freezeWallet(Principal principal, @PathVariable Long userId,
                        @RequestBody(required = false) WalletFreezeRequest ignoredRequest) {
                walletService.setFrozenByAdmin(userId, true);
                audit(principal, "FREEZE_WALLET", "WALLET", userId);
                return new MessageResponse("Wallet frozen");
        }

        public MessageResponse unfreezeWallet(Principal principal, @PathVariable Long userId,
                        @RequestBody(required = false) WalletFreezeRequest ignoredRequest) {
                walletService.setFrozenByAdmin(userId, false);
                audit(principal, "UNFREEZE_WALLET", "WALLET", userId);
                return new MessageResponse("Wallet unfrozen");
        }

        // ── Transactions ─────────────────────────────────────────────────────────────
        public PageResponse<AdminTransactionResponse> transactions(@ModelAttribute AdminTransactionFilter filter) {
                int pg = filter.page() == null ? 0 : Math.max(0, filter.page());
                int sz = filter.size() == null ? 20 : Math.min(Math.max(1, filter.size()), 100);

                Specification<Transaction> spec = (root, query, cb) -> {
                        List<Predicate> preds = new ArrayList<>();
                        if (filter.fromDate() != null)
                                preds.add(cb.greaterThanOrEqualTo(root.get("initiatedAt"),
                                                filter.fromDate().atStartOfDay(IST).toOffsetDateTime()));
                        if (filter.toDate() != null)
                                preds.add(cb.lessThan(root.get("initiatedAt"),
                                                filter.toDate().plusDays(1).atStartOfDay(IST).toOffsetDateTime()));
                        if (filter.status() != null && filter.status() != TransactionStatusFilter.ALL)
                                preds.add(cb.equal(root.get("status"),
                                                TransactionStatus.valueOf(filter.status().name())));
                        if (filter.minAmount() != null)
                                preds.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
                        if (filter.maxAmount() != null)
                                preds.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
                        return cb.and(preds.toArray(new Predicate[0]));
                };

                Page<Transaction> result = transactionRepository.findAll(spec,
                                PageRequest.of(pg, sz, Sort.by(Sort.Direction.DESC, "initiatedAt", "id")));

                List<AdminTransactionResponse> content = result.getContent().stream()
                                .map(t -> new AdminTransactionResponse(
                                                t.getId(),
                                                t.getSenderWallet() == null ? null
                                                                : t.getSenderWallet().getUser().getName(),
                                                t.getReceiverWallet().getUser().getName(),
                                                t.getAmount(), t.getStatus(),
                                                Boolean.TRUE.equals(t.getFlagged()),
                                                toInstant(t.getInitiatedAt()), t.getSettlementDueDate()))
                                .toList();

                return new PageResponse<>(content, result.getNumber(), result.getSize(),
                                result.getTotalElements(), result.getTotalPages());
        }

        public AdminTransactionDetailResponse transactionDetail(@PathVariable Long id) {
                Transaction t = transactionRepository.findById(id)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                                                "Transaction not found"));

                List<LedgerEntryResponse> entries = ledgerEntryRepository
                                .findByTransactionIdOrderByCreatedAtAsc(id).stream()
                                .map(e -> new LedgerEntryResponse(e.getId(), e.getWallet().getId(),
                                                e.getEntryType(), e.getAmount(), e.getBalanceAfter(),
                                                toInstant(e.getCreatedAt())))
                                .toList();

                return new AdminTransactionDetailResponse(
                                t.getId(), t.getRequestId(), t.getType(),
                                t.getSenderWallet() == null ? null : t.getSenderWallet().getUser().getId(),
                                t.getSenderWallet() == null ? null : t.getSenderWallet().getUser().getName(),
                                t.getReceiverWallet().getUser().getId(),
                                t.getReceiverWallet().getUser().getName(),
                                t.getAmount(), t.getRemark(), t.getStatus(), t.getFailureReason(),
                                Boolean.TRUE.equals(t.getFlagged()),
                                toInstant(t.getInitiatedAt()), t.getSettlementDueDate(),
                                toInstant(t.getSettledAt()),
                                t.getSettlementBatch() == null ? null : t.getSettlementBatch().getId(),
                                entries);
        }

        // ── Settlement monitor
        // ────────────────────────────────────────────────────────
        public SettlementMonitorResponse settlements() {
                LocalDate today = WalletService.today();
                List<Transaction> all = transactionRepository.findAll(
                                (root, query, cb) -> cb.and(
                                                cb.equal(root.get("type"), TransactionType.TRANSFER),
                                                cb.equal(root.get("status"), TransactionStatus.PENDING)));

                long dueToday = all.stream().filter(t -> today.equals(t.getSettlementDueDate())).count();
                long dueTomorrow = all.stream().filter(t -> today.plusDays(1).equals(t.getSettlementDueDate())).count();
                long overdue = all.stream().filter(t -> t.getSettlementDueDate() != null
                                && t.getSettlementDueDate().isBefore(today)).count();
                BigDecimal total = all.stream().map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<AdminTransactionResponse> txns = all.stream()
                                .sorted((a, b) -> {
                                        if (a.getSettlementDueDate() == null)
                                                return 1;
                                        if (b.getSettlementDueDate() == null)
                                                return -1;
                                        return a.getSettlementDueDate().compareTo(b.getSettlementDueDate());
                                })
                                .map(t -> new AdminTransactionResponse(
                                                t.getId(),
                                                t.getSenderWallet() == null ? null
                                                                : t.getSenderWallet().getUser().getName(),
                                                t.getReceiverWallet().getUser().getName(),
                                                t.getAmount(), t.getStatus(),
                                                Boolean.TRUE.equals(t.getFlagged()),
                                                toInstant(t.getInitiatedAt()), t.getSettlementDueDate()))
                                .toList();

                return new SettlementMonitorResponse(dueToday, dueTomorrow, overdue, total, txns);
        }

        /** Manually settle a single pending transaction immediately. */
        public MessageResponse manualSettle(Principal principal, @PathVariable Long id) {
                SettlementOutcome outcome = settlementService.settle(id, null);

                audit(principal, "MANUAL_SETTLE", "TRANSACTION", id);
                return switch (outcome) {
                        case SETTLED -> new MessageResponse("Transaction settled successfully");
                        case DECLINED -> new MessageResponse("Settlement declined: receiver wallet is not active");
                        case SKIPPED -> new MessageResponse("Transaction was already processed");
                };
        }

        // ── Fraud alerts
        // ──────────────────────────────────────────────────────────────
        public List<FraudAlertResponse> fraudAlerts(
                        @RequestParam(defaultValue = "PENDING") FraudAlertStatus status) {
                return fraudAdminService.list(status);
        }

        public FraudAlertResponse reviewFraudAlert(Principal principal, @PathVariable Long alertId,
                        @Valid @RequestBody FraudReviewRequest request) {
                FraudAlertResponse result = fraudAdminService.review(principal.getName(), alertId, request);
                audit(principal, "FRAUD_REVIEW_" + request.action().name(), "FRAUD_ALERT", alertId);
                return result;
        }

        // ── Job monitor
        // ───────────────────────────────────────────────────────────────
        public List<JobRunResponse> jobs(@RequestParam(required = false) String jobName) {
                return scheduledJobLogRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt"))
                                .stream()
                                .filter(j -> jobName == null || jobName.isBlank() || j.getJobName().equals(jobName))
                                .limit(20)
                                .map(j -> {
                                        SettlementBatch batch = settlementBatchRepository.findByJobLogId(j.getId())
                                                        .orElse(null);
                                        SettlementBatchResponse batchResponse = batch == null ? null
                                                        : new SettlementBatchResponse(
                                                                        batch.getId(),
                                                                        batch.getSettledCount() == null ? 0
                                                                                        : batch.getSettledCount(),
                                                                        batch.getFailedCount() == null ? 0
                                                                                        : batch.getFailedCount(),
                                                                        batch.getDeclinedCount() == null ? 0
                                                                                        : batch.getDeclinedCount(),
                                                                        batch.getTotalAmountSettled() == null
                                                                                        ? BigDecimal.ZERO
                                                                                        : batch.getTotalAmountSettled());
                                        return new JobRunResponse(
                                                        j.getId(), j.getJobName(),
                                                        toInstant(j.getStartedAt()), toInstant(j.getEndedAt()),
                                                        j.getStatus(),
                                                        j.getRecordsProcessed() == null ? 0 : j.getRecordsProcessed(),
                                                        j.getErrorMessage(),
                                                        batchResponse);
                                })
                                .toList();
        }

        // ── Reconciliation
        // ────────────────────────────────────────────────────────────
        public ReconciliationResponse reconciliation(
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

                LocalDate target = date != null ? date : WalletService.today();
                OffsetDateTime from = target.atStartOfDay(IST).toOffsetDateTime();
                OffsetDateTime to = target.plusDays(1).atStartOfDay(IST).toOffsetDateTime();

                BigDecimal debits = orZero(ledgerEntryRepository.sumForSettled(
                                LedgerEntryType.DEBIT, TransactionType.TRANSFER, TransactionStatus.SUCCESS, from, to));
                BigDecimal credits = orZero(ledgerEntryRepository.sumForSettled(
                                LedgerEntryType.CREDIT, TransactionType.TRANSFER, TransactionStatus.SUCCESS, from, to));
                BigDecimal pendingAmt = orZero(transactionRepository.sumByStatus(
                                TransactionType.TRANSFER, TransactionStatus.PENDING));

                boolean mismatch = debits.compareTo(credits) != 0;
                BigDecimal diff = debits.subtract(credits).abs();

                return new ReconciliationResponse(target, debits, credits, pendingAmt, mismatch, diff);
        }

        // ── Helpers
        // ───────────────────────────────────────────────────────────────────

        private void audit(Principal principal, String action, String targetType, Long targetId) {
                try {
                        AuditLog log = new AuditLog();
                        log.setAction(action);
                        log.setTargetType(targetType);
                        log.setTargetId(targetId);
                        log.setDetails(Map.of());
                        log.setCreatedAt(OffsetDateTime.now());
                        if (principal != null) {
                                userRepository.findByEmail(principal.getName()).ifPresent(log::setAdmin);
                        }
                        auditLogRepository.saveAndFlush(log);
                } catch (Exception exception) {
                        // Audit failures must not break the main operation, but must be visible.
                        logger.error("Failed to persist audit log: action={}, targetType={}, targetId={}, principal={}",
                                        action, targetType, targetId,
                                        principal == null ? null : principal.getName(), exception);
                }
        }

        private AdminUserResponse toAdminUserResponse(User u) {
                Wallet w = walletRepository.findByUserId(u.getId()).orElse(null);
                return new AdminUserResponse(
                                u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(),
                                w != null ? w.getBalance() : BigDecimal.ZERO,
                                w != null ? w.getStatus() : WalletStatus.INACTIVE,
                                toInstant(u.getCreatedAt()));
        }

        private TransactionSummaryResponse toTxnSummary(Transaction t, Long myWalletId) {
                boolean sent = t.getSenderWallet() != null && t.getSenderWallet().getId().equals(myWalletId);
                String counterparty = sent
                                ? t.getReceiverWallet().getUser().getName()
                                : (t.getSenderWallet() == null ? "Top-up" : t.getSenderWallet().getUser().getName());
                boolean pending = t.getStatus() == TransactionStatus.PENDING;
                return new TransactionSummaryResponse(
                                t.getId(), t.getType(),
                                sent ? TransactionDirection.SENT : TransactionDirection.RECEIVED,
                                counterparty, t.getAmount(), t.getRemark(), t.getStatus(),
                                toInstant(t.getInitiatedAt()),
                                pending ? t.getSettlementDueDate() : null,
                                daysRemaining(t));
        }

        private static Long daysRemaining(Transaction t) {
                if (t.getStatus() != TransactionStatus.PENDING || t.getSettlementDueDate() == null)
                        return null;
                return Math.max(0, ChronoUnit.DAYS.between(WalletService.today(), t.getSettlementDueDate()));
        }

        private static Instant toInstant(OffsetDateTime v) {
                return Optional.ofNullable(v).map(OffsetDateTime::toInstant).orElse(null);
        }

        private static BigDecimal orZero(BigDecimal v) {
                return v == null ? BigDecimal.ZERO : v;
        }
}

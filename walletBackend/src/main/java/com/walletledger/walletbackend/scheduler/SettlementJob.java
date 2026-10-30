package com.walletledger.walletbackend.scheduler;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.service.SettlementService;
import com.walletledger.walletbackend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementJob {

    private final JobRunner jobRunner;
    private final TransactionRepository transactionRepository;
    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementService settlementService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Kolkata")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Settlement job skipped: previous run still in progress");
            return;
        }
        try {
            jobRunner.run("ACH_SETTLEMENT", this::settleDue);
        } finally {
            running.set(false);
        }
    }

    /** Not transactional: each transfer commits on its own inside SettlementService. */
    JobResult settleDue(ScheduledJobLog jobLog) {
        List<Long> dueIds = transactionRepository.findDueIds(TransactionStatus.PENDING, WalletService.today());

        SettlementBatch batch = new SettlementBatch();
        batch.setJobLog(jobLog);
        batch.setStartedAt(OffsetDateTime.now());
        batch = settlementBatchRepository.save(batch);

        int settled = 0;
        int failed = 0;
        int declined = 0;
        for (Long transactionId : dueIds) {
            try {
                switch (settlementService.settle(transactionId, batch.getId())) {
                    case SETTLED -> settled++;
                    case DECLINED -> declined++;
                    case SKIPPED -> { }
                }
            } catch (Exception exception) {
                failed++;
                log.error("Settlement failed for txn {}", transactionId, exception);
                try {
                    settlementService.fail(
                            transactionId,
                            JobRunner.truncate("Settlement error: " + exception.getMessage()),
                            batch.getId());
                } catch (Exception inner) {
                    log.error("Could not refund txn {}", transactionId, inner);
                }
            }
        }

        batch.setSettledCount(settled);
        batch.setFailedCount(failed);
        batch.setDeclinedCount(declined);
        batch.setTotalAmountSettled(
                transactionRepository.sumSettledInBatch(batch.getId(), TransactionStatus.SUCCESS));
        batch.setEndedAt(OffsetDateTime.now());
        settlementBatchRepository.save(batch);

        String note = failed > 0 ? failed + " of " + dueIds.size() + " settlements failed" : null;
        return new JobResult(dueIds.size(), note);
    }
}

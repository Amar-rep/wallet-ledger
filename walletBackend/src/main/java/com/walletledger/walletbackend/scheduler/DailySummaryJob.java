package com.walletledger.walletbackend.scheduler;

import com.walletledger.walletbackend.entity.DailySummary;
import com.walletledger.walletbackend.service.DailySummaryService;
import com.walletledger.walletbackend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailySummaryJob {

    private final JobRunner jobRunner;
    private final DailySummaryService dailySummaryService;

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Kolkata")
    public void run() {
        jobRunner.run("DAILY_SUMMARY", entry -> {
            DailySummary summary = dailySummaryService.generate(WalletService.today());
            return new JobResult(
                    summary.getTransfersInitiated() + summary.getTransfersSettled(),
                    Boolean.TRUE.equals(summary.getReconciliationOk()) ? null : "Reconciliation mismatch detected");
        });
    }
}

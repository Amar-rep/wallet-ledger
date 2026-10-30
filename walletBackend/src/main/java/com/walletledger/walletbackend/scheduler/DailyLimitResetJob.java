package com.walletledger.walletbackend.scheduler;

import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyLimitResetJob {

    private final JobRunner jobRunner;
    private final WalletRepository walletRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void run() {
        jobRunner.run("DAILY_LIMIT_RESET", entry -> new JobResult(walletRepository.resetDailySpent(), null));
    }
}

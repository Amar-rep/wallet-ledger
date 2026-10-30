package com.walletledger.walletbackend.scheduler;

import com.walletledger.walletbackend.service.AccountUnlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountUnlockJob {

    private final JobRunner jobRunner;
    private final AccountUnlockService accountUnlockService;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Kolkata")
    public void run() {
        jobRunner.run("ACCOUNT_AUTO_UNLOCK", entry -> new JobResult(accountUnlockService.unlockExpired(), null));
    }
}

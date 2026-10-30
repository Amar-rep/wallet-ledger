package com.walletledger.walletbackend.scheduler;

import com.walletledger.walletbackend.entity.ScheduledJobLog;

@FunctionalInterface
interface JobWork {

    JobResult execute(ScheduledJobLog entry) throws Exception;
}

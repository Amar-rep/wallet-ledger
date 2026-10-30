package com.walletledger.walletbackend.scheduler;

import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.repository.ScheduledJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {

    private final ScheduledJobLogRepository scheduledJobLogRepository;

    public void run(String jobName, JobWork work) {
        ScheduledJobLog entry = new ScheduledJobLog();
        entry.setJobName(jobName);
        entry.setStartedAt(OffsetDateTime.now());
        entry.setStatus(ScheduledJobStatus.RUNNING);
        entry.setRecordsProcessed(0);
        entry = scheduledJobLogRepository.save(entry);

        try {
            JobResult result = work.execute(entry);
            entry.setStatus(ScheduledJobStatus.SUCCESS);
            entry.setRecordsProcessed(result.records());
            entry.setErrorMessage(result.message());
        } catch (Exception exception) {
            log.error("Job {} failed", jobName, exception);
            entry.setStatus(ScheduledJobStatus.FAILED);
            entry.setErrorMessage(truncate(exception.toString()));
        }
        entry.setEndedAt(OffsetDateTime.now());
        scheduledJobLogRepository.save(entry);
    }

    static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}

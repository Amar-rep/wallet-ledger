package com.walletledger.walletbackend.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.repository.ScheduledJobLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobRunnerTest {

    private ScheduledJobLogRepository repository;
    private JobRunner jobRunner;

    @BeforeEach
    void setUp() {
        repository = mock(ScheduledJobLogRepository.class);
        when(repository.save(any(ScheduledJobLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        jobRunner = new JobRunner(repository);
    }

    @Test
    void recordsSuccessWhenWorkCompletes() {
        jobRunner.run("ACH_SETTLEMENT", entry -> new JobResult(4, "note"));

        ArgumentCaptor<ScheduledJobLog> captor = ArgumentCaptor.forClass(ScheduledJobLog.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        ScheduledJobLog finished = captor.getValue();
        assertEquals(ScheduledJobStatus.SUCCESS, finished.getStatus());
        assertEquals(4, finished.getRecordsProcessed());
        assertEquals("note", finished.getErrorMessage());
    }

    @Test
    void recordsFailedOnlyWhenTheJobItselfThrows() {
        jobRunner.run("ACH_SETTLEMENT", entry -> {
            throw new IllegalStateException("boom");
        });

        ArgumentCaptor<ScheduledJobLog> captor = ArgumentCaptor.forClass(ScheduledJobLog.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        ScheduledJobLog finished = captor.getValue();
        assertEquals(ScheduledJobStatus.FAILED, finished.getStatus());
        assertTrue(finished.getErrorMessage().contains("boom"));
    }
}

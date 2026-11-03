package com.walletledger.walletbackend.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import com.walletledger.walletbackend.entity.DailySummary;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.service.DailySummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.scheduling.annotation.Scheduled;

class DailySummaryJobTest {

    private JobRunner jobRunner;
    private DailySummaryService dailySummaryService;
    private DailySummaryJob job;

    @BeforeEach
    void setUp() {
        jobRunner = mock(JobRunner.class);
        dailySummaryService = mock(DailySummaryService.class);
        job = new DailySummaryJob(jobRunner, dailySummaryService);
    }

    @Test
    void runExecutesJobRunnerWithDailySummary() {
        job.run();
        verify(jobRunner).run(eq("DAILY_SUMMARY"), any());
    }

    @ParameterizedTest
    @MethodSource("reconciliationScenarios")
    void jobWorkHandlesMatchedAndMismatchedReconciliation(boolean ok, int initiated, int settled, String expectedNote) {
        DailySummary summary = new DailySummary();
        summary.setTransfersInitiated(initiated);
        summary.setTransfersSettled(settled);
        summary.setReconciliationOk(ok);

        when(dailySummaryService.generate(any())).thenReturn(summary);

        doAnswer(invocation -> {
            JobWork work = invocation.getArgument(1);
            JobResult result = work.execute(new ScheduledJobLog());
            assertEquals(initiated + settled, result.records());
            assertEquals(expectedNote, result.message());
            return result;
        }).when(jobRunner).run(eq("DAILY_SUMMARY"), any());

        job.run();

        verify(dailySummaryService).generate(any());
    }

    static Stream<Arguments> reconciliationScenarios() {
        return Stream.of(
                Arguments.of(true, 10, 8, null),
                Arguments.of(false, 5, 4, "Reconciliation mismatch detected"));
    }

    @Test
    void scheduledAnnotationHasExpectedCronAndZone() throws NoSuchMethodException {
        Method method = DailySummaryJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 0 23 * * *", scheduled.cron());
        assertEquals("Asia/Kolkata", scheduled.zone());
    }
}

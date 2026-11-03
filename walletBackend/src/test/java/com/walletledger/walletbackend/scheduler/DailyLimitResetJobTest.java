package com.walletledger.walletbackend.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

class DailyLimitResetJobTest {

    private JobRunner jobRunner;
    private WalletRepository walletRepository;
    private DailyLimitResetJob job;

    @BeforeEach
    void setUp() {
        jobRunner = mock(JobRunner.class);
        walletRepository = mock(WalletRepository.class);
        job = new DailyLimitResetJob(jobRunner, walletRepository);
    }

    @Test
    void runExecutesJobRunnerWithDailyLimitReset() {
        job.run();
        verify(jobRunner).run(eq("DAILY_LIMIT_RESET"), any());
    }

    @ParameterizedTest
    @MethodSource("resetCounts")
    void jobWorkInvokesWalletRepositoryResetDailySpent(int count) {
        when(walletRepository.resetDailySpent()).thenReturn(count);

        doAnswer(invocation -> {
            JobWork work = invocation.getArgument(1);
            JobResult result = work.execute(new ScheduledJobLog());
            assertEquals(count, result.records());
            return result;
        }).when(jobRunner).run(eq("DAILY_LIMIT_RESET"), any());

        job.run();

        verify(walletRepository).resetDailySpent();
    }

    static Stream<Arguments> resetCounts() {
        return Stream.of(
                Arguments.of(0),
                Arguments.of(15),
                Arguments.of(100));
    }

    @Test
    void scheduledAnnotationHasExpectedCronAndZone() throws NoSuchMethodException {
        Method method = DailyLimitResetJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 0 0 * * *", scheduled.cron());
        assertEquals("Asia/Kolkata", scheduled.zone());
    }
}

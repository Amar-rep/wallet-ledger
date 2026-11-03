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

import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.service.AccountUnlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

class AccountUnlockJobTest {

    private JobRunner jobRunner;
    private AccountUnlockService accountUnlockService;
    private AccountUnlockJob job;

    @BeforeEach
    void setUp() {
        jobRunner = mock(JobRunner.class);
        accountUnlockService = mock(AccountUnlockService.class);
        job = new AccountUnlockJob(jobRunner, accountUnlockService);
    }

    @Test
    void runExecutesJobRunnerWithExpectedJobName() {
        job.run();
        verify(jobRunner).run(eq("ACCOUNT_AUTO_UNLOCK"), any());
    }

    @ParameterizedTest
    @MethodSource("unlockedCounts")
    void jobWorkInvokesAccountUnlockServiceAndWrapsResult(int count) {
        when(accountUnlockService.unlockExpired()).thenReturn(count);

        ArgumentCaptor<JobWork> workCaptor = ArgumentCaptor.forClass(JobWork.class);
        doAnswer(invocation -> {
            JobWork work = invocation.getArgument(1);
            return work.execute(new ScheduledJobLog());
        }).when(jobRunner).run(eq("ACCOUNT_AUTO_UNLOCK"), workCaptor.capture());

        job.run();

        verify(accountUnlockService).unlockExpired();
    }

    static Stream<Arguments> unlockedCounts() {
        return Stream.of(
                Arguments.of(0),
                Arguments.of(5),
                Arguments.of(42));
    }

    @Test
    void scheduledAnnotationHasExpectedCronAndZone() throws NoSuchMethodException {
        Method method = AccountUnlockJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 */5 * * * *", scheduled.cron());
        assertEquals("Asia/Kolkata", scheduled.zone());
    }
}

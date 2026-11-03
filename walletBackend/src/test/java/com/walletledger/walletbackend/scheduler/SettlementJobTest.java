package com.walletledger.walletbackend.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.entity.ScheduledJobLog;
import com.walletledger.walletbackend.entity.SettlementBatch;
import com.walletledger.walletbackend.repository.SettlementBatchRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.service.SettlementOutcome;
import com.walletledger.walletbackend.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.scheduling.annotation.Scheduled;

class SettlementJobTest {

    private JobRunner jobRunner;
    private TransactionRepository transactionRepository;
    private SettlementBatchRepository settlementBatchRepository;
    private SettlementService settlementService;
    private SettlementJob settlementJob;

    @BeforeEach
    void setUp() {
        jobRunner = mock(JobRunner.class);
        transactionRepository = mock(TransactionRepository.class);
        settlementBatchRepository = mock(SettlementBatchRepository.class);
        settlementService = mock(SettlementService.class);

        settlementJob = new SettlementJob(
                jobRunner, transactionRepository, settlementBatchRepository, settlementService);

        when(settlementBatchRepository.save(any())).thenAnswer(inv -> {
            SettlementBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(10L);
            return b;
        });
    }

    @Test
    void runExecutesJobRunnerWithAchSettlement() {
        settlementJob.run();
        verify(jobRunner).run(eq("ACH_SETTLEMENT"), any());
    }

    @Test
    void runSkipsIfAlreadyRunning() throws Exception {
        Field runningField = SettlementJob.class.getDeclaredField("running");
        runningField.setAccessible(true);
        AtomicBoolean running = (AtomicBoolean) runningField.get(settlementJob);
        running.set(true);

        settlementJob.run();

        verify(jobRunner, never()).run(any(), any());
    }

    @ParameterizedTest
    @MethodSource("settlementOutcomes")
    void settleDueProcessesAllDueTransactions(SettlementOutcome outcome, int expectedSettled, int expectedDeclined) {
        when(transactionRepository.findDueIds(eq(TransactionStatus.PENDING), any()))
                .thenReturn(List.of(100L));
        when(settlementService.settle(eq(100L), any())).thenReturn(outcome);

        JobResult result = settlementJob.settleDue(new ScheduledJobLog());

        assertEquals(1, result.records());
        assertNull(result.message());
    }

    static Stream<Arguments> settlementOutcomes() {
        return Stream.of(
                Arguments.of(SettlementOutcome.SETTLED, 1, 0),
                Arguments.of(SettlementOutcome.DECLINED, 0, 1),
                Arguments.of(SettlementOutcome.SKIPPED, 0, 0));
    }

    @Test
    void settleDueHandlesExceptionByFailingTransaction() {
        when(transactionRepository.findDueIds(eq(TransactionStatus.PENDING), any()))
                .thenReturn(List.of(200L));
        when(settlementService.settle(eq(200L), any())).thenThrow(new RuntimeException("Simulated error"));

        JobResult result = settlementJob.settleDue(new ScheduledJobLog());

        assertEquals(1, result.records());
        assertNotNull(result.message());
        verify(settlementService).fail(eq(200L), any(), any());
    }

    @Test
    void scheduledAnnotationHasExpectedCronAndZone() throws NoSuchMethodException {
        Method method = SettlementJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("0 0 * * * *", scheduled.cron());
        assertEquals("Asia/Kolkata", scheduled.zone());
    }
}

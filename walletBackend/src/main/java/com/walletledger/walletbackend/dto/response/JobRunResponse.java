package com.walletledger.walletbackend.dto.response;

import java.time.Instant;

import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;

public record JobRunResponse(
        Long id,
        String jobName,
        Instant startedAt,
        Instant endedAt,
        ScheduledJobStatus status,
        int recordsProcessed,
        String errorMessage,
        SettlementBatchResponse settlementBatch
) {
}

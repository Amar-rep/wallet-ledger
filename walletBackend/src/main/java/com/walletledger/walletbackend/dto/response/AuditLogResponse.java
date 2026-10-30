package com.walletledger.walletbackend.dto.response;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        String adminName,
        String action,
        String targetType,
        Long targetId,
        Map<String, Object> details,
        Instant createdAt
) {
}

package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long totalUsers,
        long todayTransfersInitiated,
        long todayTransfersSettled,
        BigDecimal todayVolume,
        BigDecimal totalPendingAmount,
        long pendingFraudAlerts,
        List<DailyVolumePoint> last30DaysVolume
) {
}

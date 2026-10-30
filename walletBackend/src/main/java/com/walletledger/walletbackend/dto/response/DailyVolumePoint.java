package com.walletledger.walletbackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyVolumePoint(
        LocalDate date,
        BigDecimal volume
) {
}

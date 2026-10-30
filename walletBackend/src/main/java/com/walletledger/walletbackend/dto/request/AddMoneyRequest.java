package com.walletledger.walletbackend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AddMoneyRequest(
        @NotNull @DecimalMin(value = "1.00") @Digits(integer = 13, fraction = 2) BigDecimal amount
) {
}

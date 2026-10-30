package com.walletledger.walletbackend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendMoneyRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull Long receiverUserId,
        @NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 255) String remark,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits") String pin
) {
}

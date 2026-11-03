package com.walletledger.walletbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlexWriteOffRequest(
        @NotBlank @Size(max = 500) String reason
) {}

package com.walletledger.walletbackend.dto.request;

import com.walletledger.walletbackend.dto.enums.FraudAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FraudReviewRequest(
        @NotNull FraudAction action,
        @Size(max = 255) String note
) {
}

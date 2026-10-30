package com.walletledger.walletbackend.dto.request;

import com.walletledger.walletbackend.dto.enums.WalletStatus;
import jakarta.validation.constraints.NotNull;

public record WalletStatusRequest(@NotNull WalletStatus status) {
}

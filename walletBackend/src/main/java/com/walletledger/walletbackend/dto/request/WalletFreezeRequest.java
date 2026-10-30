package com.walletledger.walletbackend.dto.request;

import jakarta.validation.constraints.Size;

public record WalletFreezeRequest(@Size(max = 255) String reason) {
}

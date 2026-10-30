package com.walletledger.walletbackend.dto.enums;

public enum NotificationType {
    TRANSFER_INITIATED,
    TRANSFER_SETTLED,
    TRANSFER_RECEIVED,
    TRANSFER_FAILED,
    TRANSFER_DECLINED,
    TXN_BLOCKED,
    FRAUD_ALERT,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    RECON_MISMATCH
}

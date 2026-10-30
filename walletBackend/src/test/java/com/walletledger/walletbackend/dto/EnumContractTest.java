package com.walletledger.walletbackend.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.NotificationSeverity;
import com.walletledger.walletbackend.dto.enums.NotificationType;
import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionStatusFilter;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import org.junit.jupiter.api.Test;

class EnumContractTest {

    @Test
    void persistedEnumsMatchTheSchemaAndApiContract() {
        assertArrayEquals(new UserRole[]{UserRole.USER, UserRole.ADMIN}, UserRole.values());
        assertArrayEquals(new WalletStatus[]{WalletStatus.ACTIVE, WalletStatus.INACTIVE, WalletStatus.FROZEN}, WalletStatus.values());
        assertArrayEquals(new TransactionType[]{TransactionType.TRANSFER, TransactionType.ADD_MONEY}, TransactionType.values());
        assertArrayEquals(new TransactionStatus[]{TransactionStatus.PENDING, TransactionStatus.SUCCESS, TransactionStatus.FAILED, TransactionStatus.DECLINED}, TransactionStatus.values());
        assertArrayEquals(new LedgerEntryType[]{LedgerEntryType.DEBIT, LedgerEntryType.CREDIT, LedgerEntryType.REFUND}, LedgerEntryType.values());
        assertArrayEquals(new FraudAlertType[]{FraudAlertType.DAILY_LIMIT, FraudAlertType.DUPLICATE, FraudAlertType.HIGH_VALUE}, FraudAlertType.values());
        assertArrayEquals(new FraudAlertStatus[]{FraudAlertStatus.PENDING, FraudAlertStatus.BLOCKED, FraudAlertStatus.DISMISSED}, FraudAlertStatus.values());
        assertArrayEquals(new NotificationSeverity[]{NotificationSeverity.INFO, NotificationSeverity.CRITICAL}, NotificationSeverity.values());
        assertArrayEquals(new ScheduledJobStatus[]{ScheduledJobStatus.RUNNING, ScheduledJobStatus.SUCCESS, ScheduledJobStatus.FAILED}, ScheduledJobStatus.values());
        assertArrayEquals(new NotificationType[]{
                NotificationType.TRANSFER_INITIATED, NotificationType.TRANSFER_SETTLED, NotificationType.TRANSFER_RECEIVED,
                NotificationType.TRANSFER_FAILED, NotificationType.TRANSFER_DECLINED, NotificationType.TXN_BLOCKED,
                NotificationType.FRAUD_ALERT, NotificationType.ACCOUNT_LOCKED, NotificationType.ACCOUNT_UNLOCKED,
                NotificationType.RECON_MISMATCH
        }, NotificationType.values());
    }

    @Test
    void apiOnlyEnumsMatchTheDtoContract() {
        assertArrayEquals(new TransactionStatusFilter[]{
                TransactionStatusFilter.ALL, TransactionStatusFilter.PENDING, TransactionStatusFilter.SUCCESS,
                TransactionStatusFilter.FAILED, TransactionStatusFilter.DECLINED
        }, TransactionStatusFilter.values());
        assertArrayEquals(new TransactionDirection[]{
                TransactionDirection.ALL, TransactionDirection.SENT, TransactionDirection.RECEIVED
        }, TransactionDirection.values());
        assertArrayEquals(new FraudAction[]{FraudAction.BLOCK, FraudAction.DISMISS}, FraudAction.values());
    }
}

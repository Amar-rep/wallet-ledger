package com.walletledger.walletbackend.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.walletledger.walletbackend.dto.common.ErrorResponse;
import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.*;
import com.walletledger.walletbackend.dto.request.*;
import com.walletledger.walletbackend.dto.response.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DtoContractAndRecordTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void testRequestValidationConstraints() {
        // RefreshTokenRequest
        RefreshTokenRequest validRefresh = new RefreshTokenRequest("token123");
        assertTrue(validator.validate(validRefresh).isEmpty());

        RefreshTokenRequest invalidRefresh = new RefreshTokenRequest("");
        assertFalse(validator.validate(invalidRefresh).isEmpty());

        // WalletStatusRequest
        WalletStatusRequest validStatus = new WalletStatusRequest(WalletStatus.ACTIVE);
        assertTrue(validator.validate(validStatus).isEmpty());

        WalletStatusRequest invalidStatus = new WalletStatusRequest(null);
        assertFalse(validator.validate(invalidStatus).isEmpty());

        // FlexWriteOffRequest
        FlexWriteOffRequest validWriteOff = new FlexWriteOffRequest("Unrecoverable");
        assertTrue(validator.validate(validWriteOff).isEmpty());

        FlexWriteOffRequest invalidWriteOff = new FlexWriteOffRequest("");
        assertFalse(validator.validate(invalidWriteOff).isEmpty());

        // WalletFreezeRequest
        WalletFreezeRequest freezeReq = new WalletFreezeRequest("Suspicious activity");
        assertEquals("Suspicious activity", freezeReq.reason());
        assertTrue(validator.validate(freezeReq).isEmpty());
    }

    @Test
    void testResponseRecordInstantiationAndAccessors() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        // ErrorResponse
        ErrorResponse errorResponse = new ErrorResponse(now, 400, "Bad Request", "Error", "/path", null);
        assertEquals(400, errorResponse.status());
        assertEquals("Bad Request", errorResponse.error());
        assertEquals("/path", errorResponse.path());

        // MessageResponse
        MessageResponse messageResponse = new MessageResponse("Operation succeeded");
        assertEquals("Operation succeeded", messageResponse.message());

        // PageResponse
        PageResponse<String> pageResponse = new PageResponse<>(List.of("item"), 0, 10, 1L, 1);
        assertEquals(1, pageResponse.content().size());
        assertEquals(0, pageResponse.page());
        assertEquals(10, pageResponse.size());
        assertEquals(1L, pageResponse.totalElements());
        assertEquals(1, pageResponse.totalPages());

        // TokenRefreshResponse
        TokenRefreshResponse trr = new TokenRefreshResponse("acc", "ref", "Bearer", 900);
        assertEquals("acc", trr.accessToken());
        assertEquals("ref", trr.refreshToken());

        // UserProfileResponse
        UserProfileResponse upr = new UserProfileResponse(1L, "Ada", "ada@example.com", "9876543210", UserRole.USER);
        assertEquals("Ada", upr.name());
        assertEquals(UserRole.USER, upr.role());

        // WalletResponse
        WalletResponse wr = new WalletResponse(1L, BigDecimal.TEN, BigDecimal.ONE, WalletStatus.ACTIVE, true, BigDecimal.ZERO);
        assertEquals(BigDecimal.TEN, wr.availableBalance());
        assertTrue(wr.flexEnabled());

        // TransferResponse
        TransferResponse tr = new TransferResponse(1L, "req1", TransactionStatus.SUCCESS, BigDecimal.TEN, "Bob", today, "msg");
        assertEquals("req1", tr.requestId());
        assertEquals(TransactionStatus.SUCCESS, tr.status());

        // UnreadCountResponse
        UnreadCountResponse ucr = new UnreadCountResponse(5L);
        assertEquals(5L, ucr.unreadCount());

        // UserSearchResponse
        UserSearchResponse usr = new UserSearchResponse(1L, "Bob", "1234567890", "bob@example.com");
        assertEquals("Bob", usr.name());

        // UserStatsResponse
        UserStatsResponse stats = new UserStatsResponse(BigDecimal.TEN, BigDecimal.ONE, 3L);
        assertEquals(3L, stats.thisMonthTransactionCount());

        // ReconciliationResponse
        ReconciliationResponse recon = new ReconciliationResponse(today, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, false, BigDecimal.ZERO);
        assertFalse(recon.mismatch());

        // AuditLogResponse
        AuditLogResponse alr = new AuditLogResponse(1L, "admin@example.com", "FREEZE", "WALLET", 2L, null, now);
        assertEquals("FREEZE", alr.action());

        // DailyVolumePoint
        DailyVolumePoint dvp = new DailyVolumePoint(today, BigDecimal.TEN);
        assertEquals(BigDecimal.TEN, dvp.volume());

        // SettlementBatchResponse
        SettlementBatchResponse sbr = new SettlementBatchResponse(1L, 5, 1, 0, BigDecimal.TEN);
        assertEquals(5, sbr.settledCount());

        // JobRunResponse
        JobRunResponse jrr = new JobRunResponse(1L, "JOB", now, now, ScheduledJobStatus.SUCCESS, 10, null, null);
        assertEquals(ScheduledJobStatus.SUCCESS, jrr.status());

        // DailySummaryResponse
        DailySummaryResponse dsr = new DailySummaryResponse(today, 10, BigDecimal.TEN, 8, BigDecimal.ONE, 0, 2, BigDecimal.ZERO, true);
        assertEquals(10, dsr.transfersInitiated());
        assertTrue(dsr.reconciliationOk());

        // AdminUserResponse & AdminUserDetailResponse
        AdminUserResponse aur = new AdminUserResponse(1L, "Ada", "ada@example.com", "9876543210", UserRole.USER, BigDecimal.TEN, WalletStatus.ACTIVE, now);
        assertEquals(UserRole.USER, aur.role());
        AdminUserDetailResponse audr = new AdminUserDetailResponse(aur, List.of());
        assertEquals(aur, audr.user());

        // AdminTransactionResponse & AdminTransactionDetailResponse
        AdminTransactionResponse atr = new AdminTransactionResponse(1L, "Ada", "Bob", BigDecimal.TEN, TransactionStatus.SUCCESS, false, now, today);
        assertEquals(1L, atr.id());
        AdminTransactionDetailResponse atdr = new AdminTransactionDetailResponse(
                1L, "req1", TransactionType.TRANSFER, 1L, "Ada", 2L, "Bob",
                BigDecimal.TEN, "remark", TransactionStatus.SUCCESS, null, false,
                now, today, now, 0L, List.of());
        assertEquals("req1", atdr.requestId());
        assertFalse(atdr.flagged());

        // FraudAlertResponse
        FraudAlertResponse far = new FraudAlertResponse(1L, 2L, "Ada", 3L, FraudAlertType.HIGH_VALUE, BigDecimal.TEN, "desc", FraudAlertStatus.PENDING, false, null, null, now);
        assertEquals(FraudAlertType.HIGH_VALUE, far.alertType());

        // FlexCreditResponse
        FlexCreditResponse fcr = new FlexCreditResponse(1L, 2L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, FlexCreditStatus.OPEN, "Ada", now, null, null, null, null, null);
        assertEquals(FlexCreditStatus.OPEN, fcr.status());

        // SettlementMonitorResponse
        SettlementMonitorResponse smr = new SettlementMonitorResponse(1L, 2L, 0L, BigDecimal.TEN, List.of(atr));
        assertEquals(1L, smr.dueToday());

        // DashboardResponse
        DashboardResponse dr = new DashboardResponse(10L, 5L, 3L, BigDecimal.TEN, BigDecimal.ZERO, 0L, List.of(dvp));
        assertEquals(10L, dr.totalUsers());

        // LoginResponse
        LoginResponse lr = new LoginResponse("acc", "ref", "Bearer", 900, upr);
        assertEquals("acc", lr.token());
        assertEquals("ref", lr.refreshToken());

        LedgerEntryResponse ler = new LedgerEntryResponse(1L, 10L, LedgerEntryType.DEBIT, BigDecimal.TEN, BigDecimal.ONE, now);
        assertEquals(LedgerEntryType.DEBIT, ler.entryType());

        TransactionSummaryResponse tsr = new TransactionSummaryResponse(
                1L, TransactionType.TRANSFER, TransactionDirection.SENT, "Bob",
                BigDecimal.TEN, "remark", TransactionStatus.PENDING, now, today, 2L);
        assertEquals(TransactionDirection.SENT, tsr.direction());

        TransactionDetailResponse tdr = new TransactionDetailResponse(
                1L, "req1", TransactionType.TRANSFER, TransactionDirection.SENT, "Ada", "Bob",
                BigDecimal.TEN, "remark", TransactionStatus.SUCCESS, null, now, today, null, now, List.of(ler));
        assertEquals(1, tdr.ledgerEntries().size());

        NotificationResponse nr = new NotificationResponse(
                1L, NotificationType.TRANSFER_SETTLED, NotificationSeverity.INFO, "Settled", 1L, false, now);
        assertEquals(NotificationType.TRANSFER_SETTLED, nr.type());

        AdminTransactionFilter atf = new AdminTransactionFilter(today, today, TransactionStatusFilter.ALL, BigDecimal.ONE, BigDecimal.TEN, 0, 20);
        assertEquals(TransactionStatusFilter.ALL, atf.status());

        TransactionFilter tf = new TransactionFilter(today, today, TransactionDirection.ALL, TransactionStatusFilter.PENDING, 0, 20);
        assertEquals(TransactionDirection.ALL, tf.type());
    }

    @ParameterizedTest
    @EnumSource(WalletKind.class)
    void walletKindEnumValuesExist(WalletKind kind) {
        assertNotNull(kind);
    }
}

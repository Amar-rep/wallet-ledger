package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AdminTransactionFilter;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.request.WalletFreezeRequest;
import com.walletledger.walletbackend.dto.response.AdminTransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminTransactionResponse;
import com.walletledger.walletbackend.dto.response.AdminUserDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminUserResponse;
import com.walletledger.walletbackend.dto.response.DashboardResponse;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.dto.response.JobRunResponse;
import com.walletledger.walletbackend.dto.response.ReconciliationResponse;
import com.walletledger.walletbackend.dto.response.SettlementMonitorResponse;
import com.walletledger.walletbackend.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AdminControllerTest {

    private AdminService adminService;
    private AdminController adminController;
    private Principal adminPrincipal;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        adminController = new AdminController(adminService);
        adminPrincipal = () -> "admin@example.com";
    }

    @Test
    void dashboardDelegatesToService() {
        DashboardResponse expected = dashboardResponse();
        when(adminService.dashboard()).thenReturn(expected);

        DashboardResponse actual = adminController.dashboard();

        assertNotNull(actual);
        verify(adminService).dashboard();
    }

    @Test
    void usersDelegatesToService() {
        PageResponse<AdminUserResponse> expected = new PageResponse<>(List.of(), 0, 20, 0L, 0);
        when(adminService.users(null, 0, 20)).thenReturn(expected);

        PageResponse<AdminUserResponse> actual = adminController.users(null, 0, 20);

        assertNotNull(actual);
        verify(adminService).users(null, 0, 20);
    }

    @Test
    void userDetailDelegatesToService() {
        AdminUserDetailResponse expected = userDetailResponse();
        when(adminService.userDetail(1L)).thenReturn(expected);

        AdminUserDetailResponse actual = adminController.userDetail(1L);

        assertNotNull(actual);
        verify(adminService).userDetail(1L);
    }

    @Test
    void freezeWalletDelegatesToService() {
        when(adminService.freezeWallet(any(), eq(1L), any()))
                .thenReturn(new MessageResponse("Wallet frozen"));

        MessageResponse actual = adminController.freezeWallet(adminPrincipal, 1L, null);

        assertEquals("Wallet frozen", actual.message());
        verify(adminService).freezeWallet(any(), eq(1L), any());
    }

    @Test
    void unfreezeWalletDelegatesToService() {
        when(adminService.unfreezeWallet(any(), eq(1L), any()))
                .thenReturn(new MessageResponse("Wallet unfrozen"));

        MessageResponse actual = adminController.unfreezeWallet(adminPrincipal, 1L, null);

        assertEquals("Wallet unfrozen", actual.message());
    }

    @Test
    void transactionsDelegatesToService() {
        AdminTransactionFilter filter = new AdminTransactionFilter(null, null, null, null, null, 0, 20);
        PageResponse<AdminTransactionResponse> expected = new PageResponse<>(List.of(), 0, 20, 0L, 0);
        when(adminService.transactions(any())).thenReturn(expected);

        PageResponse<AdminTransactionResponse> actual = adminController.transactions(filter);

        assertNotNull(actual);
    }

    @Test
    void transactionDetailDelegatesToService() {
        AdminTransactionDetailResponse expected = mock(AdminTransactionDetailResponse.class);
        when(adminService.transactionDetail(5L)).thenReturn(expected);

        AdminTransactionDetailResponse actual = adminController.transactionDetail(5L);

        assertEquals(expected, actual);
        verify(adminService).transactionDetail(5L);
    }

    @Test
    void settlementsDelegatesToService() {
        SettlementMonitorResponse expected = mock(SettlementMonitorResponse.class);
        when(adminService.settlements()).thenReturn(expected);

        SettlementMonitorResponse actual = adminController.settlements();

        assertEquals(expected, actual);
    }

    @Test
    void manualSettleDelegatesToService() {
        when(adminService.manualSettle(any(), eq(7L)))
                .thenReturn(new MessageResponse("Settled"));

        MessageResponse actual = adminController.manualSettle(adminPrincipal, 7L);

        assertEquals("Settled", actual.message());
        verify(adminService).manualSettle(any(), eq(7L));
    }

    @Test
    void fraudAlertsDelegatesToService() {
        FraudAlertResponse alert = fraudAlert();
        when(adminService.fraudAlerts(FraudAlertStatus.PENDING)).thenReturn(List.of(alert));

        List<FraudAlertResponse> actual = adminController.fraudAlerts(FraudAlertStatus.PENDING);

        assertEquals(1, actual.size());
        verify(adminService).fraudAlerts(FraudAlertStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(FraudAlertStatus.class)
    void fraudAlertsHandlesAllStatuses(FraudAlertStatus status) {
        when(adminService.fraudAlerts(status)).thenReturn(List.of());

        List<FraudAlertResponse> actual = adminController.fraudAlerts(status);

        assertNotNull(actual);
        verify(adminService).fraudAlerts(status);
    }

    @Test
    void reviewFraudAlertDelegatesToService() {
        FraudReviewRequest request = new FraudReviewRequest(FraudAction.DISMISS, "dismissed");
        FraudAlertResponse expected = fraudAlert();
        when(adminService.reviewFraudAlert(any(), eq(1L), eq(request))).thenReturn(expected);

        FraudAlertResponse actual = adminController.reviewFraudAlert(adminPrincipal, 1L, request);

        assertNotNull(actual);
        verify(adminService).reviewFraudAlert(any(), eq(1L), eq(request));
    }

    @Test
    void jobsDelegatesToService() {
        when(adminService.jobs(null)).thenReturn(List.of());

        List<JobRunResponse> actual = adminController.jobs(null);

        assertNotNull(actual);
        verify(adminService).jobs(null);
    }

    @Test
    void reconciliationDelegatesToService() {
        ReconciliationResponse expected = mock(ReconciliationResponse.class);
        when(adminService.reconciliation(null)).thenReturn(expected);

        ReconciliationResponse actual = adminController.reconciliation(null);

        assertEquals(expected, actual);
        verify(adminService).reconciliation(null);
    }

    // ─── Parameterized: user search with various search strings ──────────────

    @ParameterizedTest
    @MethodSource("searchTerms")
    void usersSearchPassesTermToService(String searchTerm) {
        PageResponse<AdminUserResponse> expected = new PageResponse<>(List.of(), 0, 20, 0L, 0);
        when(adminService.users(searchTerm, 0, 20)).thenReturn(expected);

        adminController.users(searchTerm, 0, 20);

        verify(adminService).users(searchTerm, 0, 20);
    }

    static Stream<Arguments> searchTerms() {
        return Stream.of(
                Arguments.of("alice"),
                Arguments.of("bob@example.com"),
                Arguments.of((String) null));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static DashboardResponse dashboardResponse() {
        return new DashboardResponse(10L, 5L, 3L, new BigDecimal("50000.00"),
                new BigDecimal("25000.00"), 2L, List.of());
    }

    private static AdminUserDetailResponse userDetailResponse() {
        AdminUserResponse adminUser = new AdminUserResponse(1L, "Alice", "alice@example.com", "9876543210",
                UserRole.USER, new BigDecimal("1000.00"), WalletStatus.ACTIVE, java.time.Instant.now());
        return new AdminUserDetailResponse(adminUser, List.of());
    }

    private static FraudAlertResponse fraudAlert() {
        return new FraudAlertResponse(1L, 2L, "Sender", null,
                FraudAlertType.HIGH_VALUE, new BigDecimal("9000.00"),
                "High-value txn", FraudAlertStatus.PENDING, false,
                null, null, null);
    }
}

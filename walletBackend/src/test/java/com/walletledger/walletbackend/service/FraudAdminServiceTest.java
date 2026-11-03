package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.FraudAction;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.enums.FraudAlertType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.entity.FraudAlert;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FraudAlertRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class FraudAdminServiceTest {

    private FraudAlertRepository fraudAlertRepository;
    private UserRepository userRepository;
    private SettlementService settlementService;
    private FraudAdminService fraudAdminService;

    private User admin;

    @BeforeEach
    void setUp() {
        fraudAlertRepository = mock(FraudAlertRepository.class);
        userRepository = mock(UserRepository.class);
        settlementService = mock(SettlementService.class);
        fraudAdminService = new FraudAdminService(fraudAlertRepository, userRepository, settlementService);

        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@example.com");
        admin.setName("Admin User");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(fraudAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listReturnsAlertsMappedToResponse() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        when(fraudAlertRepository.findByStatusOrderByCreatedAtDesc(FraudAlertStatus.PENDING))
                .thenReturn(List.of(alert));

        List<FraudAlertResponse> result = fraudAdminService.list(FraudAlertStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(FraudAlertType.HIGH_VALUE, result.get(0).alertType());
    }

    @ParameterizedTest
    @EnumSource(FraudAlertStatus.class)
    void listDelegatesToRepositoryForAllStatuses(FraudAlertStatus status) {
        when(fraudAlertRepository.findByStatusOrderByCreatedAtDesc(status)).thenReturn(List.of());

        List<FraudAlertResponse> result = fraudAdminService.list(status);

        assertEquals(0, result.size());
        verify(fraudAlertRepository).findByStatusOrderByCreatedAtDesc(status);
    }

    @Test
    void reviewDismissesAlertWhenActionIsDismiss() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        FraudAlertResponse result = fraudAdminService.review("admin@example.com", 1L,
                new FraudReviewRequest(FraudAction.DISMISS, "dismiss note"));

        assertEquals(FraudAlertStatus.DISMISSED, result.status());
    }

    @Test
    void reviewBlocksHighValueAlertWithPendingTransaction() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        Transaction txn = pendingTransaction(10L);
        alert.setTransaction(txn);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        fraudAdminService.review("admin@example.com", 1L,
                new FraudReviewRequest(FraudAction.BLOCK, "block note"));

        verify(settlementService).blockByAdmin(10L);
        assertEquals(FraudAlertStatus.BLOCKED, alert.getStatus());
    }

    @Test
    void reviewThrowsConflictIfAlertAlreadyReviewed() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        alert.setStatus(FraudAlertStatus.DISMISSED);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("admin@example.com", 1L,
                        new FraudReviewRequest(FraudAction.DISMISS, "dismiss note")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void reviewThrowsConflictIfTransactionAlreadySettled() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        Transaction txn = new Transaction();
        txn.setId(10L);
        txn.setStatus(TransactionStatus.SUCCESS); // already settled, not PENDING
        alert.setTransaction(txn);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("admin@example.com", 1L,
                        new FraudReviewRequest(FraudAction.DISMISS, "dismiss note")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void reviewThrowsNotFoundForMissingAlert() {
        when(fraudAlertRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("admin@example.com", 999L,
                        new FraudReviewRequest(FraudAction.DISMISS, "dismiss note")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void reviewThrowsBadRequestWhenBlockingNonHighValueAlert() {
        FraudAlert alert = pendingAlert(FraudAlertType.DUPLICATE);
        Transaction txn = pendingTransaction(10L);
        alert.setTransaction(txn);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("admin@example.com", 1L,
                        new FraudReviewRequest(FraudAction.BLOCK, "block note")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(settlementService, never()).blockByAdmin(any());
    }

    // ─── Parameterized: review works for all alert types when dismissing ─────

    @ParameterizedTest
    @MethodSource("alertTypesForDismissal")
    void reviewCanDismissAnyAlertType(FraudAlertType alertType) {
        FraudAlert alert = pendingAlert(alertType);
        Transaction txn = pendingTransaction(10L);
        alert.setTransaction(txn);
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        FraudAlertResponse result = fraudAdminService.review("admin@example.com", 1L,
                new FraudReviewRequest(FraudAction.DISMISS, "dismiss note"));

        assertEquals(FraudAlertStatus.DISMISSED, result.status());
    }

    static Stream<Arguments> alertTypesForDismissal() {
        return Stream.of(
                Arguments.of(FraudAlertType.HIGH_VALUE),
                Arguments.of(FraudAlertType.DAILY_LIMIT),
                Arguments.of(FraudAlertType.DUPLICATE));
    }

    @Test
    void reviewThrowsUnauthorizedWhenAdminNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("unknown@example.com", 1L,
                        new FraudReviewRequest(FraudAction.DISMISS, "note")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void reviewThrowsBadRequestWhenBlockingHighValueAlertWithNullTransaction() {
        FraudAlert alert = pendingAlert(FraudAlertType.HIGH_VALUE);
        alert.setTransaction(null); // HIGH_VALUE but no transaction linked
        when(fraudAlertRepository.findById(1L)).thenReturn(Optional.of(alert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> fraudAdminService.review("admin@example.com", 1L,
                        new FraudReviewRequest(FraudAction.BLOCK, "block note")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(settlementService, never()).blockByAdmin(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private FraudAlert pendingAlert(FraudAlertType type) {
        User sender = new User();
        sender.setId(2L);
        sender.setName("Sender");

        FraudAlert alert = new FraudAlert();
        alert.setId(1L);
        alert.setUser(sender);
        alert.setAlertType(type);
        alert.setAmount(new BigDecimal("1000.00"));
        alert.setDescription("Test alert");
        alert.setStatus(FraudAlertStatus.PENDING);
        alert.setCreatedAt(OffsetDateTime.now());
        return alert;
    }

    private Transaction pendingTransaction(Long id) {
        Transaction txn = new Transaction();
        txn.setId(id);
        txn.setStatus(TransactionStatus.PENDING);
        return txn;
    }
}

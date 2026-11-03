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
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.TransactionDirection;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.dto.request.TransactionFilter;
import com.walletledger.walletbackend.dto.response.TransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.TransactionSummaryResponse;
import com.walletledger.walletbackend.dto.response.UserStatsResponse;
import com.walletledger.walletbackend.service.TransactionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransactionControllerTest {

    private TransactionHistoryService transactionHistoryService;
    private TransactionController transactionController;
    private Principal principal;

    @BeforeEach
    void setUp() {
        transactionHistoryService = mock(TransactionHistoryService.class);
        transactionController = new TransactionController(transactionHistoryService);
        principal = () -> "ada@example.com";
    }

    @Test
    void miniStatementDelegatesToService() {
        List<TransactionSummaryResponse> expected = List.of(txSummary());
        when(transactionHistoryService.miniStatement("ada@example.com")).thenReturn(expected);

        List<TransactionSummaryResponse> actual = transactionController.miniStatement(principal);

        assertEquals(1, actual.size());
        verify(transactionHistoryService).miniStatement("ada@example.com");
    }

    @Test
    void historyDelegatesToService() {
        PageResponse<TransactionSummaryResponse> expected = new PageResponse<>(List.of(), 0, 10, 0L, 0);
        TransactionFilter filter = new TransactionFilter(null, null, TransactionDirection.ALL, null, 0, 10);
        when(transactionHistoryService.history(eq("ada@example.com"), any())).thenReturn(expected);

        PageResponse<TransactionSummaryResponse> actual = transactionController.history(principal, filter);

        assertNotNull(actual);
        verify(transactionHistoryService).history(eq("ada@example.com"), any());
    }

    @Test
    void statsDelegatesToService() {
        UserStatsResponse expected = new UserStatsResponse(new BigDecimal("5000.00"),
                new BigDecimal("3000.00"), 10L);
        when(transactionHistoryService.stats("ada@example.com")).thenReturn(expected);

        UserStatsResponse actual = transactionController.stats(principal);

        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    void detailDelegatesToService() {
        TransactionDetailResponse expected = txDetail();
        when(transactionHistoryService.detail("ada@example.com", 42L)).thenReturn(expected);

        TransactionDetailResponse actual = transactionController.detail(principal, 42L);

        assertNotNull(actual);
        assertEquals(42L, actual.id());
        verify(transactionHistoryService).detail("ada@example.com", 42L);
    }

    // ─── Parameterized: multiple transaction ids ──────────────────────────────

    @ParameterizedTest
    @MethodSource("transactionIds")
    void detailForVariousIds(Long id) {
        when(transactionHistoryService.detail(eq("ada@example.com"), eq(id)))
                .thenReturn(txDetailWithId(id));

        TransactionDetailResponse actual = transactionController.detail(principal, id);

        assertEquals(id, actual.id());
    }

    static Stream<Arguments> transactionIds() {
        return Stream.of(
                Arguments.of(1L),
                Arguments.of(100L),
                Arguments.of(9999L));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static TransactionSummaryResponse txSummary() {
        return new TransactionSummaryResponse(1L, TransactionType.TRANSFER,
                TransactionDirection.SENT, "Bob", new BigDecimal("100.00"),
                "remark", TransactionStatus.PENDING, Instant.now(), null, null);
    }

    private static TransactionDetailResponse txDetail() {
        return txDetailWithId(42L);
    }

    private static TransactionDetailResponse txDetailWithId(Long id) {
        return new TransactionDetailResponse(id, "req-001", TransactionType.TRANSFER,
                TransactionDirection.SENT, "Ada", "Bob", new BigDecimal("100.00"),
                "remark", TransactionStatus.SUCCESS, null, Instant.now(), null, null, null, List.of());
    }
}

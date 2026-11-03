package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.request.SendMoneyRequest;
import com.walletledger.walletbackend.dto.response.TransferResponse;
import com.walletledger.walletbackend.dto.response.UserSearchResponse;
import com.walletledger.walletbackend.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransferControllerTest {

    private TransferService transferService;
    private TransferController transferController;
    private Principal principal;

    @BeforeEach
    void setUp() {
        transferService = mock(TransferService.class);
        transferController = new TransferController(transferService);
        principal = () -> "amarnath@example.com";
    }

    @Test
    void searchDelegatesToService() {
        UserSearchResponse expected = new UserSearchResponse(2L, "Bob", "9999999999", "bob@example.com");
        when(transferService.search("amarnath@example.com", "bob@example.com")).thenReturn(expected);

        UserSearchResponse actual = transferController.search(principal, "bob@example.com");

        assertEquals(expected, actual);
        verify(transferService).search("amarnath@example.com", "bob@example.com");
    }

    @Test
    void sendMoneyDelegatesToService() {
        SendMoneyRequest request = new SendMoneyRequest("req-001", 2L, new BigDecimal("100.00"), "test", "1234");
        TransferResponse expected = transferResponse();
        when(transferService.sendMoney("amarnath@example.com", request)).thenReturn(expected);

        TransferResponse actual = transferController.sendMoney(principal, request);

        assertNotNull(actual);
        assertEquals(expected.requestId(), actual.requestId());
        verify(transferService).sendMoney("amarnath@example.com", request);
    }

    // ─── Parameterized: search by different identifiers ──────────────────────

    @ParameterizedTest
    @MethodSource("searchQueries")
    void searchPassesQueryToService(String query) {
        UserSearchResponse expected = new UserSearchResponse(2L, "Bob", "9999999999", "bob@example.com");
        when(transferService.search("amarnath@example.com", query)).thenReturn(expected);

        UserSearchResponse actual = transferController.search(principal, query);

        assertNotNull(actual);
        verify(transferService).search("amarnath@example.com", query);
    }

    static Stream<Arguments> searchQueries() {
        return Stream.of(
                Arguments.of("bob@example.com"),
                Arguments.of("9876543210"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static TransferResponse transferResponse() {
        return new TransferResponse(
                1L, "req-001", TransactionStatus.PENDING,
                new BigDecimal("100.00"), "Bob",
                LocalDate.now().plusDays(3),
                "Transfer initiated");
    }
}

package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AddMoneyRequest;
import com.walletledger.walletbackend.dto.request.WalletStatusRequest;
import com.walletledger.walletbackend.dto.response.WalletResponse;
import com.walletledger.walletbackend.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class WalletControllerTest {

    private WalletService walletService;
    private WalletController walletController;
    private Principal principal;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        walletController = new WalletController(walletService);
        principal = () -> "ada@example.com";
    }

    @Test
    void getWalletDelegatesToService() {
        WalletResponse expected = walletResponse(WalletStatus.ACTIVE);
        when(walletService.getWallet("ada@example.com")).thenReturn(expected);

        WalletResponse actual = walletController.getWallet(principal);

        assertEquals(expected, actual);
        verify(walletService).getWallet("ada@example.com");
    }

    @Test
    void addMoneyDelegatesToService() {
        AddMoneyRequest request = new AddMoneyRequest(new BigDecimal("500.00"));
        WalletResponse expected = walletResponse(WalletStatus.ACTIVE);
        when(walletService.addMoney("ada@example.com", request)).thenReturn(expected);

        WalletResponse actual = walletController.addMoney(principal, request);

        assertEquals(expected, actual);
        verify(walletService).addMoney("ada@example.com", request);
    }

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = {"ACTIVE", "INACTIVE"})
    void updateStatusDelegatesToService(WalletStatus status) {
        WalletStatusRequest request = new WalletStatusRequest(status);
        WalletResponse expected = walletResponse(status);
        when(walletService.setOwnStatus("ada@example.com", status)).thenReturn(expected);

        WalletResponse actual = walletController.updateStatus(principal, request);

        assertEquals(status, actual.status());
        verify(walletService).setOwnStatus("ada@example.com", status);
    }

    @Test
    void toggleFlexReturnsMessage() {
        MessageResponse msg = new MessageResponse("Flex Instant Credit enabled.");
        when(walletService.toggleFlex("ada@example.com")).thenReturn(msg);

        MessageResponse actual = walletController.toggleFlex(principal);

        assertNotNull(actual);
        assertEquals("Flex Instant Credit enabled.", actual.message());
        verify(walletService).toggleFlex("ada@example.com");
    }

    // ─── Parameterized: all wallet status responses contain walletId ──────────

    @ParameterizedTest
    @MethodSource("walletResponses")
    void getWalletAlwaysReturnsWalletId(WalletResponse response) {
        when(walletService.getWallet("ada@example.com")).thenReturn(response);

        WalletResponse actual = walletController.getWallet(principal);

        assertNotNull(actual.walletId());
    }

    static Stream<Arguments> walletResponses() {
        return Stream.of(
                Arguments.of(walletResponse(WalletStatus.ACTIVE)),
                Arguments.of(walletResponse(WalletStatus.INACTIVE)),
                Arguments.of(walletResponse(WalletStatus.FROZEN)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static WalletResponse walletResponse(WalletStatus status) {
        return new WalletResponse(10L, new BigDecimal("1000.00"), BigDecimal.ZERO, status, false, BigDecimal.ZERO);
    }
}

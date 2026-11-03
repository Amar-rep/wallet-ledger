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

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.dto.request.FlexWriteOffRequest;
import com.walletledger.walletbackend.dto.response.FlexCreditResponse;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.service.FlexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class FlexAdminControllerTest {

    private FlexService flexService;
    private FlexAdminController flexAdminController;
    private Principal principal;

    @BeforeEach
    void setUp() {
        flexService = mock(FlexService.class);
        flexAdminController = new FlexAdminController(flexService);
        principal = () -> "admin@example.com";
    }

    @Test
    void listWithNullStatusReturnsAll() {
        FlexCredit fc = flexCredit(FlexCreditStatus.RECOVERING);
        when(flexService.list(null)).thenReturn(List.of(fc));

        List<FlexCreditResponse> result = flexAdminController.list(null);

        assertEquals(1, result.size());
        verify(flexService).list(null);
    }

    @Test
    void listHandlesNullUserInBeneficiaryWallet() {
        FlexCredit fc = flexCredit(FlexCreditStatus.OPEN);
        fc.getBeneficiaryWallet().setUser(null);
        when(flexService.list(null)).thenReturn(List.of(fc));

        List<FlexCreditResponse> result = flexAdminController.list(null);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).beneficiaryName());
    }

    @ParameterizedTest
    @EnumSource(FlexCreditStatus.class)
    void listFiltersByStatus(FlexCreditStatus status) {
        when(flexService.list(status)).thenReturn(List.of());

        List<FlexCreditResponse> result = flexAdminController.list(status);

        assertNotNull(result);
        verify(flexService).list(status);
    }

    @Test
    void writeOffDelegatesToServiceAndReturnsMessage() {
        FlexWriteOffRequest request = new FlexWriteOffRequest("Unrecoverable debt");

        MessageResponse response = flexAdminController.writeOff(principal, 5L, request);

        assertNotNull(response);
        assertEquals("Flex credit #5 written off.", response.message());
        verify(flexService).writeOff(5L, "admin@example.com", "Unrecoverable debt");
    }

    @ParameterizedTest
    @MethodSource("writeOffIds")
    void writeOffWorksForVariousIds(Long id) {
        FlexWriteOffRequest request = new FlexWriteOffRequest("Test reason");

        MessageResponse response = flexAdminController.writeOff(principal, id, request);

        assertNotNull(response);
        verify(flexService).writeOff(eq(id), eq("admin@example.com"), eq("Test reason"));
    }

    static Stream<Arguments> writeOffIds() {
        return Stream.of(
                Arguments.of(1L),
                Arguments.of(42L),
                Arguments.of(100L));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private FlexCredit flexCredit(FlexCreditStatus status) {
        User user = new User();
        user.setId(5L);
        user.setName("Ada");

        Wallet beneficiary = new Wallet();
        beneficiary.setId(10L);
        beneficiary.setUser(user);

        Transaction txn = new Transaction();
        txn.setId(100L);

        FlexCredit fc = new FlexCredit();
        fc.setId(1L);
        fc.setTransaction(txn);
        fc.setAmount(new BigDecimal("200.00"));
        fc.setAmountRecovered(BigDecimal.ZERO);
        fc.setAmountOutstanding(new BigDecimal("200.00"));
        fc.setStatus(status);
        fc.setBeneficiaryWallet(beneficiary);
        return fc;
    }
}

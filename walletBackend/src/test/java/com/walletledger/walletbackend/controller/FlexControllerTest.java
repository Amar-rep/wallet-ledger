package com.walletledger.walletbackend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.response.FlexCreditResponse;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FlexCreditRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FlexControllerTest {

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private FlexCreditRepository flexCreditRepository;
    private FlexController flexController;
    private Principal principal;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        walletRepository = mock(WalletRepository.class);
        flexCreditRepository = mock(FlexCreditRepository.class);
        flexController = new FlexController(userRepository, walletRepository, flexCreditRepository);
        principal = () -> "ada@example.com";

        user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");
        user.setName("Ada");

        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setWalletKind(WalletKind.CUSTOMER);

        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
    }

    @Test
    void myCreditsReturnsEmptyListWhenNoneExist() {
        when(flexCreditRepository.findByBeneficiaryWalletIdOrderByDisbursedAtDesc(10L))
                .thenReturn(List.of());

        List<FlexCreditResponse> result = flexController.myCredits(principal);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void myCreditsReturnsMappedCredits() {
        FlexCredit fc = flexCredit(FlexCreditStatus.RECOVERING);
        when(flexCreditRepository.findByBeneficiaryWalletIdOrderByDisbursedAtDesc(10L))
                .thenReturn(List.of(fc));

        List<FlexCreditResponse> result = flexController.myCredits(principal);

        assertEquals(1, result.size());
        assertEquals(FlexCreditStatus.RECOVERING, result.get(0).status());
        assertEquals(new BigDecimal("100.00"), result.get(0).amount());
    }

    @Test
    void myCreditsThrowsWhenUserNotFound() {
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> flexController.myCredits(principal));
    }

    @Test
    void myCreditsThrowsWhenWalletNotFound() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> flexController.myCredits(principal));
    }

    @ParameterizedTest
    @EnumSource(FlexCreditStatus.class)
    void myCreditsMapsAllStatuses(FlexCreditStatus status) {
        FlexCredit fc = flexCredit(status);
        when(flexCreditRepository.findByBeneficiaryWalletIdOrderByDisbursedAtDesc(10L))
                .thenReturn(List.of(fc));

        List<FlexCreditResponse> result = flexController.myCredits(principal);

        assertEquals(1, result.size());
        assertEquals(status, result.get(0).status());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private FlexCredit flexCredit(FlexCreditStatus status) {
        Transaction txn = new Transaction();
        txn.setId(100L);

        FlexCredit fc = new FlexCredit();
        fc.setId(1L);
        fc.setTransaction(txn);
        fc.setAmount(new BigDecimal("100.00"));
        fc.setAmountRecovered(BigDecimal.ZERO);
        fc.setAmountOutstanding(new BigDecimal("100.00"));
        fc.setStatus(status);
        fc.setBeneficiaryWallet(wallet);
        return fc;
    }
}

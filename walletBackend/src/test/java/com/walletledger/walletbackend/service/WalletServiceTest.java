package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.config.FlexProperties;
import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.dto.request.AddMoneyRequest;
import com.walletledger.walletbackend.dto.response.WalletResponse;
import com.walletledger.walletbackend.entity.Transaction;
import com.walletledger.walletbackend.entity.User;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.LedgerEntryRepository;
import com.walletledger.walletbackend.repository.TransactionRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class WalletServiceTest {

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private FlexService flexService;
    private FlexProperties flexProperties;
    private WalletService walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        userRepository        = mock(UserRepository.class);
        walletRepository      = mock(WalletRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        flexService           = mock(FlexService.class);
        flexProperties        = new FlexProperties(); // flex disabled by default

        walletService = new WalletService(
                userRepository, walletRepository, transactionRepository,
                ledgerEntryRepository, flexService, flexProperties);

        user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");

        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUser(user);
        wallet.setBalance(new BigDecimal("1000.00"));
        wallet.setDailySpent(BigDecimal.ZERO);
        wallet.setDailySpentDate(WalletService.today());
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setWalletKind(WalletKind.CUSTOMER);
        wallet.setFlexEnabled(false);
        wallet.setFlexDebt(BigDecimal.ZERO);

        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(99L);
            return t;
        });
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getWallet ─────────────────────────────────────────────────────────────

    @Test
    void getWalletReturnsBalanceAndFlexInfo() {
        wallet.setFlexEnabled(true);
        wallet.setFlexDebt(new BigDecimal("50.00"));

        WalletResponse response = walletService.getWallet("ada@example.com");

        assertEquals(new BigDecimal("1000.00"), response.availableBalance());
        assertTrue(response.flexEnabled());
        assertEquals(new BigDecimal("50.00"), response.flexDebt());
    }

    @Test
    void getWalletShowsTodaySpendingWhenDateMatches() {
        wallet.setDailySpent(new BigDecimal("250.00"));
        wallet.setDailySpentDate(WalletService.today());

        WalletResponse response = walletService.getWallet("ada@example.com");

        assertEquals(new BigDecimal("250.00"), response.todaySpending());
    }

    @Test
    void getWalletShowsZeroSpendingWhenDateIsDifferent() {
        wallet.setDailySpent(new BigDecimal("250.00"));
        wallet.setDailySpentDate(LocalDate.of(2020, 1, 1));

        WalletResponse response = walletService.getWallet("ada@example.com");

        assertEquals(BigDecimal.ZERO, response.todaySpending());
    }

    // ── addMoney ──────────────────────────────────────────────────────────────

    @Test
    void addMoneyCreditsFullAmountWhenNoFlexDebt() {
        WalletResponse response = walletService.addMoney("ada@example.com",
                new AddMoneyRequest(new BigDecimal("500.00")));

        assertEquals(new BigDecimal("1500.00"), response.availableBalance());
        verify(transactionRepository).save(argThat(t ->
                t.getAmount().compareTo(new BigDecimal("500.00")) == 0
                        && t.getStatus() == com.walletledger.walletbackend.dto.enums.TransactionStatus.SUCCESS));
    }

    @Test
    void addMoneyRepaysFlexDebtFirstWhenFlexIsEnabled() {
        flexProperties.setEnabled(true);
        wallet.setFlexDebt(new BigDecimal("200.00"));
        BigDecimal net = new BigDecimal("300.00"); // 500 - 200 debt
        when(flexService.repayDebtFromInbound(eq(wallet), eq(new BigDecimal("500.00")), any()))
                .thenReturn(net);

        walletService.addMoney("ada@example.com", new AddMoneyRequest(new BigDecimal("500.00")));

        // balance += net (300), not the full 500
        assertEquals(new BigDecimal("1300.00"), wallet.getBalance());
        verify(flexService).repayDebtFromInbound(any(), any(), any());
    }

    @Test
    void addMoneyRejectsTopUpToInactiveWallet() {
        wallet.setStatus(WalletStatus.INACTIVE);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.addMoney("ada@example.com",
                        new AddMoneyRequest(new BigDecimal("100.00"))));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void addMoneyRejectsTopUpToFrozenWallet() {
        wallet.setStatus(WalletStatus.FROZEN);

        assertThrows(BusinessRuleException.class,
                () -> walletService.addMoney("ada@example.com",
                        new AddMoneyRequest(new BigDecimal("100.00"))));
    }

    // ── setOwnStatus ──────────────────────────────────────────────────────────

    @Test
    void deactivatingAnActiveWalletSetsItToInactive() {
        WalletResponse response = walletService.setOwnStatus("ada@example.com", WalletStatus.INACTIVE);

        assertEquals(WalletStatus.INACTIVE, response.status());
    }

    @Test
    void reactivatingAnInactiveWalletSetsItToActive() {
        wallet.setStatus(WalletStatus.INACTIVE);

        WalletResponse response = walletService.setOwnStatus("ada@example.com", WalletStatus.ACTIVE);

        assertEquals(WalletStatus.ACTIVE, response.status());
    }

    @Test
    void userCannotSetWalletToFrozen() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.setOwnStatus("ada@example.com", WalletStatus.FROZEN));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void userCannotChangeStatusOfAFrozenWallet() {
        wallet.setStatus(WalletStatus.FROZEN);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.setOwnStatus("ada@example.com", WalletStatus.ACTIVE));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ── setFrozenByAdmin ──────────────────────────────────────────────────────

    @Test
    void adminCanFreezeActiveWallet() {
        walletService.setFrozenByAdmin(1L, true);

        assertEquals(WalletStatus.FROZEN, wallet.getStatus());
    }

    @Test
    void adminCanUnfreezeAFrozenWallet() {
        wallet.setStatus(WalletStatus.FROZEN);

        walletService.setFrozenByAdmin(1L, false);

        assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
    }

    @Test
    void freezingAlreadyFrozenWalletThrowsConflict() {
        wallet.setStatus(WalletStatus.FROZEN);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.setFrozenByAdmin(1L, true));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void unfreezingActiveWalletThrowsConflict() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.setFrozenByAdmin(1L, false));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ── toggleFlex ────────────────────────────────────────────────────────────

    @Test
    void toggleFlexEnablesItWhenCurrentlyDisabled() {
        wallet.setFlexEnabled(false);

        walletService.toggleFlex("ada@example.com");

        assertTrue(wallet.isFlexEnabled());
    }

    @Test
    void toggleFlexDisablesItWhenCurrentlyEnabled() {
        wallet.setFlexEnabled(true);

        walletService.toggleFlex("ada@example.com");

        assertFalse(wallet.isFlexEnabled());
    }

    // ── requireActive (static) ────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = {"INACTIVE", "FROZEN"})
    void requireActiveThrowsForNonActiveStatuses(WalletStatus status) {
        wallet.setStatus(status);

        assertThrows(BusinessRuleException.class, () -> WalletService.requireActive(wallet, "test"));
    }

    @Test
    void requireActiveDoesNotThrowForActiveWallet() {
        assertDoesNotThrow(() -> WalletService.requireActive(wallet, "test"));
    }

    // ── resetDailySpentIfNeeded (static) ──────────────────────────────────────

    @Test
    void resetDailySpentClearsCounterOnNewDay() {
        wallet.setDailySpent(new BigDecimal("999.00"));
        wallet.setDailySpentDate(LocalDate.of(2020, 1, 1));

        WalletService.resetDailySpentIfNeeded(wallet);

        assertEquals(BigDecimal.ZERO, wallet.getDailySpent());
        assertEquals(WalletService.today(), wallet.getDailySpentDate());
    }

    @Test
    void resetDailySpentDoesNothingWhenDateIsSameDay() {
        wallet.setDailySpent(new BigDecimal("100.00"));
        wallet.setDailySpentDate(WalletService.today());

        WalletService.resetDailySpentIfNeeded(wallet);

        assertEquals(new BigDecimal("100.00"), wallet.getDailySpent());
    }

    @Test
    void getWalletThrowsUnauthorizedWhenUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.getWallet("unknown@example.com"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void getWalletThrowsNotFoundWhenWalletNotFound() {
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.getWallet("ada@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void addMoneyThrowsNotFoundWhenWalletForUpdateNotFound() {
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> walletService.addMoney("ada@example.com", new AddMoneyRequest(new BigDecimal("100.00"))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}

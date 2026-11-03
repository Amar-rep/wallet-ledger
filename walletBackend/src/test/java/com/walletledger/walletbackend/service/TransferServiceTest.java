package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.*;
import com.walletledger.walletbackend.dto.request.SendMoneyRequest;
import com.walletledger.walletbackend.dto.response.TransferResponse;
import com.walletledger.walletbackend.entity.*;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class TransferServiceTest {

    private UserRepository userRepository;
    private WalletRepository walletRepository;
    private TransactionRepository transactionRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private FraudAlertRepository fraudAlertRepository;
    private NotificationService notificationService;
    private PasswordEncoder passwordEncoder;
    private FlexService flexService;
    private TransferService transferService;

    private User sender;
    private Wallet senderWallet;
    private User receiver;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        userRepository        = mock(UserRepository.class);
        walletRepository      = mock(WalletRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        ledgerEntryRepository = mock(LedgerEntryRepository.class);
        fraudAlertRepository  = mock(FraudAlertRepository.class);
        notificationService   = mock(NotificationService.class);
        flexService           = mock(FlexService.class);
        passwordEncoder       = new BCryptPasswordEncoder();

        transferService = new TransferService(
                userRepository, walletRepository, transactionRepository,
                ledgerEntryRepository, fraudAlertRepository, notificationService,
                passwordEncoder, flexService);

        // Default: two distinct users with working wallets
        sender = user(1L, "Amarnath", "amarnath@example.com");
        sender.setPinHash(passwordEncoder.encode("1234"));

        receiver = user(2L, "Sam", "sam@example.com");

        senderWallet = wallet(10L, sender, new BigDecimal("5000.00"));
        receiverWallet = wallet(11L, receiver, new BigDecimal("200.00"));

        when(userRepository.findByEmail("amarnath@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(receiverWallet));
        when(transactionRepository.findBySenderWalletIdAndRequestId(any(), any())).thenReturn(Optional.empty());
        when(transactionRepository.existsBySenderWalletIdAndReceiverWalletIdAndAmountAndInitiatedAtAfter(
                any(), any(), any(), any())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(100L);
            return t;
        });
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fraudAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(flexService.isEligible(any(), any(), anyBoolean())).thenReturn(false);
    }

    // ── Successful transfer ───────────────────────────────────────────────────

    @Test
    void sendMoneyDebitsSenderImmediatelyAndCreatesPendingTransaction() {
        TransferResponse response = transferService.sendMoney("amarnath@example.com", request("req-1", 2L, "1000.00"));

        assertEquals(TransactionStatus.PENDING, response.status());
        assertEquals(new BigDecimal("4000.00"), senderWallet.getBalance());
        assertEquals(new BigDecimal("1000.00"), senderWallet.getDailySpent());
        verify(transactionRepository).save(argThat(t ->
                t.getStatus() == TransactionStatus.PENDING
                        && t.getAmount().compareTo(new BigDecimal("1000.00")) == 0));
    }

    @Test
    void sendMoneyReturnsOriginalResponseOnDuplicateRequestId() {
        Transaction existing = new Transaction();
        existing.setId(50L);
        existing.setStatus(TransactionStatus.PENDING);
        existing.setAmount(new BigDecimal("500.00"));
        existing.setReceiverWallet(receiverWallet);
        existing.setSettlementDueDate(java.time.LocalDate.now().plusDays(3));
        when(transactionRepository.findBySenderWalletIdAndRequestId(10L, "req-dup"))
                .thenReturn(Optional.of(existing));

        TransferResponse response = transferService.sendMoney("amarnath@example.com",
                request("req-dup", 2L, "500.00"));

        assertEquals(50L, response.transactionId());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sendMoneyFlagsHighValueTransferAndCreatesAlert() {
        senderWallet.setBalance(new BigDecimal("10000.00"));
        transferService.sendMoney("amarnath@example.com", request("req-hv", 2L, "9000.00"));

        verify(fraudAlertRepository).save(argThat(a -> a.getAlertType() == FraudAlertType.HIGH_VALUE));
        verify(notificationService).notifyAdmins(
                eq(NotificationType.FRAUD_ALERT), any(), any(), any());
    }

    @Test
    void sendMoneyDisbursesFlex_whenReceiverIsEligible() {
        when(flexService.isEligible(eq(receiverWallet), any(), anyBoolean())).thenReturn(true);

        transferService.sendMoney("amarnath@example.com", request("req-flex", 2L, "300.00"));

        verify(flexService).disburse(any(Transaction.class), eq(senderWallet), eq(receiverWallet));
    }

    @Test
    void sendMoneyDoesNotDisburseFlex_whenNotEligible() {
        when(flexService.isEligible(any(), any(), anyBoolean())).thenReturn(false);

        transferService.sendMoney("amarnath@example.com", request("req-noflex", 2L, "300.00"));

        verify(flexService, never()).disburse(any(), any(), any());
    }

    // ── Rejections ────────────────────────────────────────────────────────────

    @Test
    void sendMoneyRejectsWrongPin() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com",
                        requestWithPin("req-pin", 2L, "100.00", "9999")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void sendMoneyRejectsInsufficientBalance() {
        senderWallet.setBalance(new BigDecimal("0.50"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-bal", 2L, "100.00")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void sendMoneyBlocksTransferThatExceedsDailyLimit() {
        senderWallet.setDailySpent(new BigDecimal("49000.00"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-limit", 2L, "2000.00")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        verify(fraudAlertRepository).save(argThat(a -> a.getAlertType() == FraudAlertType.DAILY_LIMIT));
    }

    @Test
    void sendMoneyBlocksDuplicateTransactionWithinWindow() {
        when(transactionRepository.existsBySenderWalletIdAndReceiverWalletIdAndAmountAndInitiatedAtAfter(
                any(), any(), any(), any())).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-dup2", 2L, "100.00")));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        verify(fraudAlertRepository).save(argThat(a -> a.getAlertType() == FraudAlertType.DUPLICATE));
    }

    @Test
    void sendMoneyRejectsSendingToSelf() {
        // receiver user id == sender user id
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com",
                        request("req-self", 1L, "100.00")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void sendMoneyRejectsInactiveSenderWallet() {
        senderWallet.setStatus(WalletStatus.INACTIVE);

        assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-inactive", 2L, "100.00")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sendMoneyRejectsFrozenSenderWallet() {
        senderWallet.setStatus(WalletStatus.FROZEN);

        assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-frozen", 2L, "100.00")));
        verify(transactionRepository, never()).save(any());
    }

    // ── Parameterized: all blocked-amount scenarios ───────────────────────────

    @ParameterizedTest
    @MethodSource("amountsBlockedByFraudRules")
    void sendMoneyBlocksKnownFraudScenarios(BigDecimal dailyAlreadySpent, BigDecimal amount,
            FraudAlertType expectedAlertType) {
        senderWallet.setDailySpent(dailyAlreadySpent);
        if (expectedAlertType == FraudAlertType.DUPLICATE) {
            when(transactionRepository.existsBySenderWalletIdAndReceiverWalletIdAndAmountAndInitiatedAtAfter(
                    any(), any(), any(), any())).thenReturn(true);
        }

        assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com",
                        request("req-fraud", 2L, amount.toPlainString())));

        verify(fraudAlertRepository).save(argThat(a -> a.getAlertType() == expectedAlertType));
    }

    static Stream<Arguments> amountsBlockedByFraudScenarios() {
        return Stream.of(
                Arguments.of(new BigDecimal("49000"), new BigDecimal("1001"), FraudAlertType.DAILY_LIMIT),
                Arguments.of(BigDecimal.ZERO, new BigDecimal("100"), FraudAlertType.DUPLICATE));
    }

    static Stream<Arguments> amountsBlockedByFraudRules() {
        return Stream.of(
                Arguments.of(new BigDecimal("49000.00"), new BigDecimal("1001.00"), FraudAlertType.DAILY_LIMIT),
                Arguments.of(BigDecimal.ZERO, new BigDecimal("100.00"), FraudAlertType.DUPLICATE));
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    @Test
    void searchReturnsUserWhenSearchingByEmail() {
        when(userRepository.findByEmail("sam@example.com")).thenReturn(Optional.of(receiver));

        com.walletledger.walletbackend.dto.response.UserSearchResponse response =
                transferService.search("amarnath@example.com", "sam@example.com");

        assertEquals(2L, response.userId());
        assertEquals("Sam", response.name());
    }

    @Test
    void searchReturnsUserWhenSearchingByPhone() {
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(receiver));

        com.walletledger.walletbackend.dto.response.UserSearchResponse response =
                transferService.search("amarnath@example.com", "9876543210");

        assertEquals(2L, response.userId());
    }

    @ParameterizedTest
    @MethodSource("invalidSearchQueries")
    void searchRejectsBlankQuery(String query) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.search("amarnath@example.com", query));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    static Stream<Arguments> invalidSearchQueries() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "));
    }

    @Test
    void searchThrowsNotFoundWhenUserDoesNotExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.search("amarnath@example.com", "nobody@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void searchThrowsBadRequestWhenSearchingSelf() {
        when(userRepository.findByEmail("amarnath@example.com")).thenReturn(Optional.of(sender));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.search("amarnath@example.com", "amarnath@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void sendMoneyThrowsUnauthorizedWhenSenderNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("unknown@example.com", request("req-x", 2L, "100.00")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void sendMoneyThrowsNotFoundWhenRecipientNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-x", 99L, "100.00")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void sendMoneyThrowsNotFoundWhenRecipientWalletNotFound() {
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> transferService.sendMoney("amarnath@example.com", request("req-x", 2L, "100.00")));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static SendMoneyRequest request(String requestId, Long receiverUserId, String amount) {
        return requestWithPin(requestId, receiverUserId, amount, "1234");
    }

    private static SendMoneyRequest requestWithPin(String requestId, Long receiverUserId,
            String amount, String pin) {
        return new SendMoneyRequest(requestId, receiverUserId, new BigDecimal(amount), "test remark", pin);
    }

    private static User user(Long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(UserRole.USER);
        return user;
    }

    private static Wallet wallet(Long id, User owner, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(owner);
        wallet.setBalance(balance);
        wallet.setDailySpent(BigDecimal.ZERO);
        wallet.setDailySpentDate(WalletService.today());
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setFlexDebt(BigDecimal.ZERO);
        wallet.setWalletKind(WalletKind.CUSTOMER);
        return wallet;
    }
}

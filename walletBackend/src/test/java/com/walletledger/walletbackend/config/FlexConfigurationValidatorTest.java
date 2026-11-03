package com.walletledger.walletbackend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlexConfigurationValidatorTest {

    @Test
    void acceptsDisabledFlexWithoutLookingUpAWallet() {
        FlexProperties properties = new FlexProperties();
        properties.setEnabled(false);
        assertDoesNotThrow(() -> new FlexConfigurationValidator(properties, mock(WalletRepository.class)).validate());
    }

    @Test
    void rejectsEnabledFlexWithoutSystemWalletId() {
        FlexProperties properties = enabledProperties();
        assertThrows(IllegalStateException.class,
                () -> new FlexConfigurationValidator(properties, mock(WalletRepository.class)).validate());
    }

    @ParameterizedTest
    @MethodSource("invalidWallets")
    void rejectsInvalidSystemWallets(Wallet wallet) {
        FlexProperties properties = enabledProperties();
        WalletRepository repository = mock(WalletRepository.class);
        when(repository.findById(7L)).thenReturn(Optional.of(wallet));

        assertThrows(IllegalStateException.class,
                () -> new FlexConfigurationValidator(properties, repository).validate());
    }

    static Stream<Arguments> invalidWallets() {
        return Stream.of(
                Arguments.of(wallet(WalletKind.CUSTOMER, WalletStatus.ACTIVE, BigDecimal.ZERO)),
                Arguments.of(wallet(WalletKind.SYSTEM, WalletStatus.FROZEN, BigDecimal.ZERO)),
                Arguments.of(wallet(WalletKind.SYSTEM, WalletStatus.ACTIVE, new BigDecimal("-1"))));
    }

    private static FlexProperties enabledProperties() {
        FlexProperties properties = new FlexProperties();
        properties.setEnabled(true);
        properties.setSystemWalletId(7L);
        return properties;
    }

    private static Wallet wallet(WalletKind kind, WalletStatus status, BigDecimal balance) {
        Wallet wallet = new Wallet();
        wallet.setWalletKind(kind);
        wallet.setStatus(status);
        wallet.setBalance(balance);
        return wallet;
    }
}

package com.walletledger.walletbackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.WalletRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WalletLockServiceTest {

    @ParameterizedTest
    @MethodSource("walletIdSets")
    void locksEachDistinctWalletInAscendingOrder(Long[] requested, int expectedSize) {
        WalletRepository repository = mock(WalletRepository.class);
        for (long id = 1; id <= 3; id++) {
            Wallet wallet = new Wallet();
            wallet.setId(id);
            when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(wallet));
        }

        var locked = new WalletLockService(repository).lockInOrder(requested);

        assertEquals(expectedSize, locked.size());
        assertEquals(locked.keySet().stream().sorted().toList(), locked.keySet().stream().toList());
    }

    static Stream<Arguments> walletIdSets() {
        return Stream.of(
                Arguments.of(new Long[]{3L, 1L, 2L}, 3),
                Arguments.of(new Long[]{2L, 2L, 1L}, 2),
                Arguments.of(new Long[]{1L}, 1));
    }

    @ParameterizedTest
    @MethodSource("missingWalletIds")
    void rejectsMissingWallets(Long id) {
        WalletRepository repository = mock(WalletRepository.class);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> new WalletLockService(repository).lockInOrder(id));
    }

    static Stream<Long> missingWalletIds() {
        return Stream.of(1L, 42L);
    }
}

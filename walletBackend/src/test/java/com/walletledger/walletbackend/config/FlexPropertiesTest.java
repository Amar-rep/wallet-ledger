package com.walletledger.walletbackend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlexPropertiesTest {

    @Test
    void defaultValuesAreExpected() {
        FlexProperties properties = new FlexProperties();

        assertFalse(properties.isEnabled());
        assertNull(properties.getSystemWalletId());
        assertEquals(new BigDecimal("9000.00"), properties.getMaxTransferAmount());
        assertFalse(properties.isAllowFlaggedTransfers());
    }

    @ParameterizedTest
    @MethodSource("propertyConfigurations")
    void settersAndGettersWorkAsExpected(boolean enabled, Long walletId, BigDecimal maxAmount, boolean allowFlagged) {
        FlexProperties properties = new FlexProperties();
        properties.setEnabled(enabled);
        properties.setSystemWalletId(walletId);
        properties.setMaxTransferAmount(maxAmount);
        properties.setAllowFlaggedTransfers(allowFlagged);

        assertEquals(enabled, properties.isEnabled());
        assertEquals(walletId, properties.getSystemWalletId());
        assertEquals(maxAmount, properties.getMaxTransferAmount());
        assertEquals(allowFlagged, properties.isAllowFlaggedTransfers());
    }

    static Stream<Arguments> propertyConfigurations() {
        return Stream.of(
                Arguments.of(true, 10L, new BigDecimal("5000.00"), true),
                Arguments.of(false, null, new BigDecimal("0.00"), false),
                Arguments.of(true, 999L, new BigDecimal("100000.00"), false));
    }
}

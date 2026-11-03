package com.walletledger.walletbackend.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flex Instant Credit feature configuration.
 * Bound from application.properties prefix "flex".
 * Registered via @EnableConfigurationProperties in WalletBackendApplication.
 */
@ConfigurationProperties(prefix = "flex")
public class FlexProperties {

    /** Master on/off switch. Set FLEX_ENABLED=true in environment to activate. */
    private boolean enabled = false;

    /**
     * ID of the system wallet that provides the float. Must be set when
     * enabled=true.
     */
    private Long systemWalletId;

    /** Maximum single transfer amount eligible for Flex disbursement. */
    private BigDecimal maxTransferAmount = new BigDecimal("9000.00");

    /** If false, flagged (high-value) transfers are excluded from Flex. */
    private boolean allowFlaggedTransfers = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getSystemWalletId() {
        return systemWalletId;
    }

    public void setSystemWalletId(Long systemWalletId) {
        this.systemWalletId = systemWalletId;
    }

    public BigDecimal getMaxTransferAmount() {
        return maxTransferAmount;
    }

    public void setMaxTransferAmount(BigDecimal maxTransferAmount) {
        this.maxTransferAmount = maxTransferAmount;
    }

    public boolean isAllowFlaggedTransfers() {
        return allowFlaggedTransfers;
    }

    public void setAllowFlaggedTransfers(boolean allowFlaggedTransfers) {
        this.allowFlaggedTransfers = allowFlaggedTransfers;
    }
}

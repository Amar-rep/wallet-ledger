package com.walletledger.walletbackend.config;

import java.math.BigDecimal;

import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates at startup that the configured system wallet is usable when
 * flex.enabled=true. Fails fast rather than allowing a mis-configured float
 * to corrupt balances at runtime.
 */
@Component
@RequiredArgsConstructor
public class FlexConfigurationValidator {

    private final FlexProperties flex;
    private final WalletRepository walletRepository;

    @PostConstruct
    public void validate() {
        if (!flex.isEnabled()) {
            return;
        }

        if (flex.getSystemWalletId() == null) {
            throw new IllegalStateException(
                    "Flex is enabled but flex.system-wallet-id is not configured. "
                            + "Set FLEX_SYSTEM_WALLET_ID in your environment.");
        }

        Wallet systemWallet = walletRepository.findById(flex.getSystemWalletId())
                .orElseThrow(() -> new IllegalStateException(
                        "Flex system wallet with id=" + flex.getSystemWalletId() + " does not exist. "
                                + "Run the database migration and set FLEX_SYSTEM_WALLET_ID correctly."));

        if (systemWallet.getUser() != null) {
            throw new IllegalStateException(
                    "Flex system wallet id=" + flex.getSystemWalletId()
                            + " has a linked user. The system wallet must have user_id = NULL.");
        }

        if (systemWallet.getWalletKind() != WalletKind.SYSTEM) {
            throw new IllegalStateException(
                    "Flex system wallet id=" + flex.getSystemWalletId()
                            + " has wallet_kind=" + systemWallet.getWalletKind()
                            + ". Fix it with: UPDATE wallets SET wallet_kind='SYSTEM' WHERE id="
                            + flex.getSystemWalletId() + ";");
        }

        if (systemWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Flex system wallet id=" + flex.getSystemWalletId()
                            + " is not ACTIVE (status=" + systemWallet.getStatus() + ").");
        }

        if (systemWallet.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Flex system wallet id=" + flex.getSystemWalletId()
                            + " has a negative balance. Inspect the system wallet before enabling Flex.");
        }
    }
}

package com.walletledger.walletbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.WalletKind;
import com.walletledger.walletbackend.dto.enums.WalletStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL for the system wallet; always set for CUSTOMER wallets. */
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private BigDecimal balance;
    private BigDecimal dailySpent;
    private LocalDate dailySpentDate;

    @Enumerated(EnumType.STRING)
    private WalletStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_kind", nullable = false)
    private WalletKind walletKind = WalletKind.CUSTOMER;

    /** Whether this wallet has opted in to Flex Instant Credit. */
    @Column(name = "flex_enabled", nullable = false)
    private boolean flexEnabled = false;

    /**
     * Outstanding Flex debt owed to the system wallet.
     * Repaid automatically from incoming funds before the remainder is credited.
     */
    @Column(name = "flex_debt", nullable = false)
    private BigDecimal flexDebt = BigDecimal.ZERO;

    @Version
    private Long version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

package com.walletledger.walletbackend.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flex_credits")
@Getter
@Setter
@NoArgsConstructor
public class FlexCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @ManyToOne
    @JoinColumn(name = "system_wallet_id", nullable = false)
    private Wallet systemWallet;

    @ManyToOne
    @JoinColumn(name = "beneficiary_wallet_id", nullable = false)
    private Wallet beneficiaryWallet;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "amount_recovered", nullable = false)
    private BigDecimal amountRecovered = BigDecimal.ZERO;

    @Column(name = "amount_outstanding", nullable = false)
    private BigDecimal amountOutstanding = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlexCreditStatus status;

    @Column(name = "disbursed_at", nullable = false)
    private OffsetDateTime disbursedAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "recovered_at")
    private OffsetDateTime recoveredAt;

    @Column(name = "written_off_at")
    private OffsetDateTime writtenOffAt;

    @Column(name = "write_off_reason", length = 500)
    private String writeOffReason;

    @Column(name = "written_off_by")
    private String writtenOffBy;

    @Version
    private Long version;
}

package com.walletledger.walletbackend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "daily_summary")
@Getter
@Setter
@NoArgsConstructor
public class DailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate summaryDate;
    private Integer transfersInitiated;
    private BigDecimal initiatedAmount;
    private Integer transfersSettled;
    private BigDecimal settledAmount;
    private Integer fraudAlertsRaised;
    private Integer newRegistrations;
    private BigDecimal pendingSettlementAmount;
    private BigDecimal totalDebitsSettled;
    private BigDecimal totalCreditsSettled;
    private Boolean reconciliationOk;
    private OffsetDateTime createdAt;
}

package com.walletledger.walletbackend.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "settlement_batches")
@Getter
@Setter
@NoArgsConstructor
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "job_log_id")
    private ScheduledJobLog jobLog;

    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer settledCount;
    private Integer failedCount;
    private Integer declinedCount;
    private BigDecimal totalAmountSettled;
}

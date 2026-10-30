package com.walletledger.walletbackend.entity;

import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.ScheduledJobStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scheduled_job_log")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;

    @Enumerated(EnumType.STRING)
    private ScheduledJobStatus status;

    private Integer recordsProcessed;
    private String errorMessage;
}

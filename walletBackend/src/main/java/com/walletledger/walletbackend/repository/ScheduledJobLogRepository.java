package com.walletledger.walletbackend.repository;

import com.walletledger.walletbackend.entity.ScheduledJobLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledJobLogRepository extends JpaRepository<ScheduledJobLog, Long> {
}

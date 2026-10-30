package com.walletledger.walletbackend.repository;

import com.walletledger.walletbackend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}

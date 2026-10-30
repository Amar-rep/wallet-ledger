package com.walletledger.walletbackend.repository;

import com.walletledger.walletbackend.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {
    Optional<SettlementBatch> findByJobLogId(Long jobLogId);
}

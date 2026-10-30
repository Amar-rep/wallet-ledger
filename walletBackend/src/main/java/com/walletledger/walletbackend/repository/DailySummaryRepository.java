package com.walletledger.walletbackend.repository;

import java.time.LocalDate;
import java.util.Optional;

import com.walletledger.walletbackend.entity.DailySummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySummaryRepository extends JpaRepository<DailySummary, Long> {

    Optional<DailySummary> findBySummaryDate(LocalDate summaryDate);
}

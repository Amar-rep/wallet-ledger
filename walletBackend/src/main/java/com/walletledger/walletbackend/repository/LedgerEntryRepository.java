package com.walletledger.walletbackend.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.walletledger.walletbackend.dto.enums.LedgerEntryType;
import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);

    @Query("select coalesce(sum(l.amount),0) from LedgerEntry l where l.entryType = :entry "
            + "and l.transaction.type = :type and l.transaction.status = :status "
            + "and l.transaction.settledAt >= :from and l.transaction.settledAt < :to")
    BigDecimal sumForSettled(@Param("entry") LedgerEntryType entry, @Param("type") TransactionType type,
                             @Param("status") TransactionStatus status, @Param("from") OffsetDateTime from,
                             @Param("to") OffsetDateTime to);
}

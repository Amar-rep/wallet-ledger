package com.walletledger.walletbackend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.TransactionStatus;
import com.walletledger.walletbackend.dto.enums.TransactionType;
import com.walletledger.walletbackend.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findBySenderWalletIdAndRequestId(Long senderWalletId, String requestId);

    boolean existsBySenderWalletIdAndReceiverWalletIdAndAmountAndInitiatedAtAfter(
            Long senderWalletId, Long receiverWalletId, BigDecimal amount, OffsetDateTime initiatedAfter);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :transactionId")
    Optional<Transaction> findByIdForUpdate(@Param("transactionId") Long transactionId);

    @Query("select t.id from Transaction t where t.status = :status and t.settlementDueDate <= :today "
            + "order by t.settlementDueDate, t.id")
    List<Long> findDueIds(@Param("status") TransactionStatus status, @Param("today") LocalDate today);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.settlementBatch.id = :batchId and t.status = :status")
    BigDecimal sumSettledInBatch(@Param("batchId") Long batchId, @Param("status") TransactionStatus status);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.senderWallet.id = :walletId and t.status in :statuses")
    BigDecimal sumSent(@Param("walletId") Long walletId, @Param("statuses") Collection<TransactionStatus> statuses);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.receiverWallet.id = :walletId "
            + "and t.type = :type and t.status = :status")
    BigDecimal sumReceived(@Param("walletId") Long walletId, @Param("type") TransactionType type,
                           @Param("status") TransactionStatus status);

    @Query("select count(t) from Transaction t where t.initiatedAt >= :from and "
            + "(t.senderWallet.id = :walletId or (t.receiverWallet.id = :walletId and t.status = :ok))")
    long countVisibleSince(@Param("walletId") Long walletId, @Param("ok") TransactionStatus ok,
                           @Param("from") OffsetDateTime from);

    @Query("select count(t) from Transaction t where t.type = :type and t.initiatedAt >= :from and t.initiatedAt < :to")
    long countInitiated(@Param("type") TransactionType type, @Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.type = :type "
            + "and t.initiatedAt >= :from and t.initiatedAt < :to")
    BigDecimal sumInitiated(@Param("type") TransactionType type, @Param("from") OffsetDateTime from,
                            @Param("to") OffsetDateTime to);

    @Query("select count(t) from Transaction t where t.type = :type and t.status = :status "
            + "and t.settledAt >= :from and t.settledAt < :to")
    long countSettled(@Param("type") TransactionType type, @Param("status") TransactionStatus status,
                      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.type = :type and t.status = :status "
            + "and t.settledAt >= :from and t.settledAt < :to")
    BigDecimal sumSettled(@Param("type") TransactionType type, @Param("status") TransactionStatus status,
                          @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("select coalesce(sum(t.amount),0) from Transaction t where t.type = :type and t.status = :status")
    BigDecimal sumByStatus(@Param("type") TransactionType type, @Param("status") TransactionStatus status);
}

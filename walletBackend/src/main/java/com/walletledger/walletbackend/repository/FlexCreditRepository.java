package com.walletledger.walletbackend.repository;

import java.util.List;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.entity.FlexCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlexCreditRepository extends JpaRepository<FlexCredit, Long> {

     Optional<FlexCredit> findByTransactionId(Long transactionId);

     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select fc from FlexCredit fc where fc.transaction.id = :transactionId")
     Optional<FlexCredit> findByTransactionIdForUpdate(@Param("transactionId") Long transactionId);

     /**
      * All RECOVERING records for a beneficiary, oldest first — used for debt
      * repayment ordering.
      */
     @Query("select fc from FlexCredit fc where fc.beneficiaryWallet.id = :walletId "
               + "and fc.status = :status order by fc.disbursedAt asc")
     List<FlexCredit> findByBeneficiaryWalletIdAndStatusOrderByDisbursedAt(
               @Param("walletId") Long walletId,
               @Param("status") FlexCreditStatus status);

     /** All credits for a beneficiary wallet, newest first. */
     @Query("select fc from FlexCredit fc where fc.beneficiaryWallet.id = :walletId order by fc.disbursedAt desc")
     List<FlexCredit> findByBeneficiaryWalletIdOrderByDisbursedAtDesc(@Param("walletId") Long walletId);
}

package com.walletledger.walletbackend.dto.enums;

public enum LedgerEntryType {
    DEBIT,
    CREDIT,
    REFUND,
    /** System wallet is debited when Flex disburses. */
    FLEX_DISBURSEMENT,
    /** Beneficiary is immediately credited by Flex. */
    FLEX_CREDIT,
    /** Original transfer proceeds repay the system wallet on settlement. */
    FLEX_SETTLEMENT,
    /**
     * Recovery from beneficiary (or later inbound funds) repays the system wallet.
     */
    FLEX_RECOVERY,
    /** Admin-approved write-off of unrecoverable outstanding debt. */
    FLEX_WRITE_OFF
}

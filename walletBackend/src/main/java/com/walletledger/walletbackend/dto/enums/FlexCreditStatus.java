package com.walletledger.walletbackend.dto.enums;

public enum FlexCreditStatus {
    /** Disbursed; original transfer still PENDING. */
    OPEN,
    /** Original transfer settled; system wallet repaid. */
    REPAID,
    /** Original transfer failed; partial recovery underway. */
    RECOVERING,
    /** Fully recovered — either immediately or via later inbound funds. */
    RECOVERED,
    /** Admin-approved write-off of unrecoverable outstanding amount. */
    WRITTEN_OFF
}

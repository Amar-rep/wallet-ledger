# Flex Instant Credit — Implementation Plan

## Goal

Allow a receiver who has opted in to receive the full amount of an eligible
pending transfer immediately. The platform's system wallet supplies that
temporary float. When the original transfer settles, its held funds repay the
system wallet.

This is a credit-risk feature. A later failed original transfer must be
recovered from the receiver; any amount that cannot be recovered immediately
is a receivable (`flex_debt`).

## Scope and decisions

- Feature name: **Flex Instant Credit**.
- Opt-in: disabled by default; customers explicitly enable it.
- Credit amount: 100% of the eligible transfer amount; no haircut or partial
  disbursement.
- Eligibility: receiver wallet is `ACTIVE`, Flex is enabled, original transfer
  is not flagged (unless configuration allows it), the amount is within the
  configured maximum, the system float is sufficient, and the receiver has no
  Flex debt.
- A system wallet is an operational wallet, not a fake customer or admin user.
  Its `user_id` is `NULL` and its `wallet_kind` is `SYSTEM`.
- There is no debt-recovery scheduler. Debt is repaid automatically from
  incoming money, before any remainder is credited to the customer balance.
- New Flex credits are prohibited while `flex_debt > 0`.

## Accounting lifecycle

### 1. Initiation and immediate credit

For a ₹5,000 Priya -> Amar transfer:

1. Existing transfer initiation debits Priya by ₹5,000 and creates a `PENDING`
   transaction.
2. Lock Priya's wallet, Amar's wallet, and the system wallet in ascending wallet
   ID order.
3. Check Flex eligibility.
4. Debit the system wallet ₹5,000 and credit Amar ₹5,000.
5. Create one `flex_credits` row with status `OPEN` and zero outstanding debt.
6. Create ledger entries for the system-wallet debit and Amar's Flex credit.

All six steps must commit in the same transaction. If any step fails, neither
the pending transfer nor the Flex disbursement must persist.

### 2. Normal settlement

When the hourly settlement job settles the original transfer:

1. Lock the transaction, original sender, original receiver, and system wallet
   in ascending wallet ID order.
2. If there is an `OPEN` Flex record, **do not credit Amar again**.
3. Credit the held ₹5,000 to the system wallet.
4. Mark the original transfer `SUCCESS` and the Flex record `REPAID`.
5. Write a `FLEX_SETTLEMENT` ledger entry against the system wallet.

### 3. Original transfer failure and recovery

Failure can arise from a declined receiver wallet at settlement, a settlement
exception (the job calls the failure method in a new transaction), or an admin
block before the due date.

For each failure path, in one transaction:

1. Lock the transfer, Priya's wallet, Amar's wallet, and the system wallet in
   ascending wallet ID order.
2. Refund Priya's original amount.
3. If there is no `OPEN` Flex record, finish using the existing refund path.
4. If there is an `OPEN` Flex record, calculate:

   ```text
   recovered = min(Amar.balance, flexCredit.amount)
   outstanding = flexCredit.amount - recovered
   ```

5. Debit Amar by `recovered` and credit the system wallet by the same amount.
6. If `outstanding = 0`, mark the Flex record `RECOVERED`.
7. Otherwise set `flexCredit.amount_outstanding = outstanding`, increment
   `Amar.flex_debt` by `outstanding`, and mark the record `RECOVERING`.
8. Create a `FLEX_RECOVERY` ledger entry for the recovered amount.

Recovery ignores Amar's wallet status. A frozen or inactive wallet is precisely
the situation in which recovery may be needed.

Example: Amar received ₹5,000, spent ₹3,500, then Priya's transfer fails.

```text
Priya:        refunded ₹5,000
Amar:         ₹1,500 balance -> ₹0
System wallet: receives ₹1,500
Amar debt:    ₹3,500
Flex status:  RECOVERING
```

The failure method must be idempotent: a Flex record not in `OPEN` must never
be refunded or recovered a second time.

### 4. Later debt repayment

Run this logic inside the transaction that credits money to a customer wallet:

- successful incoming settlement;
- customer top-up / add-money operation;
- future refund types only if product policy says they count as inbound funds.

Algorithm:

```text
repayment = min(incomingAmount, wallet.flex_debt)
customerCredit = incomingAmount - repayment
```

1. Credit `repayment` to the system wallet.
2. Credit only `customerCredit` to Amar's wallet.
3. Reduce Amar's `flex_debt` by `repayment`.
4. Apply repayment to `RECOVERING` Flex records oldest first, reducing each
   record's `amount_outstanding`.
5. Mark a Flex record `RECOVERED` when its outstanding amount reaches zero.
6. Write `FLEX_RECOVERY` ledger entries linked to the applicable original
   transfer(s).

Example: Amar owes ₹3,500 and receives ₹4,000. ₹3,500 repays the system wallet;
only ₹500 becomes Amar's available balance.

There is no scheduled collection action. A separate daily reminder job may be
added later, but it must not mutate money or debt.

## Configuration

Add environment-backed properties:

```properties
flex.enabled=${FLEX_ENABLED:false}
flex.system-wallet-id=${FLEX_SYSTEM_WALLET_ID:}
flex.max-transfer-amount=${FLEX_MAX_TRANSFER_AMOUNT:5000.00}
flex.allow-flagged-transfers=${FLEX_ALLOW_FLAGGED_TRANSFERS:false}
```

At application startup, when `flex.enabled=true`, validate that the configured
wallet exists, has `wallet_kind='SYSTEM'`, is `ACTIVE`, has no `user_id`, and
the balance is non-negative. Fail startup if validation fails.

## Database migration (PostgreSQL)

Run the following manually before implementation. This design does not create a
fake user. It changes `wallets.user_id` to permit a single system wallet with no
customer user.

```sql
BEGIN;

ALTER TABLE wallets
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS wallet_kind varchar(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN IF NOT EXISTS flex_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS flex_debt numeric(19,2) NOT NULL DEFAULT 0.00;

ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS chk_wallet_kind;

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_kind
    CHECK (wallet_kind IN ('CUSTOMER', 'SYSTEM'));

ALTER TABLE wallets
    DROP CONSTRAINT IF EXISTS chk_wallet_flex_debt_nonnegative;

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_flex_debt_nonnegative
    CHECK (flex_debt >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS ux_wallets_single_system_wallet
    ON wallets (wallet_kind)
    WHERE wallet_kind = 'SYSTEM';

CREATE TABLE IF NOT EXISTS flex_credits (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    transaction_id bigint NOT NULL UNIQUE REFERENCES transactions(id),
    system_wallet_id bigint NOT NULL REFERENCES wallets(id),
    beneficiary_wallet_id bigint NOT NULL REFERENCES wallets(id),
    amount numeric(19,2) NOT NULL,
    amount_recovered numeric(19,2) NOT NULL DEFAULT 0.00,
    amount_outstanding numeric(19,2) NOT NULL DEFAULT 0.00,
    status varchar(20) NOT NULL,
    disbursed_at timestamptz NOT NULL DEFAULT now(),
    settled_at timestamptz,
    recovered_at timestamptz,
    written_off_at timestamptz,
    write_off_reason varchar(500),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT chk_flex_credit_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_flex_credit_recovered_valid
        CHECK (amount_recovered >= 0 AND amount_recovered <= amount),
    CONSTRAINT chk_flex_credit_outstanding_valid
        CHECK (amount_outstanding >= 0 AND amount_outstanding <= amount),
    CONSTRAINT chk_flex_credit_status
        CHECK (status IN ('OPEN', 'REPAID', 'RECOVERING', 'RECOVERED', 'WRITTEN_OFF'))
);

CREATE INDEX IF NOT EXISTS idx_flex_credits_beneficiary_status
    ON flex_credits (beneficiary_wallet_id, status, disbursed_at);

CREATE INDEX IF NOT EXISTS idx_flex_credits_system_status
    ON flex_credits (system_wallet_id, status);

INSERT INTO wallets (
    user_id,
    balance,
    daily_spent,
    daily_spent_date,
    status,
    version,
    created_at,
    updated_at,
    wallet_kind,
    flex_enabled,
    flex_debt
)
SELECT
    NULL,
    1000000.00, -- Replace with approved, real platform float.
    0.00,
    CURRENT_DATE,
    'ACTIVE',
    0,
    now(),
    now(),
    'SYSTEM',
    false,
    0.00
WHERE NOT EXISTS (
    SELECT 1 FROM wallets WHERE wallet_kind = 'SYSTEM'
);

COMMIT;
```

Retrieve the configured system wallet ID after the migration:

```sql
SELECT id, balance, status, wallet_kind
FROM wallets
WHERE wallet_kind = 'SYSTEM';
```

Set that ID in `FLEX_SYSTEM_WALLET_ID`.

## Application changes

1. Map the new wallet fields and make the wallet-to-user relationship nullable.
   Customer APIs must reject `SYSTEM` wallets and must not dereference
   `wallet.getUser()` for them.
2. Add `FlexCredit` entity, repository, `FlexCreditStatus`, and a `FlexService`.
3. Add ledger enum values: `FLEX_DISBURSEMENT`, `FLEX_CREDIT`,
   `FLEX_SETTLEMENT`, `FLEX_RECOVERY`, and `FLEX_WRITE_OFF`.
4. Introduce one shared lock helper that locks all supplied wallet IDs in
   ascending order. Every Flex operation must include the system wallet.
5. At transfer initiation, create the pending transfer and disburse Flex in the
   same transaction if eligible.
6. At successful settlement, repay the system wallet rather than crediting the
   receiver a second time.
7. At declined settlement, failure fallback, and admin block, refund the sender
   and recover the receiver's Flex credit in the same transaction.
8. Apply debt repayment to eligible inbound credits before increasing the
   receiver's spendable balance.
9. Add protected admin functions to inspect Flex credits and write off debt.
   A write-off must preserve the record, record actor/reason/time, and emit a
   `FLEX_WRITE_OFF` ledger/audit event.

## Required tests

- Opted-out receiver is never instant-credited.
- Eligible receiver gets exactly one immediate full credit.
- Successful original settlement pays the system wallet and never double-credits
  the receiver.
- Failed original settlement refunds sender and fully recovers receiver funds.
- Partial recovery creates correct debt and later inbound funds repay it first.
- Frozen/inactive receiver can still be recovered from.
- Flex cannot be used while debt exists, for flagged transfers when disabled,
  above the configured maximum, or with insufficient system float.
- Retried job, repeated admin block, and repeated failure calls cause no double
  refund, disbursement, recovery, or system repayment.
- Concurrent opposing transfers do not deadlock and preserve balances.
- System wallet cannot appear in customer lookup, login, transfer-recipient, or
  ordinary admin wallet operations.

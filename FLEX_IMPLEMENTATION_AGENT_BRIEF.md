# Flex Instant Credit — Agent Implementation Brief

Implement the Flex Instant Credit feature described in
`FLEX_INSTANT_CREDIT_PLAN.md`. The database migration has already been run.
Do not redesign the schema or create a fake system user.

## Configuration

Store the system wallet ID in an environment variable, never as a Java
constant, database literal, or frontend value.

Add this to `application.properties`:

```properties
flex.enabled=${FLEX_ENABLED:false}
flex.system-wallet-id=${FLEX_SYSTEM_WALLET_ID:}
flex.max-transfer-amount=${FLEX_MAX_TRANSFER_AMOUNT:5000.00}
flex.allow-flagged-transfers=${FLEX_ALLOW_FLAGGED_TRANSFERS:false}
```

Set the value in the environment where the backend runs:

```text
FLEX_SYSTEM_WALLET_ID=<the id returned by the SYSTEM-wallet query>
```

Use a typed `@ConfigurationProperties(prefix = "flex")` class. Validate at
startup when `flex.enabled=true` that this ID exists, is `ACTIVE`, has
`wallet_kind='SYSTEM'`, and has no linked user. Fail startup with a clear error
if validation fails.

## Required structure

Keep responsibilities separated. Do not put Flex rules directly throughout
`TransferService`, `SettlementService`, and `WalletService`.

Create or extend these focused components:

```text
config/
  FlexProperties.java
  FlexConfigurationValidator.java

entity/
  FlexCredit.java

dto/enums/
  FlexCreditStatus.java
  WalletKind.java
  LedgerEntryType.java                  (add Flex entry types)

repository/
  FlexCreditRepository.java

service/
  FlexService.java                      (all Flex business rules)
  WalletLockService.java                (locks wallet IDs in ascending order)
```

`FlexService` owns:

- eligibility checks;
- immediate full Flex disbursement;
- successful-settlement repayment to the system wallet;
- recovery after original-transfer failure;
- repayment from later incoming funds;
- querying outstanding Flex debt;
- an auditable admin write-off operation.

Existing services remain orchestration points only:

- `TransferService`: after creating a pending transfer, ask `FlexService` to
  disburse if eligible, in the same transaction.
- `SettlementService`: on successful settlement call Flex repayment when an
  `OPEN` Flex record exists; on decline, fail, and admin block call Flex
  recovery.
- `WalletService`: before crediting an incoming amount, call Flex debt
  repayment and credit only the remainder to the customer.

## Critical invariants

1. The system wallet is not a customer wallet. It has `user_id = NULL` and
   `wallet_kind = SYSTEM`; customer-facing code must never show or select it.
2. Lock all involved wallets in ascending ID order. Flex paths lock sender,
   receiver, and system wallet. This prevents races and opposing-transfer
   deadlocks.
3. Every money mutation, Flex-record change, and associated ledger entry must
   happen in the same transaction.
4. A receiver gets the Flex amount exactly once. Do not credit the receiver a
   second time when the original transfer settles.
5. If the original transfer fails, refund the sender and recover from the
   receiver in the same transaction.
6. Recovery works even if the receiver is `FROZEN` or `INACTIVE`.
7. If the receiver balance is insufficient, persist the remainder as
   `wallet.flex_debt` and `flex_credits.amount_outstanding`. Never silently
   discard it.
8. Future incoming money repays Flex debt before increasing spendable balance.
9. `OPEN` is the only Flex status eligible for settlement repayment or failure
   recovery. Terminal states must be ignored on retries.
10. Store money as `BigDecimal`; compare with `compareTo`, never `equals`.

## Ledger types to add

Add these values to `LedgerEntryType`:

```text
FLEX_DISBURSEMENT  -- system wallet debit
FLEX_CREDIT        -- beneficiary immediate credit
FLEX_SETTLEMENT    -- original transfer proceeds paid to system wallet
FLEX_RECOVERY      -- recovery/debt repayment paid to system wallet
FLEX_WRITE_OFF     -- admin-approved unresolved loss
```

## Required behavior tests

- opt-out or ineligible receiver does not receive Flex;
- eligible receiver gets one immediate full credit;
- normal settlement credits the system wallet, never the receiver twice;
- failed transfer refunds sender and fully recovers receiver funds when present;
- partial recovery produces correct debt;
- future settlement/top-up pays debt first and credits only the remainder;
- frozen/inactive receiver can be recovered from;
- no new Flex credit while debt remains;
- repeated job execution / retry cannot double-disburse, double-refund, or
  double-recover;
- concurrent Flex operations lock consistently;
- system wallet cannot enter customer search, recipient selection, or normal
  wallet endpoints.

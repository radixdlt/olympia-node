# Transaction Validation

This doc presents the transaction validation rules by Radix Engine, which includes:
- Transaction limit check
- Stateless validation
- Stateful validation (including transaction fee check)

For transaction parsing, please check [this doc](./parsing.md).

## Transaction Limit

- The maximum number of signatures must be less or equal to `50` per round proposal
- The maximum user transaction size is `1024 * 1024` bytes

## Stateless Validation

Stateless validation checks if all transaction instructions are legit based on static rules.

If no violation is found, a list of parsed instructions, an optional message data, an optional signature and any feature flags should be returned.

### Per Instruction

#### Instruction Rules

- `HEADER`: 
   * Can appear **at most once** per transaction and must be **the first** instruction if exists;
   * The `version` must be `0x00`;
   * The `flags` must be `0x00` (default) or `0x01` (with resource allocation/de-allocation disabled).
- `SIG`: 
   * Can appear **at most once** per transaction and must be **the last** instruction if exists.
- `MSG`: 
   * Can appear **at most once** per transaction.
- `LDOWN` and `LREAD`:
   * The `index` operand must be less than the number of `UP` instructions before this instruction.
- `DOWNINDEX` and `READINDEX`:
   * The `prefix` length should be less than 10 and the first byte must be a valid `class_id`.
- `SYSCALL`:
   * The `calldata` length must match exactly what's required; see system functions section below.

#### Substate Static Check

If a substate is created by one instruction, its content must be statically checked:

| **Substate Type**                 | **Static Rules**                                                                                                                               |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `TOKEN_RESOURCE_METADATA`         | <ul><li>`description`: max 200 characters</li><li>`icon_url`: must be of OWASP URL format</li><li>`url`: must be of OWASP URL format</li></ul> |
| `TOKENS`                          | <ul><li>`amount`: must be non-zero</li><li>`owner`: must be an account address</li></ul>                                                       |
| `PREPARED_STAKE`                  | <ul><li>`amount`: must be non-zero</li></ul>                                                                                                   |
| `STAKE_OWNERSHIP`                 | <ul><li>`amount`: must be non-zero</li></ul>                                                                                                   |
| `VALIDATOR_META_DATA`             | <ul><li>`url`: must be of OWASP URL format</li></ul>                                                                                           |
| `VALIDATOR_RAKE_COPY`             | <ul><li>`rake_percentage`: must be in [0, 10000]</li></ul>                                                                                     |
| `VALIDATOR_OWNER_COPY`            | <ul><li>`owner`: must be an account address</li></ul>                                                                                          |

#### Signature Validation

The signature from `SIG` instructions must be verified against the hash of all instructions before this instruction and a valid public key should be computed.

### Per Instruction Group

Instructions are organized into groups, by splitting with `END` instruction.

Each instruction group must contain at least one state update instruction, which can be `UP`, `VDOWN`, `DOWN`, `LDOWN` and `DOWNINDEX`.

Each transaction must include at least one instruction group and incomplete instruction group is not allowed (not ended).

## Stateful Validation

After a transaction passes the stateless validation, it's validated against the ledger state, which consists of:
- **Transactions:** Committed transactions, indexed by transaction ID.
- **Substates:** A map of substates, indexed by substate ID.
- **Proofs:** Block headers, containing accumulator hashes, validator set and quorum certificate.

The program that enforces stateful validation is called Radix Constraint Machine (CM). Constraint machines are stateful and can run at different permission levels.

### Permission Levels

Permission levels are designed to allow/disallow certain actions.

| **Level**    | **Permissions Allowed**                                                                                                                                                                                               |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SYSTEM`     | <ul><li>To allocate native tokens</li><li>To skip transaction signature validation</li><li>To skip transaction fee validation</li><li>To conduct hard-forks when necessary (e.g. on safety/liveness breaks)</li></ul> |
| `SUPER_USER` | <ul><li>To update system epoch and view state</li></ul>                                                                                                                                                               |
| `USER`       | <ul><li>To conduct user transactions, like token transfer, stake and unstake</li></ul>                                                                                                                                |

Note that a higher-tier permission level is allowed for all actions granted by a lower-tier level.

### Validation State

Validation state is the internal state of the constraint machine, which includes:

| **Variable**                  | **Description**                                                                       |
|-------------------------------|---------------------------------------------------------------------------------------|
| `procedures`                  | All registered state transition procedures, ***immutable***                           |
| `virtual_substate_predicate`  | A predicate which tells if a particle can be virtually shut down, ***immutable***     |
| `cm_store`                    | A reference to the substate storage, ***immutable***                                  |
| `permission_level`            | The permission level, ***immutable***                                                 |
| `signer`                      | The transaction signer, ***immutable*** and ***optional***                            |
| `resource_mint_burn_disabled` | A flag indicates if resource allocation and deallocation is disabled, ***immutable*** |
| `current_instruction`         | The current instruction                                                               |
| `current_index`               | The index of the current instruction                                                  |
| `up_instruction_count`        | The number of `UP` instruction processed so far                                       |
| `current_state`               | The current current state                                                             |
| `end_expected`                | A flag indicates if an `END` instruction is expected                                  |
| `local_up_substates`          | A map of substates created locally, keyed off the substate index                      |
| `remote_down_substates`       | A set of substate IDs that are spun down remotely                                     |
| `meters`                      | Transaction execution meters                                                          |

The `meters` keep track of how many signature validations are left within the current proposal and bill transactions based on its usage.

#### Transition Procedure

A procedure decides whether a transition is allowed and how. It contains three parts:
- Defines the required permission level
- Defines how authorization should be performed
- Defines how to compute the next current state, given the current current state and instruction

A procedure is registered to a procedure key, which is tuple of `<current_state, operation, substate>`.

At mainnet, we have the following procedures.

| **Current state**                 | **Operation** | **Substate**             | **Transition**                                                        |
|-----------------------------------|---------------|--------------------------|-----------------------------------------------------------------------|
| AllocatingSystem                  | `UP`          | RoundData                | `EpochUpdateConstraintScrypt$$Lambda$205/0x0000000100250840`          |
| BootupValidator                   | `UP`          | ValidatorBFTData         | `EpochUpdateConstraintScrypt$$Lambda$241/0x0000000100259840`          |
| CreatingNextValidatorSet          | `READINDEX`   | ValidatorStakeData       | `EpochUpdateConstraintScrypt$$Lambda$239/0x0000000100259040`          |
| EndPrevRound                      | `DOWN`        | ValidatorBFTData         | `RoundUpdateConstraintScrypt$$Lambda$183/0x000000010024b040`          |
| EndPrevRound                      | `DOWN`        | EpochData                | `EpochUpdateConstraintScrypt$$Lambda$207/0x0000000100251040`          |
| LoadingStake                      | `DOWN`        | ValidatorStakeData       | `EpochUpdateConstraintScrypt$$Lambda$217/0x0000000100253840`          |
| NeedFixedTokenSupply              | `UP`          | TokensInAccount          | `TokensConstraintScryptV3$$Lambda$129/0x0000000100235040`             |
| NeedMetadata                      | `UP`          | TokenResourceMetadata    | `TokensConstraintScryptV3$$Lambda$131/0x0000000100235840`             |
| OwnerStakePrepare                 | `READ`        | ValidatorOwnerCopy       | `StakingConstraintScryptV4$$Lambda$161/0x000000010023d840`            |
| PreparingOwnerUpdate              | `DOWNINDEX`   | ValidatorOwnerCopy       | `EpochUpdateConstraintScrypt$$Lambda$227/0x0000000100256040`          |
| PreparingRakeUpdate               | `DOWNINDEX`   | ValidatorRakeCopy        | `EpochUpdateConstraintScrypt$$Lambda$223/0x0000000100255040`          |
| PreparingRegisteredUpdate         | `DOWNINDEX`   | ValidatorRegisteredCopy  | `EpochUpdateConstraintScrypt$$Lambda$231/0x0000000100257040`          |
| PreparingStake                    | `DOWNINDEX`   | PreparedStake            | `EpochUpdateConstraintScrypt$$Lambda$221/0x0000000100254840`          |
| PreparingUnstake                  | `DOWNINDEX`   | PreparedUnstakeOwnership | `EpochUpdateConstraintScrypt$$Lambda$215/0x0000000100253040`          |
| ProcessExittingStake              | `UP`          | TokensInAccount          | `EpochUpdateConstraintScrypt$$Lambda$211/0x0000000100252040`          |
| REAddrClaim                       | `UP`          | EpochData                | `EpochUpdateConstraintScrypt$$Lambda$203/0x0000000100250040`          |
| REAddrClaim                       | `UP`          | TokenResource            | `TokensConstraintScryptV3$$Lambda$127/0x0000000100234440`             |
| REAddrClaim                       | `END`         |                          | `MutexConstraintScrypt$$Lambda$175/0x0000000100249040`                |
| REAddrClaimStart                  | `DOWN`        | UnclaimedREAddr          | `SystemConstraintScrypt$$Lambda$153/0x000000010023b840`               |
| ResetOwnerUpdate                  | `UP`          | ValidatorOwnerCopy       | `EpochUpdateConstraintScrypt$$Lambda$229/0x0000000100256840`          |
| ResetRakeUpdate                   | `UP`          | ValidatorRakeCopy        | `EpochUpdateConstraintScrypt$$Lambda$225/0x0000000100255840`          |
| ResetRegisteredUpdate             | `UP`          | ValidatorRegisteredCopy  | `EpochUpdateConstraintScrypt$$Lambda$233/0x0000000100257840`          |
| RewardingValidators               | `DOWNINDEX`   | ValidatorBFTData         | `EpochUpdateConstraintScrypt$$Lambda$213/0x0000000100252840`          |
| StakeOwnershipHoldingBucket       | `UP`          | StakeOwnership           | `StakingConstraintScryptV4$$Lambda$169/0x000000010023e840`            |
| StakeOwnershipHoldingBucket       | `END`         |                          | `StakingConstraintScryptV4$$Lambda$173/0x0000000100248840`            |
| StakeOwnershipHoldingBucket       | `UP`          | PreparedUnstakeOwnership | `StakingConstraintScryptV4$$Lambda$171/0x0000000100248040`            |
| StakeOwnershipHoldingBucket       | `DOWN`        | StakeOwnership           | `StakingConstraintScryptV4$$Lambda$167/0x000000010023f040`            |
| StakePrepare                      | `UP`          | PreparedStake            | `StakingConstraintScryptV4$$Lambda$163/0x000000010023e040`            |
| Staking                           | `UP`          | StakeOwnership           | `EpochUpdateConstraintScrypt$$Lambda$235/0x0000000100258040`          |
| StartNextRound                    | `UP`          | RoundData                | `RoundUpdateConstraintScrypt$$Lambda$191/0x000000010024d040`          |
| StartValidatorBFTUpdate           | `UP`          | ValidatorBFTData         | `RoundUpdateConstraintScrypt$$Lambda$187/0x000000010024c040`          |
| StartValidatorBFTUpdate           | `DOWN`        | ValidatorBFTData         | `RoundUpdateConstraintScrypt$$Lambda$185/0x000000010024b840`          |
| StartingEpochRound                | `UP`          | RoundData                | `EpochUpdateConstraintScrypt$$Lambda$245/0x000000010025a840`          |
| StartingNextEpoch                 | `UP`          | EpochData                | `EpochUpdateConstraintScrypt$$Lambda$243/0x000000010025a040`          |
| TokenHoldingBucket                | `END`         |                          | `TokensConstraintScryptV3$$Lambda$135/0x0000000100236c40`             |
| TokenHoldingBucket                | `DOWN`        | TokensInAccount          | `TokensConstraintScryptV3$$Lambda$139/0x0000000100237c40`             |
| TokenHoldingBucket                | `SYSCALL`     |                          | `SystemConstraintScrypt$$Lambda$145/0x0000000100239840`               |
| TokenHoldingBucket                | `UP`          | TokensInAccount          | `TokensConstraintScryptV3$$Lambda$141/0x0000000100238440`             |
| TokenHoldingBucket                | `READ`        | AllowDelegationFlag      | `StakingConstraintScryptV4$$Lambda$159/0x000000010023d040`            |
| Unstaking                         | `UP`          | ExittingStake            | `EpochUpdateConstraintScrypt$$Lambda$219/0x0000000100254040`          |
| UpdatingDelegationFlag            | `UP`          | AllowDelegationFlag      | `ValidatorConstraintScryptV2$$Lambda$80/0x0000000100228440`           |
| UpdatingEpoch                     | `DOWNINDEX`   | ExittingStake            | `EpochUpdateConstraintScrypt$$Lambda$209/0x0000000100251840`          |
| UpdatingOwnerNeedToReadEpoch      | `READ`        | EpochData                | `ValidatorUpdateOwnerConstraintScrypt$$Lambda$115/0x0000000100231440` |
| UpdatingRakeNeedToReadCurrentRake | `READ`        | ValidatorStakeData       | `ValidatorUpdateRakeConstraintScrypt$$Lambda$95/0x000000010022c440`   |
| UpdatingRakeNeedToReadEpoch       | `READ`        | EpochData                | `ValidatorUpdateRakeConstraintScrypt$$Lambda$93/0x000000010022bc40`   |
| UpdatingRakeReady                 | `UP`          | ValidatorRakeCopy        | `ValidatorUpdateRakeConstraintScrypt$$Lambda$97/0x000000010022cc40`   |
| UpdatingRegistered                | `UP`          | ValidatorRegisteredCopy  | `ValidatorRegisterConstraintScrypt$$Lambda$107/0x000000010022f440`    |
| UpdatingRegisteredNeedToReadEpoch | `READ`        | EpochData                | `ValidatorRegisterConstraintScrypt$$Lambda$105/0x000000010022ec40`    |
| UpdatingValidatorBFTData          | `UP`          | ValidatorBFTData         | `RoundUpdateConstraintScrypt$$Lambda$189/0x000000010024c840`          |
| UpdatingValidatorInfo             | `UP`          | ValidatorMetaData        | `ValidatorConstraintScryptV2$$Lambda$72/0x00000001001ed440`           |
| UpdatingValidatorOwner            | `UP`          | ValidatorOwnerCopy       | `ValidatorUpdateOwnerConstraintScrypt$$Lambda$117/0x0000000100231c40` |
| UpdatingValidatorStakes           | `UP`          | ValidatorStakeData       | `EpochUpdateConstraintScrypt$$Lambda$237/0x0000000100258840`          |
| VoidReducerState                  | `UP`          | TokensInAccount          | `TokensConstraintScryptV3$$Lambda$133/0x0000000100236040`             |
| VoidReducerState                  | `DOWN`        | ValidatorRakeCopy        | `ValidatorUpdateRakeConstraintScrypt$$Lambda$91/0x000000010022b440`   |
| VoidReducerState                  | `DOWN`        | StakeOwnership           | `StakingConstraintScryptV4$$Lambda$165/0x000000010023f840`            |
| VoidReducerState                  | `DOWN`        | ValidatorOwnerCopy       | `ValidatorUpdateOwnerConstraintScrypt$$Lambda$113/0x0000000100230c40` |
| VoidReducerState                  | `DOWN`        | TokensInAccount          | `TokensConstraintScryptV3$$Lambda$137/0x0000000100237440`             |
| VoidReducerState                  | `DOWN`        | AllowDelegationFlag      | `ValidatorConstraintScryptV2$$Lambda$78/0x00000001001ebc40`           |
| VoidReducerState                  | `DOWN`        | ValidatorMetaData        | `ValidatorConstraintScryptV2$$Lambda$70/0x00000001001edc40`           |
| VoidReducerState                  | `DOWN`        | ValidatorRegisteredCopy  | `ValidatorRegisterConstraintScrypt$$Lambda$103/0x000000010022e440`    |
| VoidReducerState                  | `SYSCALL`     |                          | `SystemConstraintScrypt$$Lambda$147/0x000000010023a040`               |
| VoidReducerState                  | `DOWN`        | RoundData                | `RoundUpdateConstraintScrypt$$Lambda$181/0x000000010024a840`          |


### Validation Procedure

Constraint machine executes transaction instructions sequentially, based on the following flow:

![Validation Flow](./validation_flow.png)

1. Load the next instruction and update `current_instruction`, `current_index` and `up_instruction_count`
1. If `end_expected == true`
   * If `current_instruction != END`
      * Abort
1. Check if this instruction is allowed by the meters
   * If not, abort
1. If the current instruction is `SYSCALL`
   * Look up transition procedure with procedure key and abort if not found
   * Verify the required permission level
   * Verify authorization
   * Update `current_state` to the output of the state reducing rule
   * Update `end_expected` to `true` if `current_state` is void
1. If current instruction is `READ`, `LREAD`, `VREAD` or `READINDEX`
   * If `current_instruction == READINDEX`
      * Prepare an iterator for all the substates with the given prefix (including the ones in `local_up_substates` and excluding the ones in `remote_down_substates`)
   * Else
      * Assert the state to read and abort on error
   * Look up transition procedure with procedure key and abort if not found
   * Verify the required permission level
   * Verify authorization
   * Update `current_state` to the output of the state reducing rule
   * Update `end_expected` to `true` if `current_state` is void
1. If current instruction is `UP`, `VDOWN`, `DOWN`, `LDOWN` or `DOWNINDEX`
   * If `current_instruction == DOWNINDEX`
      * Prepare an iterator for all the substates with the given prefix (including the ones in `local_up_substates` and excluding the ones in `remote_down_substates`)
   * Else
      * Update `local_up_substates` and `remote_down_substates` according to the instruction and abort on error
   * Look up transition procedure with procedure key and abort if not found
   * Verify the required permission level
   * Verify authorization
   * Update `current_state` to the output of the state reducing rule
   * Update `end_expected` to `true` if `current_state` is void
1. If `current_instruction == END`
   * If `current_state` is not void
      * Look up transition procedure with procedure key and abort if not found
      * Verify the required permission level for burning the `current_state`, if not `SYSTEM`
      * Verify authorization for the `current_state`
      * Update `current_state` to the output of the state reducing rule (void expected)
   * Update `end_expected` to `false`
1. Jump to step 1

### Transaction Billing

At Radix, transactions are billed based on transaction size (bytes) and the number and type of new substates created.

Transaction bytes are charged

```
0.0002 XRD per byte
```

New substates are charged based on the following scheme:

| **Substate Type**                 | **Fee**        | **Description**                             |
|-----------------------------------|----------------|---------------------------------------------|
| `TOKENS`                          | 1000 XRD       | Create a new resource                       |
| `VALIDATOR_REGISTERED_FLAG_COPY`  | 5 XRD          | Update validator registered flag            |
| `VALIDATOR_RAKE_COPY`             | 5 XRD          | Update validator fee                        |
| `VALIDATOR_OWNER_COPY`            | 5 XRD          | Update validator owner                      |
| `VALIDATOR_META_DATA`             | 5 XRD          | Update validator metadata                   |
| `VALIDATOR_ALLOW_DELEGATION_FLAG` | 5 XRD          | Update validator allow delegation flag      |
| `PREPARED_STAKE`                  | 0.5 XRD        | Stake                                       |
| `PREPARED_UNSTAKE`                | 0.5 XRD        | Unstake                                     |

#### Mechanism

Transactions are billed as follows:
- Before a transaction is executed, it's granted a loan (`200 XRD`) from the system, and the loan goes directly into the fee reserve;
- Then, transaction size is immediately charged from the reserve and every instruction step is billed (currently, `UP` instructions only);
   * XRDs are expected to be deposited into the reserve through a combination of `HEADER`, `SYSCALL`, `DOWN` instructions;
- At the first non-`HEADER`/`SYSCALL`/`DOWN` instruction or transaction end, whichever is first, the system takes back the loan from the reserve.

If the fee reserve goes out of balance at any step, transaction aborts.

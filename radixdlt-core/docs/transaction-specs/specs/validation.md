# Transaction Validation

This doc describes how transactions are validated by Radix Engine, after [parsed](./parsing.md), which includes:
- Transaction limit check
- Stateless validation
- Stateful validation (including transaction fee check)

## Transaction Limit

- The maximum transaction size is `1024 * 1024` bytes.

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
- `LDOWN`:
   * The `index` operand must be less than the index of the current instruction.
- `DOWNALL`:
   * The `class_id` operand must be one of the supported substate type.

#### Substate Static Check

If a substate is created by one instruction, its content must be statically checked:

| **Substate Type** | **Static Rules** |
|-|-|
| `RE_ADDR` | <ul><li>None</li></ul> |
| `SYSTEM` | <ul><li>None</li></ul> |
| `TOKEN_DEF` | <ul><li>`description`: max 200 characters</li><li>`icon_url`: must be of OWASP URL format</li><li>`url`: must be of OWASP URL format</li></ul> |
| `TOKENS` | <ul><li>`amount`: must be non-zero</li><li>`owner`: must of an account address</li></ul> |
| `PREPARED_STAKE` | <ul><li>`amount`: must be non-zero</li></ul> |
| `VALIDATOR` | <ul><li>`url`: must be of OWASP URL format</li></ul> |
| `UNIQUE` | <ul><li>None</li></ul> |
| `TOKENS_LOCKED` | <ul><li>`amount`: must be non-zero</li><li>`owner`: must of an account address</li></ul> |
| `STAKE` | <ul><li>`amount`: must be non-zero</li></ul> |
| `ROUND_DATA` | <ul><li>None</li></ul> |
| `EPOCH_DATA` | <ul><li>None</li></ul> |
| `STAKE_SHARE` | <ul><li>`amount`: must be non-zero</li></ul> |
| `VALIDATOR_EPOCH_DATA` | <ul><li>None</li></ul> |
| `PREPARED_UNSTAKE` | <ul><li>None</li></ul> |
| `EXITING_STAKE` | <ul><li>None</li></ul> |

#### Signature Validation

The signature from `SIG` instructions must be verified against the hash of all instructions before this instruction and a valid public key should be computed.

### Per Instruction Group

Instructions are organized into groups, by splitting with `END` instruction.

Each instruction group must contain at least one state update instruction, which is `UP`, `VDOWN`, `VDOWNARG`, `DOWN`, `LDOWN` or `DOWNALL`.

## Stateful Validation

After a transaction passes the stateless validation, it's validated against the ledger state, which consists of:
- **Transactions:** Committed transactions, indexed by transaction ID.
- **Substates:** A map of substates, indexed by substate ID.
- **Proofs:** Block headers, containing accumulator hashes, validator set and quorum certificate.

The stateful validation occurs within a constraint machine, which runs at a given permission level and maintains it's internal state.

### Permission Levels

Permission levels are designed to allow/disallow certain actions.

| **Level** | **Permissions Allowed** |
|-|-|
| `SYSTEM` | <ul><li>To allocate native tokens</li><li>To skip transaction signature validation</li><li>To skip transaction fee validation</li><li>To conduct hard-forks when necessary (e.g. on safety/liveness breaks)</li></ul> |
| `SUPER_USER` | <ul><li>To update system epoch and view state</li></ul> |
| `USER` | <ul><li>To conduct user transactions, like token transfer, stake and unstake</li></ul> |

Note that a higher-tier permission level are granted all permissions from a lower-tier permission level.

### Validation State

Validation state is the internal state of the constraint machine, which includes:

| **Variable** | **Description** |
|-|-|
| `procedures` | All registered state transition procedures, ***immutable*** |
| `virtual_substate_predicate` | A predicate which tells if a particle can be virtually shut down, ***immutable*** |
| `cm_store` | A reference to the substate storage, ***immutable*** |
| `permission_level` | The permission level, ***immutable*** |
| `signer` | The transaction signer, ***immutable*** and ***optional*** |
| `resource_mint_burn_disabled` | A flag indicates if resource allocation and deallocation is disabled, ***immutable*** |
| `current_instruction` | The current instruction |
| `current_index` | The index of the current instruction |
| `reducer_state` | The current reducer state |
| `end_expected` | A flag indicates if an `END` instruction is expected |
| `local_up_substates` | A map of substates created locally, keyed off the substate index |
| `remote_down_substates` | A set of substate IDs that are spun down remotely |

#### Transition Procedure

A procedure decides whether a transition is allowed and how. It contains three parts:
- Defines the required permission level
- Defines how authorization should be performed
- Defines how to compute the next reducer state, given the current reducer state and instruction

A procedure is registered to a procedure key, which is pair of substate class and reducer state class.

In Betanet v3, we have the following registered procedures:

| **Reducer State** | **Transition Type** | **Substate** | **Permission Rule** | **Authorisation Rule** | **State Reducing Rule** |
|-|-|-|-|-|-|
| AllocatingSystem | `UP` | RoundData | `SystemConstraintScryptV2$$Lambda$350/0x000000080054bc40` | `SystemConstraintScryptV2$$Lambda$351/0x000000080054c040` | `SystemConstraintScryptV2$$Lambda$352/0x000000080054c440` |
| CreatingNextValidatorSet | `UP` | EpochData | `SystemConstraintScryptV2$$Lambda$389/0x0000000800555840` | `SystemConstraintScryptV2$$Lambda$390/0x0000000800555c40` | `SystemConstraintScryptV2$$Lambda$391/0x0000000800556040` |
| CreatingNextValidatorSet | `UP` | ValidatorEpochData | `SystemConstraintScryptV2$$Lambda$386/0x0000000800554c40` | `SystemConstraintScryptV2$$Lambda$387/0x0000000800555040` | `SystemConstraintScryptV2$$Lambda$388/0x0000000800555440` |
| LoadingStake | `DOWN` | ValidatorStake | `SystemConstraintScryptV2$$Lambda$371/0x0000000800551040` | `SystemConstraintScryptV2$$Lambda$372/0x0000000800551440` | `SystemConstraintScryptV2$$Lambda$373/0x0000000800551840` |
| NeedFixedTokenSupply | `UP` | TokensInAccount | `TokensConstraintScryptV2$$Lambda$302/0x0000000800531c40` | `TokensConstraintScryptV2$$Lambda$303/0x0000000800540040` | `TokensConstraintScryptV2$$Lambda$304/0x0000000800540440` |
| PreparingStake | `DOWNALL` | PreparedStake | `SystemConstraintScryptV2$$Lambda$377/0x0000000800552840` | `SystemConstraintScryptV2$$Lambda$378/0x0000000800552c40` | `SystemConstraintScryptV2$$Lambda$379/0x0000000800553040` |
| PreparingUnstake | `DOWNALL` | PreparedUnstakeOwnership | `SystemConstraintScryptV2$$Lambda$368/0x0000000800550440` | `SystemConstraintScryptV2$$Lambda$369/0x0000000800550840` | `SystemConstraintScryptV2$$Lambda$370/0x0000000800550c40` |
| ProcessExittingStake | `UP` | ExittingStake | `SystemConstraintScryptV2$$Lambda$359/0x000000080054e040` | `SystemConstraintScryptV2$$Lambda$360/0x000000080054e440` | `SystemConstraintScryptV2$$Lambda$361/0x000000080054e840` |
| ProcessExittingStake | `UP` | TokensInAccount | `SystemConstraintScryptV2$$Lambda$362/0x000000080054ec40` | `SystemConstraintScryptV2$$Lambda$363/0x000000080054f040` | `SystemConstraintScryptV2$$Lambda$364/0x000000080054f440` |
| REAddrClaim | `UP` | EpochData | `SystemConstraintScryptV2$$Lambda$347/0x000000080054b040` | `SystemConstraintScryptV2$$Lambda$348/0x000000080054b440` | `SystemConstraintScryptV2$$Lambda$349/0x000000080054b840` |
| REAddrClaim | `UP` | TokenResource | `TokensConstraintScryptV2$$Lambda$299/0x0000000800532040` | `TokensConstraintScryptV2$$Lambda$300/0x0000000800532440` | `TokensConstraintScryptV2$$Lambda$301/0x0000000800531840` |
| REAddrClaim | `UP` | UniqueParticle | `UniqueParticleConstraintScrypt$$Lambda$268/0x000000080052f440` | `UniqueParticleConstraintScrypt$$Lambda$269/0x000000080052f840` | `UniqueParticleConstraintScrypt$$Lambda$270/0x000000080052fc40` |
| RewardingValidators | `DOWNALL` | ValidatorEpochData | `SystemConstraintScryptV2$$Lambda$365/0x000000080054f840` | `SystemConstraintScryptV2$$Lambda$366/0x000000080054fc40` | `SystemConstraintScryptV2$$Lambda$367/0x0000000800550040` |
| RoundClosed | `DOWN` | EpochData | `SystemConstraintScryptV2$$Lambda$353/0x000000080054c840` | `SystemConstraintScryptV2$$Lambda$354/0x000000080054cc40` | `SystemConstraintScryptV2$$Lambda$355/0x000000080054d040` |
| RoundClosed | `UP` | RoundData | `SystemConstraintScryptV2$$Lambda$398/0x0000000800557c40` | `SystemConstraintScryptV2$$Lambda$399/0x0000000800558040` | `SystemConstraintScryptV2$$Lambda$400/0x0000000800558440` |
| StakeOwnershipHoldingBucket | `DOWN` | StakeOwnership | `StakingConstraintScryptV3$$Lambda$328/0x0000000800546440` | `StakingConstraintScryptV3$$Lambda$329/0x0000000800546840` | `StakingConstraintScryptV3$$Lambda$330/0x0000000800546c40` |
| StakeOwnershipHoldingBucket | `END` |  | `StakingConstraintScryptV3$$Lambda$337/0x0000000800548840` | `StakingConstraintScryptV3$$Lambda$338/0x0000000800548c40` | `StakingConstraintScryptV3$$Lambda$339/0x0000000800549040` |
| StakeOwnershipHoldingBucket | `UP` | PreparedUnstakeOwnership | `StakingConstraintScryptV3$$Lambda$334/0x0000000800547c40` | `StakingConstraintScryptV3$$Lambda$335/0x0000000800548040` | `StakingConstraintScryptV3$$Lambda$336/0x0000000800548440` |
| StakeOwnershipHoldingBucket | `UP` | StakeOwnership | `StakingConstraintScryptV3$$Lambda$331/0x0000000800547040` | `StakingConstraintScryptV3$$Lambda$332/0x0000000800547440` | `StakingConstraintScryptV3$$Lambda$333/0x0000000800547840` |
| Staking | `UP` | StakeOwnership | `SystemConstraintScryptV2$$Lambda$380/0x0000000800553440` | `SystemConstraintScryptV2$$Lambda$381/0x0000000800553840` | `SystemConstraintScryptV2$$Lambda$382/0x0000000800553c40` |
| StartingEpochRound | `UP` | RoundData | `SystemConstraintScryptV2$$Lambda$392/0x0000000800556440` | `SystemConstraintScryptV2$$Lambda$393/0x0000000800556840` | `SystemConstraintScryptV2$$Lambda$394/0x0000000800556c40` |
| TokenHoldingBucket | `DOWN` | TokensInAccount | `TokensConstraintScryptV2$$Lambda$314/0x0000000800542c40` | `TokensConstraintScryptV2$$Lambda$315/0x0000000800543040` | `TokensConstraintScryptV2$$Lambda$316/0x0000000800543440` |
| TokenHoldingBucket | `END` |  | `TokensConstraintScryptV2$$Lambda$308/0x0000000800541440` | `TokensConstraintScryptV2$$Lambda$309/0x0000000800541840` | `TokensConstraintScryptV2$$Lambda$310/0x0000000800541c40` |
| TokenHoldingBucket | `UP` | PreparedStake | `StakingConstraintScryptV3$$Lambda$322/0x0000000800544c40` | `StakingConstraintScryptV3$$Lambda$323/0x0000000800545040` | `StakingConstraintScryptV3$$Lambda$324/0x0000000800545440` |
| TokenHoldingBucket | `UP` | TokensInAccount | `TokensConstraintScryptV2$$Lambda$317/0x0000000800543840` | `TokensConstraintScryptV2$$Lambda$318/0x0000000800543c40` | `TokensConstraintScryptV2$$Lambda$319/0x0000000800544040` |
| TransitionToV2 | `DOWNALL` | ExittingStake | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$412/0x000000080055b440` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$413/0x000000080055b840` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$414/0x000000080055bc40` |
| TransitionToV2 | `UP` | SystemParticle | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$415/0x000000080055c040` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$416/0x000000080055c440` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$417/0x000000080055c840` |
| Unstaking | `UP` | ExittingStake | `SystemConstraintScryptV2$$Lambda$374/0x0000000800551c40` | `SystemConstraintScryptV2$$Lambda$375/0x0000000800552040` | `SystemConstraintScryptV2$$Lambda$376/0x0000000800552440` |
| UpdateValidatorEpochData | `DOWN` | ValidatorEpochData | `SystemConstraintScryptV2$$Lambda$401/0x0000000800558840` | `SystemConstraintScryptV2$$Lambda$402/0x0000000800558c40` | `SystemConstraintScryptV2$$Lambda$403/0x0000000800559040` |
| UpdatingEpoch | `DOWNALL` | ExittingStake | `SystemConstraintScryptV2$$Lambda$356/0x000000080054d440` | `SystemConstraintScryptV2$$Lambda$357/0x000000080054d840` | `SystemConstraintScryptV2$$Lambda$358/0x000000080054dc40` |
| UpdatingValidatorEpochData | `UP` | ValidatorEpochData | `SystemConstraintScryptV2$$Lambda$404/0x0000000800559440` | `SystemConstraintScryptV2$$Lambda$405/0x0000000800559840` | `SystemConstraintScryptV2$$Lambda$406/0x0000000800559c40` |
| UpdatingValidatorStakes | `UP` | ValidatorStake | `SystemConstraintScryptV2$$Lambda$383/0x0000000800554040` | `SystemConstraintScryptV2$$Lambda$384/0x0000000800554440` | `SystemConstraintScryptV2$$Lambda$385/0x0000000800554840` |
| ValidatorUpdate | `UP` | ValidatorParticle | `ValidatorConstraintScrypt$$Lambda$226/0x0000000800524c40` | `ValidatorConstraintScrypt$$Lambda$227/0x0000000800525040` | `ValidatorConstraintScrypt$$Lambda$228/0x0000000800525440` |
| VoidReducerState | `DOWN` | REAddrParticle | `CMAtomOS$$Lambda$209/0x0000000800520840` | `CMAtomOS$$Lambda$210/0x0000000800520c40` | `CMAtomOS$$Lambda$211/0x0000000800521040` |
| VoidReducerState | `DOWN` | RoundData | `SystemConstraintScryptV2$$Lambda$395/0x0000000800557040` | `SystemConstraintScryptV2$$Lambda$396/0x0000000800557440` | `SystemConstraintScryptV2$$Lambda$397/0x0000000800557840` |
| VoidReducerState | `DOWN` | StakeOwnership | `StakingConstraintScryptV3$$Lambda$325/0x0000000800545840` | `StakingConstraintScryptV3$$Lambda$326/0x0000000800545c40` | `StakingConstraintScryptV3$$Lambda$327/0x0000000800546040` |
| VoidReducerState | `DOWN` | SystemParticle | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$409/0x000000080055a840` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$410/0x000000080055ac40` | `SystemV1ToV2TransitionConstraintScrypt$$Lambda$411/0x000000080055b040` |
| VoidReducerState | `DOWN` | TokensInAccount | `TokensConstraintScryptV2$$Lambda$311/0x0000000800542040` | `TokensConstraintScryptV2$$Lambda$312/0x0000000800542440` | `TokensConstraintScryptV2$$Lambda$313/0x0000000800542840` |
| VoidReducerState | `DOWN` | ValidatorParticle | `ValidatorConstraintScrypt$$Lambda$223/0x0000000800524040` | `ValidatorConstraintScrypt$$Lambda$224/0x0000000800524440` | `ValidatorConstraintScrypt$$Lambda$225/0x0000000800524840` |
| VoidReducerState | `UP` | TokensInAccount | `TokensConstraintScryptV2$$Lambda$305/0x0000000800540840` | `TokensConstraintScryptV2$$Lambda$306/0x0000000800540c40` | `TokensConstraintScryptV2$$Lambda$307/0x0000000800541040` |

### Validation Procedure

Constraint machine executes each instruction from transaction sequentially, based on the following flow:

1. Load the next instruction and update `current_instruction` and `current_index`
1. If `end_expected == true`
   * If `current_instruction != END`
      * Abort
1. If `current_instruction` is a state update instruction
   * If `current_instruction == DOWNALL`
      * Prepare an iterator for all the substates of the substate type (including the ones in `local_up_substates` and excluding the ones in `remote_down_substates`)
   * Else
      * Update `local_up_substates` and `remote_down_substates` according to the instruction and abort on error
   * Look up transition procedure with procedure key and abort if not found
   * Verify the required permission level
   * Verify authorization
   * Update `reducer_state` to the output of the state reducing rule
   * Update `end_expected` to `true` if `reducer_state` is void
1. If `current_instruction == END`
   * If `reducer_state` is not void
      * Look up transition procedure with procedure key and abort if not found
      * Verify the required permission level for burning the `reducer_state`, if not `SYSTEM`
      * Verify authorization for the `reducer_state`
      * Update `reducer_state` to the output of the state reducing rule (void expected)
   * Update `end_expected` to `false`
1. If `current_instruction == SYSCALL`
   * Look up transition procedure with procedure key and abort if not found
   * Verify the required permission level
   * Verify authorization
   * Update `reducer_state` to the output of the state reducing rule
   * Update `end_expected` to `true` if `reducer_state` is void

![Validation Flow](./validation_flow.png)

### Transaction Fee

Currently, there is a minimum transaction fee of `0.1 XRD`.

Transaction fee is paid by spending tokens and making a system call. Any subsequent instructions (non-`DOWN`) will result in a failure if no transaction fee has been paid.

In addition, the transaction fee `SYSCALL` can occur **once only**.

Example transaction structure:
```
HEADER(0, 1)
DOWN <some_xrd_substate>
SYSCALL <0x00 (u8) + fee (u256)>
UP <xrd_remainder>
END
<remaining instructions>
```
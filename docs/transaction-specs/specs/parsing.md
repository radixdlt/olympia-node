# Transaction

A transaction is the serialization of actions that are cryptographically signed and broadcast to the Radix Network. Transactions trigger ledger state transitions, considering blockchain as a [replicated state machine](https://en.wikipedia.org/wiki/State_machine_replication). 

In this documentation, we describe the structure of Radix transaction. If you'd like to understand how transactions are validated, please switch to [the validation doc](./validation.md).

## Basics

This section explains how values of primitive types are read and written.

Unless otherwise specified, the order of bytes is alway [big endian](https://en.wikipedia.org/wiki/Endianness). 

### Boolean

A boolean value is encoded as a single byte:
- `0` represents false
- `1` represents true
- Other value are considered invalid

### Integers

| **Type** | **Length in Bytes** | **Description**          |
|----------|---------------------|--------------------------|
| `u8`     | 1                   | Unsigned 8-bit integer   |
| `u16`    | 2                   | Unsigned 16-bit integer  |
| `u32`    | 4                   | Unsigned 32-bit integer  |
| `u64`    | 8                   | Unsigned 64-bit integer  |
| `u256`   | 32                  | Unsigned 256-bit integer |
| `u384`   | 48                  | Unsigned 384-bit integer |

### Bytes

`bytes` is a byte array of variable length and is encoded as:
```
+-------------+-----------+
| length (u8) | raw_bytes |
+-------------+-----------+
```

### String

Strings are encoded as `bytes`, using `UTF-8` encoding.

### Hash

All hash values are of 32 bytes, and the hash algorithm being used is `sha_256_twice(x)`. To learn more about `SHA-256`, see [this wikipedia page](https://en.wikipedia.org/wiki/SHA-2).

### Public Key and Signature

In Radix, the [ECDSA scheme](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm) is used for public-key authentication.

Public keys are always encoded in its compressed form (33 bytes).

An ECDSA signatures consists of:

```
+--------+--------------+--------------+
| v (u8) | r (32 bytes) | s (32 bytes) |
+--------+--------------+--------------+
```

### Address

Addresses are used to identify a particular entity and resource. The first byte of an address describes the type, followed by additional data depending on the type.

Currently, there are 4 types of addresses:
- `0x00` - System
- `0x01` - Radix native token
- `0x02 + lower_26_bytes(hash(public_key + args))` - A resource address
- `0x03 + public_key` - An account address

At api/user level, addresses are wrapped into user-friendly identifiers:
- Account addresses are encoded as `bech32("xrd", address)` for mainnet and `bech32("brx", address)` for testnet
- Validator addresses are encoded as `bech32("vr", public_key)` for mainnet and `bech32("vb", public_key)` for testnet
- Resource addresses are encoded as `bench32(symbol + "_rr", address)` for mainnet and `bench32(symbol + "_rb", address)` for testnet

Bech32 encoding is specified [here](https://en.bitcoin.it/wiki/Bech32).

## Substate

A substate is an object that is stored in the ledger state, which can be viewed as a map of substates indexed by substate ID.

### Substate ID

For physical substate, the ID is the concatenation of transaction ID and instruction index (where the substate is defined).

```
+--------------+--------------+
| tx_id (hash) | index (u32)  |
+--------------+--------------+
```

For virtual substate, the ID is the hash of the serialization.

### Substate Type

Each substate is of a particular type and, when serialized, the first byte encodes the type info.

Currently, we have the following types:

| **Substate Type**                 | **Code** | **Description**                                                |
|-----------------------------------|----------|----------------------------------------------------------------|
| `UNCLAIMED_READDR`                | `0x00`   | Unclaimed Radix Engine address                                 |
| `ROUND_DATA`                      | `0x01`   | BFT consensus round data                                       |
| `EPOCH_DATA`                      | `0x02`   | BFT consensus epoch data                                       |
| `TOKEN_DEF`                       | `0x03`   | Token definition                                               |
| `TOKENS`                          | `0x04`   | Tokens                                                         |
| `PREPARED_STAKE`                  | `0x05`   | Prepared stake, will transition into stake ownership by system |
| `STAKE_OWNERSHIP`                 | `0x06`   | Stake ownership                                                |
| `PREPARED_UNSTAKE`                | `0x07`   | Prepared unstake                                               |
| `EXITTING_STAKE`                  | `0x08`   | Existing stake                                                 |
| `VALIDATOR_META_DATA`             | `0x09`   | Validator metadata, e.g. name and url                          |
| `VALIDATOR_STAKE_DATA`            | `0x0A`   | Validator stake data                                           |
| `VALIDATOR_BFT_DATA`              | `0x0B`   | Validator BFT data                                             |
| `VALIDATOR_ALLOW_DELEGATION_FLAG` | `0x0C`   | Validator allow delegation from others flag                    |
| `VALIDATOR_REGISTERED_FLAG_COPY`  | `0x0D`   | Validator registered flag copy                                 |
| `PREPARED_REGISTERED_FLAG_UPDATE` | `0x0E`   | Prepared registered flag copy                                  |
| `VALIDATOR_RAKE_COPY`             | `0x0F`   | Validator rake (fee) copy                                      |
| `PREPARED_RAKE_UPDATE`            | `0x10`   | Prepared validator rake update                                 |
| `VALIDATOR_OWNER_COPY`            | `0x11`   | Validator owner copy                                           |
| `PREPARED_VALIDATOR_OWNER_UPDATE` | `0x12`   | Prepared validator owner update                                |

### Substate Schema

Substates are serialized and deserialized based on the following protocol:
- The first byte indicates the substate type
- The following bytes are the fields based on the order they appear on the tables below.

#### `UNCLAIMED_READDR` (`0x00`)

| **Name**   | **Type**  | **Description**        |
|------------|-----------|------------------------|
| `reserved` | `u8`      | Reserved, always `0`   |
| `address`  | `address` | A Radix Engine address |

#### `ROUND_DATA` (`0x01`)

| **Name**    | **Type** | **Description**      |
|-------------|----------|----------------------|
| `reserved`  | `u8`     | Reserved, always `0` |
| `view`      | `u64`    | The new view         |
| `timestamp` | `u64`    | The new timestamp    |

#### `EPOCH_DATA` (`0x02`)

| **Name**   | **Type** | **Description**      |
|------------|----------|----------------------|
| `reserved` | `u8`     | Reserved, always `0` |
| `epoch`    | `u64`    | The new epoch        |

#### `TOKEN_DEF` (`0x03`)

| **Name**      | **Type**     | **Description**                                               |
|---------------|--------------|---------------------------------------------------------------|
| `type`        | `u8`         | The resource type (allowed values: `0x00`, `0x01` and `0x02`) |
| `supply`      | `u256`       | (If `type == 0x02`) The max token supply                      |
| `minter`      | `public_key` | (If `type == 0x01`) The token minter public key               |
| `resource`    | `address`    | The resource address                                          |
| `name`        | `string`     | The token name                                                |
| `description` | `string`     | The token description                                         |
| `url`         | `string`     | An URL                                                        |
| `icon_url`    | `string`     | An URL to an icon                                             |

#### `TOKENS` (`0x04`)

| **Name**   | **Type**  | **Description**      |
|------------|-----------|----------------------|
| `reserved` | `u8`      | Reserved, always `0` |
| `resource` | `address` | The resource address |
| `owner`    | `address` | The owner address    |
| `amount`   | `u256`    | The amount           |

#### `PREPARED_STAKE` (`0x05`)

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `owner`     | `address`    | The owner address        |
| `validator` | `public_key` | The validator public key |
| `amount`    | `u256`       | The stake amount         |

#### `STAKE_OWNERSHIP` (`0x06`)

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `validator` | `public_key` | The validator public key |
| `owner`     | `address`    | The stake owner          |
| `amount`    | `u256`       | The stake amount         |

#### `PREPARED_UNSTAKE` (`0x07`)

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `validator` | `public_key` | The validator public key |
| `owner`     | `u256`       | The owner                |
| `amount`    | `u256`       | The stake amount         |

#### `EXITING_STAKE` (`0x08`)

| **Name**         | **Type**     | **Description**          |
|------------------|--------------|--------------------------|
| `reserved`       | `u8`         | Reserved, always `0`     |
| `epoch_unlocked` | `u64`        | The unlocking epoch      |
| `validator`      | `public_key` | The validator public key |
| `owner`          | `u256`       | The owner                |
| `amount`         | `u256`       | The stake amount         |

#### `VALIDATOR_META_DATA` (`0x09`)

| **Name**         | **Type**     | **Description**                 |
|------------------|--------------|---------------------------------|
| `reserved`       | `u8`         | Reserved, always `0`            |
| `validator`      | `public_key` | The validator public key        |
| `name`           | `string`     | The validator name              |
| `url`            | `string`     | A link to the validator website |
| `fork_vote_hash` | `bytes`      | Validator's latest fork vote    |

#### `VALIDATOR_STAKE_DATA` (`0x0A`)

| **Name**          | **Type**     | **Description**                      |
|-------------------|--------------|--------------------------------------|
| `reserved`        | `u8`         | Reserved, always `0`                 |
| `is_registered`   | `bool`       | Whether this validator is registered |
| `amount`          | `u256`       | Total stake                          |
| `validator`       | `public_key` | Validator public key                 |
| `ownership`       | `u256`       | Total ownership                      |
| `rake_percentage` | `u32`        | Rake percentage                      |
| `owner`           | `address`    | Validator owner address              |

#### `VALIDATOR_BFT_DATA` (`0x0B`)

| **Name**              | **Type**     | **Description**              |
|-----------------------|--------------|------------------------------|
| `reserved`            | `u8`         | Reserved, always `0`         |
| `validator`           | `public_key` | Validator public key         |
| `proposals_completed` | `u64`        | Number of proposal completed |
| `proposals_missed`    | `u64`        | Number of proposal missed    |

#### `VALIDATOR_ALLOW_DELEGATION_FLAG` (`0x0C`)

| **Name**                | **Type**     | **Description**               |
|-------------------------|--------------|-------------------------------|
| `reserved`              | `u8`         | Reserved, always `0`          |
| `validator`             | `public_key` | Validator public key          |
| `is_delegation_allowed` | `bool`       | Whether delegation is allowed |

#### `VALIDATOR_REGISTERED_FLAG_COPY` (`0x0D`)

| **Name**        | **Type**     | **Description**                             |
|-----------------|--------------|---------------------------------------------|
| `reserved`      | `u8`         | Reserved, always `0`                        |
| `validator`     | `public_key` | Validator public key                        |
| `is_registered` | `bool`       | Whether this validator is registered active |

#### `PREPARED_REGISTERED_FLAG_UPDATE` (`0x0E`)

| **Name**         | **Type**     | **Description**                                |
|------------------|--------------|------------------------------------------------|
| `reserved`       | `u8`         | Reserved, always `0`                           |
| `validator`      | `public_key` | Validator public key                           |
| `is_registered`  | `bool`       | Whether this validator is registered as active |
| `fork_vote_hash` | `bytes`      | Validator's latest fork vote                   |

#### `VALIDATOR_RAKE_COPY` (`0x0F`)

| **Name**          | **Type**     | **Description**           |
|-------------------|--------------|---------------------------|
| `reserved`        | `u8`         | Reserved, always `0`      |
| `validator`       | `public_key` | Validator public key      |
| `rake_percentage` | `u32`        | Validator rake percentage |

#### `PREPARED_RAKE_UPDATE` (`0x10`)

| **Name**                  | **Type**     | **Description**         |
|---------------------------|--------------|-------------------------|
| `reserved`                | `u8`         | Reserved, always `0`    |
| `epoch`                   | `u64`        | Epoch                   |
| `validator`               | `public_key` | Validator public key    |
| `current_rake_percentage` | `u32`        | Current rake percentage |
| `next_rake_percentage`    | `u32`        | Next rake percentage    |

#### `VALIDATOR_OWNER_COPY` (`0x11`)

| **Name**    | **Type**     | **Description**         |
|-------------|--------------|-------------------------|
| `reserved`  | `u8`         | Reserved, always `0`    |
| `validator` | `public_key` | Validator public key    |
| `owner`     | `address`    | Validator owner address |

#### `PREPARED_VALIDATOR_OWNER_UPDATE` (`0x12`)

| **Name**    | **Type**     | **Description**         |
|-------------|--------------|-------------------------|
| `reserved`  | `u8`         | Reserved, always `0`    |
| `validator` | `public_key` | Validator public key    |
| `owner`     | `address`    | Validator owner address |


## Transaction Format

A transaction consists of an array of instructions. The hash output of its serialization is considered as **Transaction ID**.

Each instruction consists of
- One opcode (1 byte)
- One optional operand (of variable length)

The table below summarizes all opcodes.

| **Opcode**  | **Byte Value** | **Operand**         | **Description**                               |
|-------------|----------------|---------------------|-----------------------------------------------|
| `END`       | `0x00`         | None                | Mark the end of an action                     |
| `UP`        | `0x01`         | `substate`          | Boot up a new substate                        |
| `VDOWN`     | `0x02`         | `substate`          | Spin down a virtual substate                  |
| `VDOWNARG`  | `0x03`         | `substate + string` | Spin down a virtual substate with arguments   |
| `DOWN`      | `0x04`         | `substate_id`       | Spin down a remote substate                   |
| `LDOWN`     | `0x05`         | `substate_index`    | Spin down a local substate                    |
| `MSG`       | `0x06`         | `bytes`             | Record a message                              |
| `SIG`       | `0x07`         | `signature`         | Provide a signature for prior instructions    |
| `DOWNALL`   | `0x08`         | `class_id`          | Spin down all substates of the given class    |
| `SYSCALL`   | `0x09`         | `call_data`         | Make a system call                            |
| `HEADER`    | `0x0A`         | `version + flags`   | Specify headers                               |
| `DOWNINDEX` | `0x0B`         | `prefix`            | Spin down all substates with the given prefix |
| `LREAD`     | `0x0C`         | `substate_index`    | Read a local substate                         |
| `VREAD`     | `0x0D`         | `substate`          | Read a virtual substate                       |
| `READ`      | `0x0E`         | `substate_id`       | Read a remote substate                        |

### `END` (`0x00`)

An `END` instruction marks the end of an instruction group (a.k.a. action).

### `UP` (`0x01`)

An `UP` instruction instructs Radix Engine to create a new remote substate. The operand is the serialization of the new substate.

### `VDOWN` (`0x02`)

A `VDOWN` instruction spins down a virtual substate (which is not stored in global state).

### `VDOWNARG` (`0x03`)

A `VDOWNARG` instruction spins down a virtual substate with an additional argument. 

One use case is to create a new token by spinning down a `UNCLAIMED_READDR` substate and booting up a `TOKEN_DEF` substate, where the argument is the token symbol.

### `DOWN` (`0x04`)

A `DOWN` instruction spins down a remote substate. The operand is substate id.

### `LDOWN` (`0x05`)

A `LDOWN` instruction spins down a local substate created by this transaction. The operand is an index (`u32`).

### `MSG` (`0x06`)

A `MSG` instruction is used to emit a message. The operand is a `bytes`.

### `SIG` (`0x07`)

A `SIG` instruction provides a signature for authenticating all instructions before. The operand is a `signature`.

### `DOWNALL` (`0x08`)

A `DOWNALL` instruction spins down all substates of a particular type. The operand is a class ID (`u8`).

### `SYSCALL` (`0x09`)

A `SYSCALL` instruction allows a system invocation, e.g. to pay transaction fee. The operand `call_data` is a `bytes`.

### `HEADER` (`0x0A`)

A `HEADER` instruction specifies transaction meta. Currently, we have the following structure:

```
+--------------+--------------+
| version (u8) | flags (u8)   |
+--------------+--------------+
```

Please check [validation rules](./validation.md) for the allowed values.

### `DOWNINDEX` (`0x0B`)

A `DOWNINDEX` instruction spins down all substates whose serialization has the given prefix (`bytes`) as in operand.

### `LREAD` (`0x0C`)

A `LREAD` instruction reads a local substate.

### `VREAD` (`0x0D`)

A `VREAD` instruction reads a virtual substate.

### `READ` (`0x0E`)

A `READ` instruction reads a remote substate.

## Error Handling

Any unrecognized entity in the raw transaction should result in a parsing exception.

Most of the data structures are of variable length. In case the required data is more than the remaining unparsed transaction bytes, a.k.a. underflow, a parsing exception should also be raised.

While Radix transaction is modelled on bytebuffer, it's client's responsibility to ensure memory safety during the parsing phase.
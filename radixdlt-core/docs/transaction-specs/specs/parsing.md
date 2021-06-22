# Transaction

A transaction is the serialization of a command by an external actor that is broadcast to the Radix network and collated into blocks. Transactions are the source of global state transitions, considering blockchain as a [replicated state machine](https://en.wikipedia.org/wiki/State_machine_replication). 

In this documentation, we cover the structure specification of Radix transactions. If you'd like to understand how transactions are validated, please go [here](./validation.md).

## Basics

This section explains how values of primitive types are read and written.

Unless otherwise specified, the order of bytes is alway [big endian](https://en.wikipedia.org/wiki/Endianness). 

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

All hash values are of 32 bytes and the hash algorithm being used is `sha_256_twice(x)`. To learn more about `SHA-256`, see [this wikipedia page](https://en.wikipedia.org/wiki/SHA-2).

### Public Key and Signature

In Radix, the [ECDSA scheme](https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm) is used for public-key authentication.

Public keys are always encoded in its compressed form (33 bytes) and signatures are encoded as:

```
+--------+--------------+--------------+
| v (u8) | r (32 bytes) | s (32 bytes) |
+--------+--------------+--------------+
```

### Address

Addresses are used to identify a particular entity and resource. The first byte of an address describes the type of address, followed by additional data depending on the type.

Currently, there are 4 types of addresses:
- `0x00` - System
- `0x01` - Radix native token
- `0x02 + lower_26_bytes(hash(public_key + args))` - A resource address
- `0x03 + public_key` - An account address

The above schema is a low-level representation of address. At a higher level, address may be wrapped into user-friendly identifiers:
- Account addresses are encoded as `bech32("xrd", address)` for mainnet and `bech32("brx", address)` for testnet
- Validator addresses are encoded as `bech32("vr", public_key)` for mainnet and `bech32("vb", public_key)` for testnet
- Resource addresses are encoded as `bench32(symbol + "_rr", address)` for mainnet and `bench32(symbol + "_rb", address)` for testnet

Bech32 encoding is specified [here](https://en.bitcoin.it/wiki/Bech32).

## Substate

A substate is an object that is stored in the global state, a map of substates keyed off substate ID.

### Substate ID

For physical substate, the ID is the concatenation of transaction ID and substate index (the number of the instruction that has introduced the substate).

```
+--------------+--------------+
| tx_id (hash) | index (u32)  |
+--------------+--------------+
```

For virtual substate, the ID is the hash of the serialization.

### Substate Type

Each substate is of a particular type and, when serialized, the first byte of a substate encodes the type info.

Currently, we have the following types:

| **Substate Type**      | **Byte Value** | **Description**                                            |
|------------------------|----------------|------------------------------------------------------------|
| `RE_ADDR`              | `0x00`         | Radix Engine address                                       |
| `SYSTEM`               | `0x01`         | System state                                               |
| `TOKEN_DEF`            | `0x02`         | Definition of a token                                      |
| `TOKENS`               | `0x03`         | Tokens                                                     |
| `PREPARED_STAKE`       | `0x04`         | Stake                                                      |
| `VALIDATOR`            | `0x05`         | Registered validator                                       |
| `UNIQUE`               | `0x06`         | Unique address                                             |
| `TOKENS_LOCKED`        | `0x07`         | Locked tokens                                              |
| `STAKE`                | `0x08`         | Stake                                                      |
| `ROUND_DATA`           | `0x09`         | BFT consensus round data                                   |
| `EPOCH_DATA`           | `0x0A`         | BFT consensus epoch data                                   |
| `STAKE_SHARE`          | `0x0B`         | The share of an owner to a validator                       |
| `VALIDATOR_EPOCH_DATA` | `0x0C`         | Validator epoch data                                       |
| `PREPARED_UNSTAKE`     | `0x0D`         | Unstake                                                    |
| `EXITING_STAKE`        | `0x0E`         | Stake that is pending exit                                 |


### Substate Schema

#### `RE_ADDR`

| **Name**  | **Type**  | **Description**        |
|-----------|-----------|------------------------|
| `address` | `address` | A Radix Engine address |

#### `SYSTEM`

| **Name**    | **Type** | **Description**                   |
|-------------|----------|-----------------------------------|
| `epoch`     | `u64`    | The current epoch                 |
| `view`      | `u64`    | The current view                  |
| `timestamp` | `u64`    | The current timestamp             |

#### `TOKEN_DEF`

| **Name**      | **Type**  | **Description**                                               |
|---------------|-----------|---------------------------------------------------------------|
| `rri`         | `address` | The resource address                                          |
| `type`        | `u8`      | The resource type (allowed values: `0x00`, `0x01` and `0x02`) |
| `supply`      | `u256`    | (If `type == 0x02`) The max token supply                      |
| `minter`      | `address` | (If `type == 0x01`) The token minter address                  |
| `name`        | `string`  | The token name                                                |
| `description` | `string`  | The token description                                         |
| `url`         | `string`  | An URL                                                        |
| `icon_url`    | `string`  | An URL to an icon                                             |

#### `TOKENS`

| **Name** | **Type**  | **Description**      |
|----------|-----------|----------------------|
| `rri`    | `address` | The resource address |
| `owner`  | `address` | The owner address    |
| `amount` | `u256`    | The amount           |

#### `PREPARED_STAKE`

| **Name**   | **Type**     | **Description**          |
|------------|--------------|--------------------------|
| `owner`    | `address`    | The owner address        |
| `delegate` | `public_key` | The validator public key |
| `amount`   | `u256`       | The stake amount         |

#### `VALIDATOR`

| **Name**        | **Type**     | **Description**                                          |
|-----------------|--------------|----------------------------------------------------------|
| `key`           | `public_key` | The validator public key                                 |
| `is_registered` | `u8`         | Whether this validator is registered - true for non-zero |
| `name`          | `string`     | The validator name                                       |
| `url`           | `string`     | A link to the validator website                          |

#### `UNIQUE`

| **Name**  | **Type**  | **Description**        |
|-----------|-----------|------------------------|
| `address` | `address` | A Radix Engine address |

#### `TOKENS_LOCKED`

| **Name**         | **Type**  | **Description**      |
|------------------|-----------|----------------------|
| `rri`            | `address` | The resource address |
| `owner`          | `address` | The owner address    |
| `amount`         | `u256`    | The amount           |
| `epoch_unlocked` | `u64`     | The epoch unlocked   |

#### `STAKE`

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `delegate`  | `public_key` | The validator public key |
| `amount`    | `u256`       | The stake amount         |
| `ownership` | `u256`       | The ownership amount     |

#### `ROUND_DATA`

| **Name**    | **Type** | **Description**      |
|-------------|----------|----------------------|
| `view`      | `u64`   | The current view      |
| `timestamp` | `u64`   | The current timestamp |

#### `EPOCH_DATA`

| **Name**    | **Type** | **Description**      |
|-------------|----------|----------------------|
| `epoch`     | `u64`   | The current epoch     |

#### `STAKE_SHARE`

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `delegate`  | `public_key` | The validator public key |
| `owner`     | `address`    | The stake owner          |
| `amount`    | `u256`       | The stake amount         |

#### `VALIDATOR_EPOCH_DATA`

| **Name**              | **Type**     | **Description**                   |
|-----------------------|--------------|-----------------------------------|
| `key`                 | `public_key` | The validator public key          |
| `proposals_completed` | `u64`        | The number of proposals completed |

#### `PREPARED_UNSTAKE`

| **Name**         | **Type**     | **Description**          |
|------------------|--------------|--------------------------|
| `delegate`       | `public_key` | The validator public key |
| `owner`          | `u256`       | The owner                |
| `amount`         | `u256`       | The stake amount         |

#### `EXITING_STAKE`

| **Name**         | **Type**     | **Description**          |
|------------------|--------------|--------------------------|
| `epoch_unlocked` | `u64`        | The unlocking epoch      |
| `delegate`       | `public_key` | The validator public key |
| `owner`          | `u256`       | The owner                |
| `amount`         | `u256`       | The stake amount         |


## Transaction Format

A transaction, in its binary form, is the serialization of instructions. The hash value of the serialization is called  **Transaction ID**.

Each instruction consists of
- An opcode (1 byte)
- An optional operand (variable length)

The tables below summarizes all supported opcodes.

| **Opcode** | **Byte Value** | **Operand**                | **Description**                               |
|------------|----------------|----------------------------|-----------------------------------------------|
| `UP`       | `0x01`         | `substate`                 | Boot up a new substate                        |
| `VDOWN`    | `0x02`         | `substate`                 | Spin down a virtual substate                  |
| `VDOWNARG` | `0x03`         | `substate + string`        | Spin down a virtual substate with arguments   |
| `DOWN`     | `0x04`         | `substate_id`              | Spin down a substate                          |
| `LDOWN`    | `0x05`         | `substate_index`           | Spin down a local substate                    |
| `MSG`      | `0x06`         | `bytes`                    | Record a message                              |
| `SIG`      | `0x07`         | `signature`                | Provide a signature for prior instructions    |
| `DOWNALL`  | `0x08`         | `class_id`                 | Spin down all substates of the given class    |
| `SYSCALL`  | `0x09`         | `call_data`                | Make a system call                            |
| `HEADER`   | `0x0A`         | `version + flags`          | Specify headers                               |
| `END`      | `0x00`         | None                       | Mark the end of an action                     |

### `HEADER`

A `HEADER` instruction specify metadata info about the transaction. The operand includes two parts:

```
+--------------+--------------+
| version (u8) | flags (u8)   |
+--------------+--------------+
```

Please check [validation rules](./validation.md) for the allowed values.

### `SYSCALL`

A `SYSCALL` instruction allows a system invocation, e.g. to pay transaction fee. The operand `call_data` is a `bytes`.

### `UP`

An `UP` instruction instructs Radix Engine to create a new substate in the global state.  The operand is the serialization of a substate.

### `VDOWN`

A `VDOWN` instruction spins down a virtual substate (which is not stored in global state).

### `VDOWNARG`

A `VDOWNARG` instruction spins down a virtual substate with additional argument. 

One common use case is to create a new token by spinning down a `RE_ADDR` and booting up a `TOKEN_DEFINITION`, where the argument is the token symbol.

### `DOWN`

A `DOWN` instruction spins down a physical substate. The operand is substate id.

### `LDOWN`

A `LDOWN` instruction spins down a local substate created within this transaction. The operand is an index (`u32`).

### `DOWNALL`

A `DOWNALL` instruction spins down all substates of a particular type. The operand is a class ID (`u8`).

### `MSG`

A `MSG` instruction is used to record a message. The operand is a `bytes`.

### `SIG`

A `SIG` instruction provides a signature for authenticating all instructions before this instruction. The operand is a `signature`.

### `END`

An `END` instruction marks the end of an instruction group. Each transactions are logically divided into different groups.

## Error Handling

Any unrecognized entity in the raw transaction should result in a parsing exception.

Most of the data structures are of variable length. In case the required data is more than the remaining unparsed transaction bytes, a.k.a. underflow, a parsing exception should also be raised.

Also, implementation should take care of memory safety while parsing transactions.

## Additional Notes

While this specification use unsigned integers, the [Java node implementation](https://github.com/radixdlt/radixdlt) may choose to use signed integers instead for performance reason, if the value is within range.

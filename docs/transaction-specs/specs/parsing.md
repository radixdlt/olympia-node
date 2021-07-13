# Transaction

A transaction is the serialization of actions that are cryptographically signed and broadcast to the Radix Network. Transactions trigger ledger state transitions, considering blockchain as a [replicated state machine](https://en.wikipedia.org/wiki/State_machine_replication). 

In this documentation, we describe the structure of Radix transaction. If you'd like to understand how transactions are validated, please switch to [the validation doc](./validation.md).

## Basics

This section explains how values of primitive types are read and written.

Unless otherwise specified, the order of bytes is always [big endian](https://en.wikipedia.org/wiki/Endianness). 

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

### Size

Sizes are encoded as `u16`.

### Bytes

`bytes` is a byte array of variable length and is encoded as:
```
+-------------+-----------+
| size        | raw_bytes |
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
- `0x03 + lower_26_bytes(hash(public_key + symbol))` - A resource address
- `0x04 + public_key` - An account address

At api/user level, addresses are wrapped into user-friendly identifiers:

| **Type**  | **Mainnet**                       | **Testnet**                       | **Devnet**                        |
|-----------|-----------------------------------|-----------------------------------|-----------------------------------|
| Account   | `bech32("rdx", address)`          | `bech32("tdx", address)`          | `bech32("ddx", address)`          |
| Validator | `bech32("rv", public_key)`        | `bech32("tv", public_key)`        | `bech32("dv", public_key)`        |
| Resource  | `bech32(symbol + "_rr", address)` | `bech32(symbol + "_tr", address)` | `bech32(symbol + "_dr", address)` |

Bech32 encoding is specified [here](https://en.bitcoin.it/wiki/Bech32).

### Optional

Optional fields are encoded as:
- `0x00` for `None`
- `0x01 + <value>` for `Some`

## Substate

A substate is an object that is stored in the ledger state, which can be viewed as a map of substates indexed by substate ID.

### Substate ID

For physical substate, the ID is the concatenation of transaction ID and substate index (where the substate is defined).

```
+--------------+--------------+
| tx_id (hash) | index (u32)  |
+--------------+--------------+
```

Note that substate index is the number of the `UP` instruction in the transaction (e.g., if `index == 1`, it means the substate is created by the 2nd `UP` instruction)

For virtual substate, the ID is the hash of the serialization.

### Substate Type

Each substate is of a particular type and, when serialized, the first byte encodes the type info.

Currently, we have the following types:

| **Substate Type**                 | **Code** | **Description**                                                |
|-----------------------------------|----------|----------------------------------------------------------------|
| `VIRTUAL_PARENT`                  | `0x00`   | Virtual parent                                                 |
| `UNCLAIMED_READDR`                | `0x01`   | Unclaimed Radix Engine address                                 |
| `ROUND_DATA`                      | `0x02`   | BFT consensus round data                                       |
| `EPOCH_DATA`                      | `0x03`   | BFT consensus epoch data                                       |
| `TOKEN_RESOURCE`                  | `0x04`   | Token resource                                                 |
| `TOKEN_RESOURCE_METADATA`         | `0x05`   | Token resource metadata                                        |
| `TOKENS`                          | `0x06`   | Tokens                                                         |
| `PREPARED_STAKE`                  | `0x07`   | Prepared stake, will transition into stake ownership by system |
| `STAKE_OWNERSHIP`                 | `0x08`   | Stake ownership                                                |
| `PREPARED_UNSTAKE`                | `0x09`   | Prepared unstake                                               |
| `EXITTING_STAKE`                  | `0x0A`   | Existing stake                                                 |
| `VALIDATOR_META_DATA`             | `0x0B`   | Validator metadata, e.g. name and url                          |
| `VALIDATOR_STAKE_DATA`            | `0x0C`   | Validator stake data                                           |
| `VALIDATOR_BFT_DATA`              | `0x0D`   | Validator BFT data                                             |
| `VALIDATOR_ALLOW_DELEGATION_FLAG` | `0x0E`   | Validator allow delegation from others flag                    |
| `VALIDATOR_REGISTERED_FLAG_COPY`  | `0x0F`   | Validator registered flag copy                                 |
| `VALIDATOR_RAKE_COPY`             | `0x10`   | Validator rake (fee) copy                                      |
| `VALIDATOR_OWNER_COPY`            | `0x11`   | Validator owner copy                                           |

### Substate Schema

Substates are serialized and deserialized based on the following protocol:
- The first byte indicates the substate type
- The following bytes are the fields based on the order they appear on the tables below.

#### `VIRTUAL_PARENT`

| **Name**   | **Type** | **Description**      |
|------------|----------|----------------------|
| `reserved` | `u8`     | Reserved, always `0` |
| `data`     | `bytes`  | Data                 |

#### `UNCLAIMED_READDR`

| **Name**   | **Type**  | **Description**        |
|------------|-----------|------------------------|
| `reserved` | `u8`      | Reserved, always `0`   |
| `address`  | `address` | A Radix Engine address |

#### `ROUND_DATA`

| **Name**    | **Type** | **Description**      |
|-------------|----------|----------------------|
| `reserved`  | `u8`     | Reserved, always `0` |
| `view`      | `u64`    | The new view         |
| `timestamp` | `u64`    | The new timestamp    |

#### `EPOCH_DATA`

| **Name**   | **Type** | **Description**      |
|------------|----------|----------------------|
| `reserved` | `u8`     | Reserved, always `0` |
| `epoch`    | `u64`    | The new epoch        |

#### `TOKEN_RESOURCE`

| **Name**      | **Type**          | **Description**                              |
|---------------|-------------------|----------------------------------------------|
| `type`        | `u8`              | Reserved, always `0`                         |
| `resource`    | `address`         | The resource address                         |
| `granularity` | `u256`            | The token granularity, must be `1` as of now |
| `is_mutable`  | `opt<boolean>`    | (Optional) Whether it's mutable              |
| `miner`       | `opt<public_key>` | (Optional) The token owner public key        |

#### `TOKEN_RESOURCE_METADATA`

| **Name**      | **Type**  | **Description**       |
|---------------|-----------|-----------------------|
| `reserved`    | `u8`      | Reserved, always `0`  |
| `resource`    | `address` | The resource address  |
| `name`        | `string`  | The token name        |
| `description` | `string`  | The token description |
| `url`         | `string`  | An URL                |
| `icon_url`    | `string`  | An URL to an icon     |

#### `TOKENS`

| **Name**   | **Type**  | **Description**      |
|------------|-----------|----------------------|
| `reserved` | `u8`      | Reserved, always `0` |
| `owner`    | `address` | The owner address    |
| `resource` | `address` | The resource address |
| `amount`   | `u256`    | The amount           |

#### `PREPARED_STAKE`

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `validator` | `public_key` | The validator public key |
| `owner`     | `address`    | The owner address        |
| `amount`    | `u256`       | The stake amount         |

#### `STAKE_OWNERSHIP`

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `validator` | `public_key` | The validator public key |
| `owner`     | `address`    | The stake owner          |
| `amount`    | `u256`       | The stake amount         |

#### `PREPARED_UNSTAKE`

| **Name**    | **Type**     | **Description**          |
|-------------|--------------|--------------------------|
| `reserved`  | `u8`         | Reserved, always `0`     |
| `validator` | `public_key` | The validator public key |
| `owner`     | `u256`       | The owner                |
| `amount`    | `u256`       | The stake amount         |

#### `EXITING_STAKE`

| **Name**         | **Type**     | **Description**          |
|------------------|--------------|--------------------------|
| `reserved`       | `u8`         | Reserved, always `0`     |
| `epoch_unlocked` | `u64`        | The unlocking epoch      |
| `validator`      | `public_key` | The validator public key |
| `owner`          | `u256`       | The owner                |
| `amount`         | `u256`       | The stake amount         |

#### `VALIDATOR_META_DATA`

| **Name**    | **Type**     | **Description**                 |
|-------------|--------------|---------------------------------|
| `reserved`  | `u8`         | Reserved, always `0`            |
| `validator` | `public_key` | The validator public key        |
| `name`      | `string`     | The validator name              |
| `url`       | `string`     | A link to the validator website |

#### `VALIDATOR_STAKE_DATA`

| **Name**          | **Type**     | **Description**                      |
|-------------------|--------------|--------------------------------------|
| `reserved`        | `u8`         | Reserved, always `0`                 |
| `is_registered`   | `bool`       | Whether this validator is registered |
| `amount`          | `u256`       | Total stake                          |
| `validator`       | `public_key` | Validator public key                 |
| `ownership`       | `u256`       | Total ownership                      |
| `rake_percentage` | `u32`        | Rake percentage                      |
| `owner`           | `address`    | Validator owner address              |

#### `VALIDATOR_BFT_DATA`

| **Name**              | **Type**     | **Description**              |
|-----------------------|--------------|------------------------------|
| `reserved`            | `u8`         | Reserved, always `0`         |
| `validator`           | `public_key` | Validator public key         |
| `proposals_completed` | `u64`        | Number of proposal completed |
| `proposals_missed`    | `u64`        | Number of proposal missed    |

#### `VALIDATOR_ALLOW_DELEGATION_FLAG`

| **Name**                | **Type**     | **Description**               |
|-------------------------|--------------|-------------------------------|
| `reserved`              | `u8`         | Reserved, always `0`          |
| `validator`             | `public_key` | Validator public key          |
| `is_delegation_allowed` | `bool`       | Whether delegation is allowed |

#### `VALIDATOR_REGISTERED_FLAG_COPY`

| **Name**        | **Type**     | **Description**                             |
|-----------------|--------------|---------------------------------------------|
| `reserved`      | `u8`         | Reserved, always `0`                        |
| `epoch_update`  | `opt<u64>`   | The effective epoch                         |
| `validator`     | `public_key` | Validator public key                        |
| `is_registered` | `bool`       | Whether this validator is registered active |

#### `VALIDATOR_RAKE_COPY`

| **Name**          | **Type**     | **Description**           |
|-------------------|--------------|---------------------------|
| `reserved`        | `u8`         | Reserved, always `0`      |
| `epoch_update`    | `opt<u64>`   | The effective epoch       |
| `validator`       | `public_key` | Validator public key      |
| `rake_percentage` | `u32`        | Validator rake percentage |

#### `VALIDATOR_OWNER_COPY`

| **Name**       | **Type**     | **Description**         |
|----------------|--------------|-------------------------|
| `reserved`     | `u8`         | Reserved, always `0`    |
| `epoch_update` | `opt<u64>`   | The effective epoch     |
| `validator`    | `public_key` | Validator public key    |
| `owner`        | `address`    | Validator owner address |

## Transaction Format

A transaction consists of an array of instructions. The hash output of its serialization is considered as **Transaction ID**.

Each instruction consists of
- One opcode (1 byte)
- One optional operand (of variable length)

The table below summarizes all opcodes.

| **Opcode**  | **Byte Value** | **Operand**                 | **Description**                               |
|-------------|----------------|-----------------------------|-----------------------------------------------|
| `END`       | `0x00`         | None                        | Mark the end of an action                     |
| `SYSCALL`   | `0x01`         | `call_data`                 | Make a system call                            |
| `UP`        | `0x02`         | `substate`                  | Boot up a new substate                        |
| `READ`      | `0x03`         | `substate_id`               | Read a remote substate                        |
| `LREAD`     | `0x04`         | `substate_index`            | Read a local substate                         |
| `VREAD`     | `0x05`         | `virtual_substate_id`       | Read a virtual substate                       |
| `LVREAD`    | `0x06`         | `local_virtual_substate_id` | Read a local virtual substate                 |
| `DOWN`      | `0x07`         | `substate_id`               | Spin down a remote substate                   |
| `LDOWN`     | `0x08`         | `substate_index`            | Spin down a local substate                    |
| `VDOWN`     | `0x09`         | `virtual_substate_id`       | Spin down a virtual substate                  |
| `LVDOWN`    | `0x0A`         | `local_virtual_substate_id` | Spin down a local virtual substate            |
| `SIG`       | `0x0B`         | `signature`                 | Provide a signature for prior instructions    |
| `MSG`       | `0x0C`         | `msg`                       | Record a message                              |
| `HEADER`    | `0x0D`         | `version + flags`           | Specify headers                               |
| `READINDEX` | `0x0E`         | `prefix`                    | Read all substates with the given prefix      |
| `DOWNINDEX` | `0x0F`         | `prefix`                    | Spin down all substates with the given prefix |

### Operand Format


| **Name**                    | **Description**                                                             |
|-----------------------------|-----------------------------------------------------------------------------|
| `call_data`                 | System call data (`bytes`) and the content must match function requirements |
| `substate`                  | Serialized substate                                                         |
| `substate_id`               | Substate ID                                                                 |
| `substate_index`            | Substate index (`u16`)                                                      |
| `virtual_substate_id`       | Virtual substate index (`bytes`)                                            |
| `local_virtual_substate_id` | Local virtual substate index (`bytes`)                                      |
| `substate`                  | Spin down a virtual substate                                                |
| `signature`                 | ECDSA Signature                                                             |
| `msg`                       | Message (`bytes`)                                                           |
| `version`                   | Header version (`u8`)                                                       |
| `flags`                     | Header flags (`u8`)                                                         |
| `prefix`                    | Substate prefix (`bytes`)                                                   |

## Error Handling

Any unrecognized entity in the raw transaction should result in a parsing exception.

Most of the data structures are of variable length. In case the required data is more than the remaining unparsed transaction bytes, a.k.a. underflow, a parsing exception should also be raised.

While Radix transaction is modelled on bytebuffer, it's client's responsibility to ensure memory safety during the parsing phase.

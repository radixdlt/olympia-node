# API Error Encoding Scheme

The JSON RPC 2.0 specification, reserves error codes in the range between 
`-32000` and `-32700` for protocol-specific errors (see [specification](https://www.jsonrpc.org/specification#error_object) for more details). 
Rest of the error code space is available for application defined errors.

API uses following scheme to build error codes:

`[sign][category][error id]`

 `[sign]` always `-` (consistent with JSON RPC errors)

 `[category]` consists of 1 or 2 digits, range 0 - 99
 
 `[error id]` consists of 3 digits, range 0 - 999 

## Error Categories

| Code   | Type | Description |
|---|---|---|
| 1 | GENERAL | All internal errors belong to this category
| 32 | PROTOCOL | Category reserved for error codes defined in JSON RPC specification

## Error ID

Each error receives own ID which, being combined with group and category produces unique error code.
The error ID `000` is used to represent errors which did not yet have dedicated ID.

## Semi-automated ID assignment

In order to simplify error management, for each category created a dedicated
enums ([ProtocolErrors](../../radixdlt-java-common/src/main/java/com/radixdlt/errors/ProtocolErrors.java), [RadixErrors](../../radixdlt-java-common/src/main/java/com/radixdlt/errors/RadixErrors.java)), 
which implements the `Failure` interface. 

__In order to achieve automated ID assignment, it is enough to add new errors _after_ existing ones.__ 
This requirement is described in the relevant source files.


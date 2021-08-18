# API Error Encoding Scheme

The JSON RPC 2.0 specification, reserves error codes in the range between 
`-32000` and `-32700` for protocol-specific errors (see [specification](https://www.jsonrpc.org/specification#error_object) for more details). 
Rest of the error code space is available for application defined errors.

In order to provide more focused error information, API uses following scheme to
build error codes:

`[sign][category][error id]`

 `[sign]` always `-` (consistent with JSON RPC errors)

 `[category]` consists of 1 or 2 digits, range 0 - 99
 
 `[error id]` consists of 3 digits, range 0 - 999 

## Error Categories

| Code   | Type | Description |
|---|---|---|
| 1 | CONVERSION | Errors related to data conversion, such as transformation/serialization/deserialization/formatting/etc.
| 2 | PARAMETER | Input parameters errors encountered during parsing/validation 
| 3 | INTERNAL_STATE | Errors caused by incompatibility with current internal state: message expired, operation interrupted, peer banned, etc.
| 4 | EXTERNAL_STATE | Errors caused by incompatibility with current external state: not enough funds, not enough funds for fee
| 5 | PROCESSING | Errors met during processing, for example, while dispatching events
| 6&#8209;31 | reserved | These codes are reserved
| 32 | PROTOCOL | Category reserved for error codes defined in JSON RPC specification
| 33&#8209;98 | reserved | These codes are reserved
| 99 | OTHER | Errors which don't fall into other categories

## Error ID

Each error receives own ID which, being combined with group and category produces unique error code.
The error ID `000` is used to represent errors which did not yet have dedicated ID.

## Few Examples of Error Code Encoding

| Error (internal) | Code | [Category, ID] | Description |
|---|---|---|---|
| INVALID_VALIDATOR_ADDRESS | -2001| [PARAMETER,&nbsp;1] | Error could be encountered during parsing of the validator address passes as a parameter 
| VALUE_OUT_OF_RANGE | -2005| [PARAMETER,&nbsp;5] | Error could be encountered during validation of some parameters 
| UNABLE_TO_RESTORE_CREATOR | -1003 | [CONVERSION,&nbsp;3] | Error encountered during attempt to restore public key of creator from transaction 
| UNKNOWN_TX_ID | -3010 | [INTERNAL_STATE,&nbsp;10] | Attempt to retrieve non-existent transaction

## Semi-automated ID assignment

In order to simplify error management, for each category should be created a dedicated
enum, which implements the `Failure` interface. In order to achieve automated ID assignment, it is enough to add new errors after existing ones. 
This requirement should be explicitly described in the relevant source files and enforced during code reviews.

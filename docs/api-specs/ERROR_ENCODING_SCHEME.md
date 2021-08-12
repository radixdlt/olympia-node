# API Error Encoding Scheme

The JSON RPC 2.0 specification, reserves error codes in the range between 
`-32000` and `-32700` for protocol-specific errors (see [specification](https://www.jsonrpc.org/specification#error_object) for more details). 
Rest of the error code space is available for application defined errors.

In order to provide more focused error information, API uses following scheme to
build error codes:

`[group][category][error id]`

`[group]` consists of single digit, range 0-9
`[category]` consists of single digit, range 0-9
`[error id]` consists of 3 digits, range 0 - 999 

## Error Groups

| Code   | Type | Description |
|---|---|---|
| 1 | NETWORK | Errors related to network communication
| 2 | INPUT | Errors encountered during input (incoming parameters) parsing or validation
| 3 | OUTPUT | Errors encountered while processing the request. For example, if request refers transaction which is missing/non-existent
| 4 | INTERNAL | Internal processing errors - serialization/deserialization errors, disk I/O or DB errors, etc.
| 5 | ENGINE | Radix Engine processing errors
| 6-8 | reserved | These codes are reserved
| 9 | OTHER | Errors which do not fall into any category above

## Error Categories

| Code   | Type | Description |
|---|---|---|
| 1 | DATA | Errors related to data conversion, such as transformation/serialization/deserialization/formatting/etc.
| 2 | PARAMETER | Input parameters errors encountered during parsing/validation 
| 3 | INTERNAL_STATE | Errors caused by incompatibility with current internal state: message expired, operation interrupted, peer banned, etc.
| 4 | EXTERNAL_STATE | Errors caused by incompatibility with current external state: not enough funds, not enough funds for fee
| 5 | PROCESSING | Processing errors
| 6-8 | reserved | These codes are reserved
| 9 | OTHER | Errors which don't fall into other categories

## Error ID

Each error receives own ID which, being combined with group and category produces unique error code.
The error ID `000` is used to represent errors which did not yet have dedicated ID.

## Few Examples of Error Code Encoding

| Error (internal) | Code | [Group, Category, ID] | Description |
|---|---|---|---|
| INVALID_VALIDATOR_ADDRESS | 22001| [INPUT,&nbsp;PARAMETER,&nbsp;1] | Error could be encountered during parsing of the validator address passes as a parameter 
| VALUE_OUT_OF_RANGE | 22005| [INPUT,&nbsp;PARAMETER,&nbsp;5] | Error could be encountered during validation of some parameters 
| UNABLE_TO_RESTORE_CREATOR | 41003 | [INTERNAL,&nbsp;DATA,&nbsp;3] | Error encountered during attempt to restore public key of creator from transaction 
| UNKNOWN_TX_ID | 43010 | [INTERNAL,&nbsp;INTERNAL_STATE,&nbsp;10] | Attempt to retrieve non-existent transaction

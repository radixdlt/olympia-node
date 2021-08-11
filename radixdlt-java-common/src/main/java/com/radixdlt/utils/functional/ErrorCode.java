/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.utils.functional;

public final class ErrorCode {
	private ErrorCode() { }

	public int of(Group group, Category category, int identifier) {
		return group.code() + category.code() + identifier;
	}

	public enum Group {
		NETWORK(1000),		// Network related errors
		INPUT(2000),		// Input parameter parsing/validation errors
		OUTPUT(3000),		// Request processing errors (for example, missing Tx)
		INTERNAL(4000),	// Internal processing errors (potential data corruption, deserialization errors, etc.)
		;

		private final int code;

		Group(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}

	public enum Category {
		GENERAL(100),			// Generic error for cases, when there is no specific subcategory
		PARAMETER(200), 		// Input parameter errors
		ACTION(300),			// Action processing errors
		INTERNAL_STATE(400), 	// Errors caused by incompatibility with current internal state: message expired, operation interrupted, peer banned, etc.
		EXTERNAL_STATE(500), 	// Errors caused by incompatibility with current external state: not enough funds, not enough funds for fee
		;
		private final int code;

		Category(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}
	}

//
//
//
//
//	ADDRESS_IS_MISSING(1303, "Address is missing"),
//	AID_IS_NULL(1601, "AID string is 'null'"),
//	ALREADY_A_VALIDATOR(1306, "Already a validator"),
//	ALREADY_UNREGISTERED(1311, "Already unregistered"),
//	ASYNC_PROCESSING_ERROR(2525, "Async processing error {0}"),
//	BASE_URL_IS_MANDATORY(1001, "Base URL is mandatory"),
//	CANT_MAKE_RECOVERABLE(1701, "Unable to convert signature to recoverable {0}"),
//	DIFFERENT_SOURCE_ADDRESSES(1313, "Source addresses for actions must be identical"),
//	EMPTY_TRANSACTIONS_NOT_SUPPORTED(1321, "Empty transactions are not supported");
//	INSUFFICIENT_FUNDS(1301, "Insufficient balance"),									<---- OUTPUT, STATE, 1
//	INSUFFICIENT_FUNDS_FOR_FEE(1312, "Insufficient funds for fee"),						<---- OUTPUT, STATE, 2
//	INVALID_ACCOUNT_ADDRESS(2510, "Invalid account address {0}"),						<---- INPUT, PARAMETER, 1
//	INVALID_ACTION(1314, "Invalid action"),
//	INVALID_ACTION_DATA(2518, "Action data are invalid {0}"),
//	INVALID_ACTION_TYPE(1315, "Invalid action type"),
//	INVALID_ADDRESS(1317, "Invalid address"),
//	INVALID_AMOUNT(1319, "Invalid amount"),
//	INVALID_BLOB(2511, "Invalid blob {0}"),
//	INVALID_HEX_STRING(2502, "The value {0} is not a correct hexadecimal string"),
//	INVALID_LENGTH(1602, "AID string has incorrect length {0}"),
//	INVALID_NETWORK_ID(2507, "Network ID is not an integer"),
//	INVALID_PAGE_SIZE(2505, "Size {0} must be greater than zero"),
//	INVALID_PUBLIC_KEY(2513, "Invalid public key {0}"),
//	INVALID_RADIX_ADDRESS(1702, "Invalid RadixAddress {0}"),
//	INVALID_RRI(1316, "Invalid RRI"),
//	INVALID_SIGNATURE_DER(2512, "Invalid signature DER {0}"),
//	INVALID_STATE(1307, "Invalid state"),
//	INVALID_TX_ID(2514, "Invalid TX ID {0}"),
//	INVALID_UINT_VALUE(1703, "Invalid UInt256/UInt384 value {0}"),
//	INVALID_VALIDATOR_ADDRESS(1318, "Invalid validator address"),
//	INVALID_VALIDATOR_ADDRESS(2509, "Invalid validator address {0}"),
//	IO_ERROR(2, "IO Error"),
//	MALFORMED_TRANSACTION(1300, "Transaction request is malformed"),
//	MESSAGE_EXPIRED(1, "Message expired"),
//	MISSING_FIELD(2519, "Field {0} is missing or invalid"),
//	MISSING_PARAMETER(2503, "The parameter {0} is missing"),
//	MISSING_PARAMS(2506, "The 'params' field must be present"),
//	NETWORK_IO_ERROR(1004, "Network I/O error {0}"),
//	NEXT_VIEW_LE_CURRENT(1310, "Next view is less or equal to current"),
//	NO_SYSTEM_PARTICLE(1308, "No system particle"),
//	NOT_A_SYSTEM(1304, "Not a system"),
//	NOT_ENOUGH_STAKED(1309, "Not enough stacked"),
//	NOT_PERMITTED(1302, "Not permitted"),
//	OPERATION_INTERRUPTED(1003, "Operation was interrupted {0}"),
//	PEER_BANNED(4, "Peer is banned");
//	RRI_NOT_AVAILABLE(1305, "RRI is not available"),
//	SELF_CONNECTION_ATTEMPT(3, "Attempt to connect to self"),
//	SSL_ALGORITHM_ERROR(1606, "SSL algorithm error: {0}"),
//	SSL_GENERAL_ERROR(1607, "SSL algorithm error: {0}"),
//	SSL_KEY_ERROR(1605, "SSL Key error: {0}"),
//	SUBMISSION_FAILURE(1500, "Transaction submission failed: {0}"),
//	SYMBOL_DOES_NOT_MATCH(2504, "Symbol {0} does not match"),
//	TRANSACTION_ADDRESS_DOES_NOT_MATCH(1320, "Provided txID does not match provided transaction"),
//	TRANSACTION_PARSING_ERROR(2401, "Unable to parse transaction {0}");
//	UNABLE_TO_DECODE(1603, "Unable to decode: {0}"),
//	UNABLE_TO_DESERIALIZE(1604, "Unable to deserialize: {0}"),
//	UNABLE_TO_PREPARE_TX(2515, "Unable to prepare transaction {0}"),
//	UNABLE_TO_RESTORE_CREATOR(2522, "Unable to restore creator from transaction {0}"),
//	UNKNOWN_ACCOUNT_ADDRESS(2521, "Unknown account address {0}"),
//	UNKNOWN_ACTION(1002, "Unknown action {0}"),
//	UNKNOWN_ACTION(2516, "Unknown action {0}"),
//	UNKNOWN_ADDRESS_TYPE(1710, "Unknown address type {0}");
//	UNKNOWN_ERROR(1005, "Unknown error of type {0} with message {1}");
//	UNKNOWN_RRI(2520, "Unknown RRI {0}"),
//	UNKNOWN_TX_ID(2523, "Transaction with id {0} not found");
//	UNKNOWN_VALIDATOR(2508, "Validator {0} not found"),
//	UNSUPPORTED_ACTION(2517, "Action type {0} is not supported"),
//	VALUE_OUT_OF_RANGE(1608, "Parameter must be between {0} and {1}"),
//
}

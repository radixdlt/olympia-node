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

package com.radixdlt.errors;

import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Failure;

/**
 * Full list of internal error codes.
 * <p>
 * <b>WARNING: New errors should be added to the end of the list to preserve error codes.</b>
 */
public enum RadixErrors implements Failure {
	GENERAL("General error (used for testing only)"),
	UNKNOWN("Unknown error of type {0} with message {1}"),

	ERROR_ASYNC("Async processing error {0}"),
	ERROR_CALL_DATA("CallData invalid access (index: {0} accessSize: {1}) on size {2}"),
	ERROR_INTERRUPTED("Operation interrupted with InterruptedException. Details: {0} {1}"),
	ERROR_IO("I/O Error: {0} {1}"),
	ERROR_NEXT_EPOCH_STAKE_FAILED("Preparing stakes to next epoch failed: {0}"),
	ERROR_SSL_ALGORITHM("SSL algorithm error: {0}"),
	ERROR_SSL_GENERAL("SSL algorithm error: {0}"),
	ERROR_SSL_KEY("SSL Key error: {0}"),

	INVALID_ACTION_DATA("Invalid action data {0}"),
	INVALID_ACCOUNT_ADDRESS("Invalid account address {0}"),
	INVALID_ADDRESS_CLAIM("Address claim exceeds 32 bytes."),
	INVALID_ADDRESS_TYPE("Expected address to be " + REAddr.REAddrType.HASHED_KEY + " but was {0}"),
	INVALID_AID_LENGTH("AID string has incorrect length {0}"),
	INVALID_AID_STRING("AID string is 'null'"),
	INVALID_BURN_AMOUNT("Must burn > 0 tokens"),
	INVALID_CALL_TYPE("Invalid call type {0}"),
	INVALID_CONNECT_TO_SELF_ATTEMPT("Attempt to connect to self"),
	INVALID_DATA("Invalid data: {0}"),
	INVALID_DATA_FOR_VALIDATOR_UPDATE("Invalid data for validator bft data update"),
	INVALID_EPOCH("Expected epoch to be {0} but is {1}"),
	INVALID_KEY_FOR_VALIDATOR_UPDATE("Invalid key for validator bft data update"),
	INVALID_MESSAGE_EXPIRED("Message expired"),
	INVALID_MINIMUM_STAKE("Minimum to stake is {0} but trying to stake {1}"),
	INVALID_MINIMUM_UNSTAKE("Must unstake > 0 tokens"),
	INVALID_MINT_AMOUNT("Must mint > 0 tokens"),
	INVALID_NEXT_STATE("Expecting next state to be {0} but was {1}"),
	INVALID_NEXT_VIEW("Next view: {0} is not higher than current view: {1}"),
	INVALID_PAGE_SIZE("Size {0} must be > 0"),
	INVALID_PREFIX("Invalid shutdownAll prefix, expected {0}, received {1}"),
	INVALID_PREFIX_LEN("Invalid shutdownAll prefix length, expected {0}, received {1}"),
	INVALID_PUBLIC_KEY("Invalid public key {0}"),
	INVALID_RAKE_INCREASE("Max rake increase is {0} but trying to increase {1}"),
	INVALID_RESOURCE("Expected resource {0} but was {1}"),
	INVALID_RESOURCE_ADDRESS("Invalid resource address {0}"),
	INVALID_SHUTDOWN_OF_EXITTING_STAKE("Invalid shutdown of exitting stake update epoch expected {0} but was {1}"),
	INVALID_SIGNATURE_DER("Invalid signature DER {0}"),
	INVALID_STATE_ALREADY_INSERTED("Already inserted {0}"),
	INVALID_STATE_BUFFER_HAS_EXTRA_BYTES("Buffer has extra {0} bytes"),
	INVALID_STATE_EPOCH_IS_NOT_FINISHED("Must execute epoch update on end of round {0} but is {1}"),
	INVALID_STATE_INCONSISTENT_DATA("Inconsistent data, there should only be a single substate per validator"),
	INVALID_STATE_VALIDATOR_STARTED_UPDATE("Validator already started to update."),
	INVALID_SUBSTATE("Expected substate {0} but was {1}"),
	INVALID_SUBSTATE_TYPE_ID("Invalid substate type id. Expected {0} but was {1}"),
	INVALID_SYSCALL_PARAMETER("Length must be >= 1 and <= 32 but was {0}"),
	INVALID_TOKEN_GRANULARITY("Granularity must be one."),
	INVALID_TOKEN_SYMBOL("Invalid token symbol {0}"),
	INVALID_TRANSFER_AMOUNT("Invalid transfer amount {0}. The amount must be > 0."),
	INVALID_TX_ID("Invalid TX ID {0}"),
	INVALID_VALIDATOR_ADDRESS("Invalid validator address {0}"),
	INVALID_VALIDATOR_FEE_INCREASE("Max rake increase is {0} but trying to increase {1}"),
	INVALID_VALUE_OUT_OF_RANGE("Parameter {0} must be between {1} and {2}"),
	INVALID_VIEW("Expected view to be {0} but is {1}"),

	MISSING_ACTION_FIELD("Required field {0} is not present in action definition"),
	MISSING_BASE_URL("Base URL is mandatory"),
	MISSING_END_INSTRUCTION("Missing END instruction"),
	MISSING_EPOCH_UPDATE("Must contain epoch update"),
	MISSING_KEY("Missing key"),
	MISSING_PARAMETER("The parameter {0} is missing"),

	MUST_BE_SHARES_FOR_SAME_ACCOUNT("Shares must be for same account"),
	MUST_BE_SHARES_FROM_SAME_DELEGATE("Shares must be from same delegate"),
	MUST_KEEP_STAKE_LOCKED("Stake must still be locked."),
	MUST_MATCH_HASHED_KEY("Hashed key does not match: [{0}]"),
	MUST_MATCH_RAKE_PERCENTAGE("Rake percentage must match."),
	MUST_MATCH_REGISTERED_FLAGS("Registered flags must match."),
	MUST_MATCH_SYMBOL("Symbol {0} does not match"),
	MUST_MATCH_TOKEN_ADDRESS("Token address {0} does not match update token addresses {1}"),
	MUST_MATCH_TOKEN_SYMBOL("Token symbol {0} does not match update token symbol {1}"),
	MUST_MATCH_TX_ID("Provided txID does not match provided transaction"),
	MUST_MATCH_VALIDATOR_ADDRESSES("Validator addresses do not match: {0} != {1}"),
	MUST_NOT_HAVE_EPOCH("Should not have an epoch."),
	MUST_UPDATE_SAME_KEY("Must update same key (validator key != request validator key)"),

	NOT_ALLOWED_DELEGATION("Delegation not allowed by owner."),
	NOT_ALLOWED_MULTIPLE_FEE_RESERVE_DEPOSIT("MultipleFeeReserveDeposit"),
	NOT_ALLOWED_STAKING("Stacking is not allowed: {0}"),
	NOT_AN_ACCOUNT("The address {0} is not an account address"),
	NOT_AUTHORIZED_KEY("Key not authorized: {0}"),
	NOT_A_RESOURCE("The address {0} is not a resource"),
	NOT_ENOUGH_BALANCE("Not enough balance for transfer."),
	NOT_ENOUGH_BALANCE_FOR_FEES("Not enough balance to for fee burn."),
	NOT_ENOUGH_FEES("Not enough fees: unable to construct with fees after {0} tries."),
	NOT_ENOUGH_FEES_PAID("Fee paid {0} is not enough to cover fees {1}"),
	NOT_ENOUGH_RESERVE("Charging {0} but fee reserve only contains {1}"),
	NOT_ENOUGH_RESOURCES("Not enough resources. Requested {0}, available {1}"),
	NO_KEY_PRESENT("No key present."),
	NO_MATCHING_VALIDATOR_KEYS("Prepared stake delegate key does not match validator key"),
	NO_SIGNATURES_LEFT("Used up all signatures allowed"),
	NO_UPDATE_FOR_VALIDATOR_DATA("No update to Validator BFT data"),

	OVERFLOW_AMOUNT("Overflow occurred while calculating amount: {0}"),
	OVERFLOW_VIEW("View overflow"),

	UNABLE_TO_BURN_FIXED_TOKENS("Can only burn mutable tokens."),
	UNABLE_TO_BURN_SHARES("Shares cannot be burnt."),
	UNABLE_TO_CONNECT_BANNED_PEER("Peer is banned"),
	UNABLE_TO_DECREASE_RAKE("Decreasing rake requires epoch delay to {0} but was {1}"),
	UNABLE_TO_DESERIALIZE("Unable to deserialize: {0}"),
	UNABLE_TO_FIND_PARTICLE("Could not find large particle greater than {0}"),
	UNABLE_TO_INCREASE_RAKE("Increasing rake requires epoch delay to {0} but was {1}"),
	UNABLE_TO_MAKE_SIGNATURE_RECOVERABLE("Unable to convert signature to recoverable {0}"),
	UNABLE_TO_PARSE_BOOLEAN("Unable to parse boolean value: {0}"),
	UNABLE_TO_PARSE_FLOAT("Unable to parse float number: {0}"),
	UNABLE_TO_PARSE_HEX_STRING("The value {0} is not a correct hexadecimal string"),
	UNABLE_TO_PARSE_INT("Unable to parse integer number: {0}"),
	UNABLE_TO_PARSE_JSON("Unable to parse JSON: {0}"),
	UNABLE_TO_PARSE_TRANSACTION("Unable to parse transaction: {0}"),
	UNABLE_TO_PARSE_UINT("Unable to parse unsigned integer number: {0}"),
	UNABLE_TO_PREPARE_TX("Unable to prepare transaction {0}"),
	UNABLE_TO_RESTORE_ACCOUNT_ADDRESS("Unable to restore account address {0} from DB key: {1}"),
	UNABLE_TO_RESTORE_CREATOR("Unable to restore creator from transaction {0}"),
	UNABLE_TO_SERIALIZE("Unable to serialize: {0}"),
	UNABLE_TO_SUBMIT_TX("Transaction submission failed: {0}"),

	UNKNOWN_ACTION("Unknown action {0}"),
	UNKNOWN_OPERATION("Unknown {0} instruction {1}"),
	UNKNOWN_RRI("Unknown RRI {0}"),
	UNKNOWN_TOKEN_DEFINITION("Unknown token definition {0}"),
	UNKNOWN_TX_ID("Transaction with id {0} not found"),
	UNKNOWN_VIRTUAL_PARENT("Virtual parent {0} does not exist."),

	UNSUPPORTED_ACTION("Action type {0} is not supported");

	private final String message;

	RadixErrors(String message) {
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return Category.GENERAL.forId(ordinal());
	}
}

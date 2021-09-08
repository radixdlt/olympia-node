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

import com.radixdlt.utils.functional.Failure;

/**
 * Input parameters errors encountered during parsing/validation
 * <p>
 * <b>WARNING:</b> New errors should be added to the end, no insertions or re-arrangements are allowed!
 */
public enum ParameterError implements Failure {
	MISSING_PARAMETER("The parameter {0} is missing"),
	UNKNOWN_ACTION("Unknown action {0}"),
	UNSUPPORTED_ACTION("Action type {0} is not supported"),
	MISSING_ACTION_FIELD("Required field {0} is not present in action definition"),
	INVALID_PAGE_SIZE("Size {0} must be greater than zero"),
	INVALID_SIGNATURE_DER("Invalid signature DER {0}"),
	INVALID_TX_ID("Invalid TX ID {0}"),
	INVALID_VALIDATOR_ADDRESS("Invalid validator address {0}"),
	INVALID_ACCOUNT_ADDRESS("Invalid account address {0}"),
	INVALID_RESOURCE_ADDRESS("Invalid resource address {0}"),
	INVALID_PUBLIC_KEY("Invalid public key {0}"),
	INVALID_SYSCALL_PARAMETER("Length must be >= 1 and <= 32 but was {0}"),
	INVALID_VALIDATOR_FEE_INCREASE("Max rake increase is {0} but trying to increase {1}"),
	INVALID_MINT_AMOUNT("Must mint > 0 tokens"),
	INVALID_BURN_AMOUNT("Must burn > 0 tokens"),
	INVALID_UNSTAKE_AMOUNT("Must unstake > 0 tokens"),
	INVALID_TRANSFER_AMOUNT("Invalid transfer amount {0}. The amount must be > 0."),
	INVALID_MINIMUM_STAKE("Minimum to stake is {0} but trying to stake {1}"),
	INVALID_AID_LENGTH("AID string has incorrect length {0}"),
	VALUE_OUT_OF_RANGE("Parameter {0} must be between {1} and {2}"),
	STAKING_NOT_ALLOWED("Stacking is not allowed: {0}"),
	AID_IS_NULL("AID string is 'null'"),
	BASE_URL_IS_MANDATORY("Base URL is mandatory"),
	HASHED_KEY_DOES_NOT_MATCH("Hashed key does not match: [{0}]"),
	NOT_ENOUGH_RESOURCES("Not enough resources. Requested {0}, available {1}"),
	SHARES_MUST_BE_FROM_SAME_DELEGATE("Shares must be from same delegate"),
	SHARES_MUST_BE_FOR_SAME_ACCOUNT("Shares must be for same account"),
	INVALID_RAKE_INCREASE("Max rake increase is {0} but trying to increase {1}"),
	MUST_CONTAIN_EPOCH_UPDATE("Must contain epoch update"),
	CANT_INCREASE_RAKE("Increasing rake requires epoch delay to {0} but was {1}"),
	CANT_DECREASE_RAKE("Decreasing rake requires epoch delay to {0} but was {1}"),
	INVALID_EPOCH("Expected epoch to be {0} but is {1}"),
	INVALID_VIEW("Expected view to be {0} but is {1}"),
	VIEW_OVERFLOW("View overflow"),
	NO_MATCHING_VALIDATOR_KEYS("Prepared stake delegate key does not match validator key"), //TODO: merge all 3 below?
	MUST_UPDATE_SAME_KEY("Must update same key (validator key != request validator key)"),
	VALIDATOR_ADDRESSES_DO_NOT_MATCH("Validator addresses do not match: {0} != {1}"),
	INVALID_SUBSTATE_TYPE_ID("Invalid substate type id. Expected {0} but was {1}"),
	ADDRESS_CLAIM_TOO_LARGE("Address claim too large."),
	RAKE_MUST_MATCH("Rake percentage must match."),
	REGISTERED_FLAGS_MUST_MATCH("Registered flags must match."),
	SHOULD_NOT_HAVE_EPOCH("Should not have an epoch."),
	INVALID_PREFIX_LEN("Invalid shutdownAll prefix length, expected {0}, received {1}"),
	INVALID_PREFIX("Invalid shutdownAll prefix, expected {0}, received {1}"),
	INVALID_KEY_FOR_VALIDATOR_UPDATE("Invalid key for validator bft data update"),
	INVALID_DATA_FOR_VALIDATOR_UPDATE("Invalid data for validator bft data update"),
	NO_UPDATE_FOR_VALIDATOR_DATA("No update to Validator BFT data"),
	TOKEN_ADDRESS_MISMATCH("Token address {0} does not match update token addresses {1}"),
	TOKEN_SYMBOL_MISMATCH("Token symbol {0} does not match update token symbol {1}"),
	INVALID_TOKEN_SYMBOL("Invalid token symbol {0}"),
	INVALID_TOKEN_GRANULARITY("Granularity must be one.");

	private final String message;

	ParameterError(String message) {
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return Category.PARAMETER.forId(ordinal());
	}
}

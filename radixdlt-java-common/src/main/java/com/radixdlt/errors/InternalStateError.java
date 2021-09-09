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
 * Errors caused by incompatibility with current internal state.
 * For example: message expired, operation interrupted, peer banned, etc.
 * <p>
 * <b>WARNING:</b> New errors should be added to the end, no insertions or re-arrangements are allowed!
 */
public enum InternalStateError implements Failure {
	GENERAL("General error"),
	UNKNOWN_RRI("Unknown RRI {0}"),
	UNKNOWN_TOKEN_DEFINITION("Unknown token definition {0}"),
	UNKNOWN_TX_ID("Transaction with id {0} not found"),
	SSL_KEY_ERROR("SSL Key error: {0}"),
	SSL_ALGORITHM_ERROR("SSL algorithm error: {0}"),
	SSL_GENERAL_ERROR("SSL algorithm error: {0}"),
	NEXT_EPOCH_STAKE_FAILURE("Preparing stakes to next epoch failed: {0}"),
	COULD_NOT_FIND_PARTICLE("Could not find large particle greater than {0}"),
	NEXT_VIEW_IS_NOT_HIGHER_THAN_CURRENT("Next view: {0} is not higher than current view: {1}"),
	DELEGATION_NOT_ALLOWED("Delegation not allowed by owner."),
	INVALID_ADDRESS_TYPE("Expected address to be " + REAddr.REAddrType.HASHED_KEY + " but was {0}"),
	INVALID_RESOURCE("Expected resource {0} but was {1}"),
	INVALID_SUBSTATE("Expected substate {0} but was {1}"),
	INVALID_DATA("Invalid data: {0}"),
	TOKEN_IS_NOT_MUTABLE("Can only burn mutable tokens."),
	SHARES_CANNOT_BE_BURNT("Shares cannot be burnt."),
	MISSING_KEY("Missing key"),
	INVALID_CALL_TYPE("Invalid call type {0}"),
	INVALID_SHUTDOWN_OF_EXITTING_STAKE("Invalid shutdown of exitting stake update epoch expected {0} but was {1}"),
	STAKE_MUST_BE_LOCKED("Stake must still be locked."),
	INVALID_NEXT_STATE("Expecting next state to be {0} but was {1}"),
	ALREADY_INSERTED("Already inserted {0}"),
	INCONSISTENT_DATA("Inconsistent data, there should only be a single substate per validator"),
	EPOCH_IS_NOT_FINISHED("Must execute epoch update on end of round {0} but is {1}"),
	VALIDATOR_STARTED_UPDATE("Validator already started to update."),
	VIRTUAL_PARENT_NOT_EXISTS("Virtual parent {0} does not exist.");

	private final String message;

	InternalStateError(String message) {
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return Category.INTERNAL_STATE.forId(ordinal());
	}
}

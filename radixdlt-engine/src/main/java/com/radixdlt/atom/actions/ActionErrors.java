/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.atom.actions;

import com.radixdlt.utils.functional.Failure;

public enum ActionErrors implements Failure {
	SUBMISSION_FAILURE(1500, "Transaction submission failed: {0}"),
//
//	MALFORMED_TRANSACTION(1300, "Transaction request is malformed"),
//
//	INSUFFICIENT_FUNDS(1301, "Insufficient balance"),
//	NOT_PERMITTED(1302, "Not permitted"),
//  ADDRESS_IS_MISSING(1303, "Address is missing"),
//	NOT_A_SYSTEM(1304, "Not a system"),
//	RRI_NOT_AVAILABLE(1305, "RRI is not available"),
//	ALREADY_A_VALIDATOR(1306, "Already a validator"),
//	INVALID_STATE(1307, "Invalid state"),
//	NO_SYSTEM_PARTICLE(1308, "No system particle"),
//	NOT_ENOUGH_STAKED(1309, "Not enough stacked"),
//	NEXT_VIEW_LE_CURRENT(1310, "Next view is less or equal to current"),
//	ALREADY_UNREGISTERED(1311, "Already unregistered"),
//	INSUFFICIENT_FUNDS_FOR_FEE(1312, "Insufficient funds for fee"),
//	DIFFERENT_SOURCE_ADDRESSES(1313, "Source addresses for actions must be identical"),
//	INVALID_ACTION(1314, "Invalid action"),
//	INVALID_ACTION_TYPE(1315, "Invalid action type"),
//	INVALID_RRI(1316, "Invalid RRI"),
//	INVALID_ADDRESS(1317, "Invalid address"),
//	INVALID_VALIDATOR_ADDRESS(1318, "Invalid validator address"),
//	INVALID_AMOUNT(1319, "Invalid amount"),
	TRANSACTION_ADDRESS_DOES_NOT_MATCH(1320, "Provided txID does not match provided transaction"),
//	EMPTY_TRANSACTIONS_NOT_SUPPORTED(1321, "Empty transactions are not supported"),
	;

	private final int code;
	private final String message;

	ActionErrors(int code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String message() {
		return message;
	}

	@Override
	public int code() {
		return code;
	}
}

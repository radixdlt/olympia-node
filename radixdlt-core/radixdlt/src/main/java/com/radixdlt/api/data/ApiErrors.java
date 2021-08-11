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

package com.radixdlt.api.data;

import com.radixdlt.utils.functional.Failure;

public enum ApiErrors implements Failure {
	INVALID_HEX_STRING(2502, "The value {0} is not a correct hexadecimal string"),
	MISSING_PARAMETER(2503, "The parameter {0} is missing"),
	SYMBOL_DOES_NOT_MATCH(2504, "Symbol {0} does not match"),
	INVALID_PAGE_SIZE(2505, "Size {0} must be greater than zero"),
	MISSING_PARAMS(2506, "The 'params' field must be present"),
//	INVALID_NETWORK_ID(2507, "Network ID is not an integer"),
//	UNKNOWN_VALIDATOR(2508, "Validator {0} not found"),
//	INVALID_BLOB(2511, "Invalid blob {0}"),
	INVALID_SIGNATURE_DER(2512, "Invalid signature DER {0}"),
	INVALID_TX_ID(2514, "Invalid TX ID {0}"),
	UNABLE_TO_PREPARE_TX(2515, "Unable to prepare transaction {0}"),
	UNKNOWN_ACTION(2516, "Unknown action {0}"),
	UNSUPPORTED_ACTION(2517, "Action type {0} is not supported"),
	INVALID_ACTION_DATA(2518, "Action data are invalid {0}"),
	MISSING_FIELD(2519, "Field {0} is missing or invalid"),
	UNKNOWN_RRI(2520, "Unknown RRI {0}"),
	UNKNOWN_ACCOUNT_ADDRESS(2521, "Unknown account address {0}"),
	UNABLE_TO_RESTORE_CREATOR(2522, "Unable to restore creator from transaction {0}"),
	UNKNOWN_TX_ID(2523, "Transaction with id {0} not found"),
;
	private final int code;
	private final String message;

	ApiErrors(int code, String message) {
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

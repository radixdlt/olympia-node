/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.archive.validator;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.data.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.api.util.JsonRpcUtil.fromCollection;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.util.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.util.JsonRpcUtil.safeString;
import static com.radixdlt.api.util.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.util.JsonRpcUtil.withRequiredStringParameter;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.ok;

public final class ArchiveValidationHandler {
	private final BerkeleyValidatorStore validatorStore;
	private final BerkeleyValidatorUptimeArchiveStore uptimeStore;
	private final ValidatorArchiveInfoService validatorInfoService;
	private final Addressing addressing;

	@Inject
	public ArchiveValidationHandler(
		BerkeleyValidatorStore validatorStore,
		BerkeleyValidatorUptimeArchiveStore uptimeStore,
		ValidatorArchiveInfoService validatorInfoService,
		Addressing addressing
	) {
		this.validatorStore = validatorStore;
		this.uptimeStore = uptimeStore;
		this.validatorInfoService = validatorInfoService;
		this.addressing = addressing;
	}

	public JSONObject handleValidatorsGetNextEpochSet(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("size"),
			List.of("cursor"),
			params -> allOf(parseSize(params), ok(parseAddressCursor(params)))
				.flatMap((size, cursor) ->
							 validatorInfoService.getValidators(size, cursor)
								 .map(this::formatValidatorResponse))
		);
	}

	public JSONObject handleValidatorsLookupValidator(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"validatorAddress",
			address -> addressing.forValidators().fromString(address)
				.map(key -> {
					var json = validatorStore.getValidatorInfo(key);
					var uptime = uptimeStore.getUptimeTwoWeeks(key);
					json
						.put("proposalsCompleted", uptime.getProposalsCompleted())
						.put("proposalsMissed", uptime.getProposalsMissed())
						.put("uptimePercentage", uptime.toPercentageString());
					return json;
				})
		);
	}

	//-----------------------------------------------------------------------------------------------------
	// internal processing
	//-----------------------------------------------------------------------------------------------------

	private JSONObject formatValidatorResponse(Optional<ECPublicKey> cursor, List<ValidatorInfoDetails> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(addressing.forValidators()::of).orElse(""))
			.put("validators", fromCollection(transactions, d -> d.asJson(addressing)));
	}

	private Optional<ECPublicKey> parseAddressCursor(JSONObject params) {
		return safeString(params, "cursor")
			.toOptional()
			.flatMap(this::parsePublicKey);
	}

	private Optional<ECPublicKey> parsePublicKey(String address) {
		return addressing.forValidators().fromString(address).toOptional();
	}

	private static Result<Integer> parseSize(JSONObject params) {
		return safeInteger(params, "size")
			.filter(value -> value > 0, INVALID_PAGE_SIZE);
	}
}

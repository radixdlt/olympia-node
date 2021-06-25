/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.handler;

import com.radixdlt.identifiers.AccountAddresses;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.ValidatorInfoService;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.ValidatorAddresses;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;
import static com.radixdlt.api.data.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.ok;

@Singleton
public class ArchiveValidationHandler {
	private final ValidatorInfoService validatorInfoService;
	private final AccountAddresses accountAddresses;
	private final ValidatorAddresses validatorAddresses;

	@Inject
	public ArchiveValidationHandler(
		ValidatorInfoService validatorInfoService,
		AccountAddresses accountAddresses,
		ValidatorAddresses validatorAddresses
	) {
		this.validatorInfoService = validatorInfoService;
		this.accountAddresses = accountAddresses;
		this.validatorAddresses = validatorAddresses;
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
			address -> validatorAddresses.fromString(address)
				.flatMap(validatorInfoService::getValidator)
				.map(d -> d.asJson(accountAddresses, validatorAddresses))
		);
	}

	//-----------------------------------------------------------------------------------------------------
	// internal processing
	//-----------------------------------------------------------------------------------------------------

	private JSONObject formatValidatorResponse(Optional<ECPublicKey> cursor, List<ValidatorInfoDetails> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(validatorAddresses::of).orElse(""))
			.put("validators", fromList(transactions, d -> d.asJson(accountAddresses, validatorAddresses)));
	}

	private Optional<ECPublicKey> parseAddressCursor(JSONObject params) {
		return safeString(params, "cursor")
			.toOptional()
			.flatMap(this::parsePublicKey);
	}

	private Optional<ECPublicKey> parsePublicKey(String address) {
		return validatorAddresses.fromString(address).toOptional();
	}

	private static Result<Integer> parseSize(JSONObject params) {
		return safeInteger(params, "size")
			.filter(value -> value > 0, INVALID_PAGE_SIZE);
	}
}

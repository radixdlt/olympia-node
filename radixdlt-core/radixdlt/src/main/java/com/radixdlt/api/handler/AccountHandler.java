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

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.api.service.AccountService;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.optString;
import static com.radixdlt.api.JsonRpcUtil.response;
import static com.radixdlt.api.JsonRpcUtil.safeArray;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.utils.functional.Result.allOf;

@Singleton
public class AccountHandler {
	private final AccountService accountService;
	private final SubmissionService submissionService;
	private final ActionParserService actionParserService;
	private final HashSigner hashSigner;

	@Inject
	public AccountHandler(
		AccountService accountService,
		SubmissionService submissionService,
		ActionParserService actionParserService,
		@Named("RadixEngine") HashSigner hashSigner
	) {
		this.accountService = accountService;
		this.submissionService = submissionService;
		this.actionParserService = actionParserService;
		this.hashSigner = hashSigner;
	}

	public JSONObject handleAccountGetInfo(JSONObject request) {
		return response(request, accountService.getAccountInfo());
	}

	public JSONObject handleAccountSubmitTransactionSingleStep(JSONObject request) {
		return withRequiredParameters(request, List.of("actions"), List.of("message"), this::respondWithTransactionId);
	}

	private Result<JSONObject> respondWithTransactionId(JSONObject params) {
		return allOf(
			safeArray(params, "actions"),
			Result.ok(optString(params, "message")),
			Result.ok(params.optBoolean("disableResourceAllocationAndDestroy"))
		)
			.flatMap(this::parseSignSubmit)
			.map(AccountHandler::formatTxId);
	}

	private Result<AID> parseSignSubmit(
		JSONArray actions, Optional<String> message, boolean disableResourceAllocationAndDestroy
	) {
		return actionParserService.parse(actions)
			.flatMap(steps -> submissionService.oneStepSubmit(
				steps, message, hashSigner, disableResourceAllocationAndDestroy
			));
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}
}

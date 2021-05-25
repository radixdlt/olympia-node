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
import com.google.inject.name.Named;
import com.radixdlt.api.Rri;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.application.Balances;
import com.radixdlt.application.StakedBalance;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.optString;
import static com.radixdlt.api.JsonRpcUtil.safeArray;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.utils.functional.Result.allOf;

public class AccountHandler {
	private final SubmissionService submissionService;
	private final ActionParserService actionParserService;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final HashSigner hashSigner;
	private final ECPublicKey bftKey;
	private final ClientApiStore clientApiStore;

	@Inject
	public AccountHandler(
		SubmissionService submissionService,
		ActionParserService actionParserService,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@Named("RadixEngine") HashSigner hashSigner,
		@Self ECPublicKey bftKey,
		ClientApiStore clientApiStore
	) {
		this.submissionService = submissionService;
		this.actionParserService = actionParserService;
		this.radixEngine = radixEngine;
		this.hashSigner = hashSigner;
		this.bftKey = bftKey;
		this.clientApiStore = clientApiStore;
	}

	public JSONObject handleAccountGetInfo(JSONObject request) {
		return withRequiredParameters(request, List.of(), List.of(), params -> Result.ok(getAccountInfo()));
	}

	public JSONObject handleAccountSubmitTransactionSingleStep(JSONObject request) {
		return withRequiredParameters(request, List.of("actions"), List.of("message"), this::respondWithTransactionId);
	}

	private Result<JSONObject> respondWithTransactionId(JSONObject params) {
		return allOf(safeArray(params, "actions"), Result.ok(optString(params, "message")))
			.flatMap(this::parseSignSubmit)
			.map(AccountHandler::formatTxId);
	}

	private Result<AID> parseSignSubmit(JSONArray actions, Optional<String> message) {
		return actionParserService.parse(actions)
			.flatMap(steps -> submissionService.oneStepSubmit(steps, message, hashSigner));
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}

	private JSONObject getAccountInfo() {
		return jsonObject()
			.put("address", AccountAddress.of(REAddr.ofPubKeyAccount(bftKey)))
			.put("balance", getBalance());
	}

	private JSONObject getBalance() {
		var balances = radixEngine.getComputedState(Balances.class);
		var stakedBalance = radixEngine.getComputedState(StakedBalance.class);

		var stakesArray = jsonArray();
		stakedBalance.forEach((publicKey, amount) -> stakesArray.put(constructStakeEntry(publicKey, amount)));

		var balancesArray = jsonArray();
		balances.forEach((rri, amount) -> balancesArray.put(constructBalanceEntry(rri, amount)));

		return jsonObject()
			.put("tokens", balancesArray)
			.put("stakes", stakesArray);
	}

	private JSONObject constructBalanceEntry(REAddr rri, UInt384 amount) {
		return clientApiStore.getTokenDefinition(rri)
			.fold(
				__ -> jsonObject().put("rri", "<unknown>").put("amount", amount),
				definition -> jsonObject().put("rri", Rri.of(definition.getSymbol(), rri)).put("amount", amount)
			);
	}

	private JSONObject constructStakeEntry(ECPublicKey publicKey, UInt256 amount) {
		return jsonObject().put("delegate", ValidatorAddress.of(publicKey)).put("amount", amount);
	}
}

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

import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.FaucetTokensTransfer;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.networks.Addressing;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;

import static com.radixdlt.atom.actions.ActionErrors.SUBMISSION_FAILURE;

public class FaucetHandler {
	private final REAddr account;
	private final Addressing addressing;
	private final EventDispatcher<NodeApplicationRequest> dispatcher;

	@Inject
	public FaucetHandler(
		@Self REAddr account,
		Addressing addressing,
		EventDispatcher<NodeApplicationRequest> dispatcher
	) {
		this.account = account;
		this.addressing = addressing;
		this.dispatcher = dispatcher;
	}

	public JSONObject requestTokens(JSONObject request) {
		return withRequiredStringParameter(
			request, "address",
			address -> addressing.forAccounts().parseFunctional(address).flatMap(this::sendTokens)
		);
	}

	private Result<JSONObject> sendTokens(REAddr destination) {
		var request = TxnConstructionRequest.create().action(
			new FaucetTokensTransfer(account, destination)
		);
		var completableFuture = new CompletableFuture<MempoolAddSuccess>();
		var accountRequest = NodeApplicationRequest.create(request, completableFuture);
		dispatcher.dispatch(accountRequest);
		try {
			var success = completableFuture.get();
			return Result.ok(FaucetHandler.formatTxId(success.getTxn().getId()));
		} catch (ExecutionException e) {
			return SUBMISSION_FAILURE.with(e.getCause().getMessage()).result();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}
}

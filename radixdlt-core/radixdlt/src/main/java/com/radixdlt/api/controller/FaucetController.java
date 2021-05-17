/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.api.faucet.FaucetToken;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atom.actions.PayFee;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.transaction.TokenFeeChecker;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import com.radixdlt.api.Controller;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.RestUtils.*;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public final class FaucetController implements Controller {
	private static final Logger logger = LogManager.getLogger();
	private static final UInt256 AMOUNT = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private final EventDispatcher<NodeApplicationRequest> faucetRequestDispatcher;
	private final REAddr account;
	private final Set<REAddr> tokensToSend;

	@Inject
	public FaucetController(
		@Self REAddr account,
		@FaucetToken Set<REAddr> tokensToSend,
		final EventDispatcher<NodeApplicationRequest> faucetRequestDispatcher
	) {
		this.account = account;
		this.tokensToSend = tokensToSend;
		this.faucetRequestDispatcher = faucetRequestDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/faucet/request", this::handleFaucetRequest);
	}

	@VisibleForTesting
	void handleFaucetRequest(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBody(exchange, values -> {
			var params = values.getJSONObject("params");
			var addressString = params.getString("address");
			final REAddr address;
			try {
				address = AccountAddress.parse(addressString);
			} catch (DeserializeException e) {
				respond(exchange, jsonObject().put("error", jsonObject().put("message", "Bad address.")));
				return;
			}

			var txnConstructionRequest = TxnConstructionRequest.create();
			txnConstructionRequest.action(new PayFee(account, TokenFeeChecker.FIXED_FEE));
			for (var tokenAddr : tokensToSend) {
				txnConstructionRequest.transfer(tokenAddr, account, address, AMOUNT);
			}
			var completableFuture = new CompletableFuture<MempoolAddSuccess>();
			var request = NodeApplicationRequest.create(txnConstructionRequest, completableFuture);
			faucetRequestDispatcher.dispatch(request);

			try {
				var success = completableFuture.get();
				respond(exchange, jsonObject()
					.put("result", jsonObject()
						.put("transaction", Hex.toHexString(success.getTxn().getPayload()))
						.put("transaction_identifier", success.getTxn().getId().toString())
					)
				);
			} catch (ExecutionException | RuntimeException e) {
				logger.warn("Unable to fulfill faucet request {}", e.getMessage());
				respond(exchange, jsonObject()
					.put("error", jsonObject()
						.put("message", e.getCause().getMessage()))
				);
			}
		});
	}
}

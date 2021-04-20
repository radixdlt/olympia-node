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

package org.radix.api.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.radix.api.http.RestUtils.*;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class FaucetController implements Controller {
	private final EventDispatcher<NodeApplicationRequest> faucetRequestDispatcher;
	private final Rri nativeToken;
	private final UInt256 amount = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);
	private static final UInt256 FEE = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	@Inject
	public FaucetController(
		@NativeToken Rri nativeToken,
		final EventDispatcher<NodeApplicationRequest> faucetRequestDispatcher
	) {
		this.nativeToken = nativeToken;
		this.faucetRequestDispatcher = faucetRequestDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/faucet/request", this::handleFaucetRequest);
	}

	@VisibleForTesting
	void handleFaucetRequest(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBodyAsync(exchange, values -> {
			var params = values.getJSONObject("params");
			var addressString = params.getString("address");
			final RadixAddress address;
			try {
				address = RadixAddress.from(addressString);
			} catch (IllegalArgumentException e) {
				respond(exchange, jsonObject().put("error", jsonObject().put("message", "Bad address.")));
				return;
			}

			var actions = TxActionListBuilder.create()
				.transfer(nativeToken, address, amount)
				.burn(nativeToken, FEE)
				.build();

			var success = new CompletableFuture<AID>();

			var request = NodeApplicationRequest.create(
				actions,
				(txn, aid) -> success.complete(aid),
				(txn, error) -> success.completeExceptionally(new RuntimeException(error))
			);

			faucetRequestDispatcher.dispatch(request);
			try {
				var aid = success.get();
				respond(exchange, jsonObject().put("result", aid.toString()));
			} catch (ExecutionException e) {
				respond(exchange, jsonObject().put("error", jsonObject().put("message", e.getMessage())));
			}
		});
	}
}

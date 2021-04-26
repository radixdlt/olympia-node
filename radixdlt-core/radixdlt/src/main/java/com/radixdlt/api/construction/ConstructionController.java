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

package com.radixdlt.api.construction;

import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import com.radixdlt.api.Controller;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.radixdlt.api.RestUtils.respond;
import static com.radixdlt.api.RestUtils.withBody;
import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public final class ConstructionController implements Controller {
	private final TxnParser txnParser;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

	@Inject
	public ConstructionController(
		TxnParser txnParser,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		this.txnParser = txnParser;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/node/parse", this::handleParse);
		handler.post("/node/submit", this::handleSubmit);
	}

	private JSONObject instructionToObject(REInstruction i) {
		return jsonObject()
			.put("type", i.getMicroOp())
			.put("data", Objects.toString(i.getData()));
	}

	void handleParse(HttpServerExchange exchange) {
		withBody(exchange, values -> {
			var transactionHex = values.getString("transaction");
			var transactionBytes = Hex.decode(transactionHex);
			REParsedTxn parsedTxn;
			try {
				parsedTxn = txnParser.parse(Txn.create(transactionBytes));
			} catch (RadixEngineException e) {
				var statelessParsed = new JSONArray();
				e.getResult().instructionsParsed().forEach(i -> statelessParsed.put(instructionToObject(i)));
				respond(exchange, jsonObject()
					.put("parsed", statelessParsed)
					.put("error", e.getMessage())
				);
				return;
			}

			var ops = jsonArray();
			var response = jsonObject().put("operations", ops);
			parsedTxn.instructions().forEach(i -> {
				var jsonOp = jsonObject()
					.put("type", i.getInstruction().getMicroOp())
					.put("parsed", i.getParticle());

				ops.put(jsonOp);
			});

			respond(exchange, response);
		});
	}

	void handleSubmit(HttpServerExchange exchange) {
		withBody(exchange, values -> {
			var transactionHex = values.getString("transaction");
			var transactionBytes = Hex.decode(transactionHex);
			var txn = Txn.create(transactionBytes);
			var completableFuture = new CompletableFuture<MempoolAddSuccess>();
			var mempoolAdd = MempoolAdd.create(txn, completableFuture);
			this.mempoolAddEventDispatcher.dispatch(mempoolAdd);
			try {
				var success = completableFuture.get();
				respond(exchange, jsonObject()
					.put("result", jsonObject()
						.put("transaction", Hex.toHexString(success.getTxn().getPayload()))
						.put("transaction_identifier", success.getTxn().getId().toString())
					)
				);
			} catch (ExecutionException | RuntimeException e) {
				respond(exchange, jsonObject()
					.put("error", jsonObject()
						.put("message", e.getCause().getMessage()))
				);
			}
		});
	}
}

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
import com.radixdlt.constraintmachine.REParsedTxn;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.radix.api.http.Controller;

import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsync;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class ConstructionController implements Controller {
	private final TxnParser txnParser;

	@Inject
	public ConstructionController(TxnParser txnParser) {
		this.txnParser = txnParser;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/node/parse", this::handleParse);
	}

	void handleParse(HttpServerExchange exchange) {
		withBodyAsync(exchange, values -> {
			var transactionHex = values.getString("transaction");
			var transactionBytes = Hex.decode(transactionHex);
			REParsedTxn parsedTxn = txnParser.parse(Txn.create(transactionBytes));
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

}

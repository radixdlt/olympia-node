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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.identifiers.AID;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.utils.Ints;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.radix.api.http.Controller;

import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsync;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class ConstructionController implements Controller {
	private final AtomIndex atomIndex;

	@Inject
	public ConstructionController(AtomIndex atomIndex) {
		this.atomIndex = atomIndex;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/node/parse", this::handleParse);
	}

	void handleParse(HttpServerExchange exchange) {
		withBodyAsync(exchange, values -> {
			var transactionHex = values.getString("transaction");
			var transactionBytes = Hex.decode(transactionHex);
			var txn = DefaultSerialization.getInstance().fromDson(transactionBytes, Atom.class);
			var ops = jsonArray();
			var response = jsonObject().put("operations", ops);

			for (int i = 0; i < txn.getInstructions().size(); i += 2) {
				var b = txn.getInstructions().get(i);
				var payload = txn.getInstructions().get(i + 1);
				var instruction = REInstruction.create(b[0], payload);
				var op = instruction.getMicroOp();
				var jsonOp = jsonObject()
					.put("type", op.toString())
					.put("data", Hex.toHexString(instruction.getData()));
				if (op == REInstruction.REOp.DOWN) {
					var prevTxnBytes = atomIndex.get(AID.from(instruction.getData(), 0)).orElseThrow();
					var index = Ints.fromByteArray(instruction.getData(), AID.BYTES);
					var prevTxn = DefaultSerialization.getInstance().fromDson(prevTxnBytes.getPayload(), Atom.class);
					var particleBytes = prevTxn.getInstructions().get(index * 2 + 1);
					var particle = DefaultSerialization.getInstance().fromDson(particleBytes, Particle.class);
					jsonOp.put("parsedData", particle.toString());
				} else if (op == REInstruction.REOp.UP) {
					var particle = DefaultSerialization.getInstance().fromDson(instruction.getData(), Particle.class);
					jsonOp.put("parsedData", particle.toString());
				} else if (op == REInstruction.REOp.VDOWN) {
					var particle = DefaultSerialization.getInstance().fromDson(instruction.getData(), Particle.class);
					jsonOp.put("parsedData", particle.toString());
				} else if (op == REInstruction.REOp.LDOWN) {
					var index = Ints.fromByteArray(instruction.getData());
					jsonOp.put("parsedData", index);
				}

				ops.put(jsonOp);
			}

			respond(exchange, response);
		});
	}

}

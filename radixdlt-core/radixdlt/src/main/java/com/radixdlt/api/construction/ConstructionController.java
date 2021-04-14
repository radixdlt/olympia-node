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
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.store.CMStore;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.radix.api.http.Controller;

import java.util.Optional;

import static com.radixdlt.serialization.SerializationUtils.restore;
import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsync;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class ConstructionController implements Controller {
	private final ConstraintMachine constraintMachine;
	private final CMStore readLogStore;

	@Inject
	public ConstructionController(
		AtomIndex atomIndex,
		ConstraintMachine constraintMachine
	) {
		this.constraintMachine = constraintMachine;
		this.readLogStore = new CMStore() {
			@Override
			public Transaction createTransaction() {
				return null;
			}

			@Override
			public boolean isVirtualDown(Transaction dbTxn, SubstateId substateId) {
				return false;
			}

			@Override
			public Optional<Particle> loadUpParticle(Transaction dbTxn, SubstateId substateId) {
				var txnId = substateId.getTxnId();
				return atomIndex.get(txnId)
					.flatMap(txn ->
						restore(DefaultSerialization.getInstance(), txn.getPayload(), Atom.class)
							.map(a -> ConstraintMachine.toInstructions(a.getInstructions()))
							.map(i -> i.get(substateId.getIndex().orElseThrow()))
							.flatMap(i -> SubstateSerializer.deserializeToResult(i.getData()))
							.toOptional()
					);
			}
		};
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/node/parse", this::handleParse);
	}

	private REParsedTxn parseTxn(Txn txn) {
		try {
			return constraintMachine.validate(null, readLogStore, txn, PermissionLevel.SUPER_USER);
		} catch (RadixEngineException e) {
			throw new IllegalStateException(e);
		}
	}

	void handleParse(HttpServerExchange exchange) {
		withBodyAsync(exchange, values -> {
			var transactionHex = values.getString("transaction");
			var transactionBytes = Hex.decode(transactionHex);
			REParsedTxn parsedTxn = parseTxn(Txn.create(transactionBytes));
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

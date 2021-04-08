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

package org.radix.api.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.application.StakeReceived;
import com.radixdlt.application.StakedBalance;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.application.ValidatorInfo;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnNativeToken;
import com.radixdlt.atom.actions.RegisterAsValidator;
import com.radixdlt.atom.actions.StakeNativeToken;
import com.radixdlt.atom.actions.UnregisterAsValidator;
import com.radixdlt.atom.actions.UnstakeNativeToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.radix.api.http.RestUtils.*;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class NodeController implements Controller {
	private static final UInt256 FEE = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));
	private final RRI nativeToken;
	private final RadixAddress selfAddress;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;

	@Inject
	public NodeController(
		@NativeToken RRI nativeToken,
		@Self RadixAddress selfAddress,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher
	) {
		this.nativeToken = nativeToken;
		this.selfAddress = selfAddress;
		this.radixEngine = radixEngine;
		this.nodeApplicationRequestEventDispatcher = nodeApplicationRequestEventDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/node/execute", this::handleExecute);
		handler.get("/node", this::respondWithNode);
	}

	private JSONObject getValidator() {
		var stakeReceived = radixEngine.getComputedState(StakeReceived.class);
		var validatorInfo = radixEngine.getComputedState(ValidatorInfo.class);
		var stakeFrom = new JSONArray();
		stakeReceived.forEach((addr, amt) -> {
			stakeFrom.put(
				new JSONObject()
					.put("delegator", addr)
					.put("amount", TokenUnitConversions.subunitsToUnits(amt))
			);
		});
		return new JSONObject()
			.put("registered", validatorInfo.isRegistered())
			.put("totalStake", TokenUnitConversions.subunitsToUnits(stakeReceived.getTotal()))
			.put("stakes", stakeFrom);
	}

	private JSONObject getBalance() {
		var spendable = radixEngine.getComputedState(UInt256.class);
		var stakedBalance = radixEngine.getComputedState(StakedBalance.class);
		var stakeTo = new JSONArray();
		stakedBalance.forEach((addr, amt) ->
			stakeTo.put(
				new JSONObject()
					.put("delegate", addr)
					.put("amount", TokenUnitConversions.subunitsToUnits(amt))
			)
		);
		return new JSONObject()
			.put("spendable", TokenUnitConversions.subunitsToUnits(spendable))
			.put("staked", stakeTo);
	}

	@VisibleForTesting
	void respondWithNode(HttpServerExchange exchange) {
		respond(exchange, jsonObject()
			.put("address", selfAddress)
			.put("balance", getBalance())
			.put("validator", getValidator()));
	}

	private TxAction parseAction(JSONObject actionObject) throws IllegalArgumentException {
		var actionString = actionObject.getString("action");
		var paramsObject = actionObject.getJSONObject("params");
		switch (actionString) {
			case "StakeTokens": {
				var addressString = paramsObject.getString("to");
				var delegate = RadixAddress.from(addressString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new StakeNativeToken(nativeToken, delegate, subunits);
			}
			case "UnstakeTokens": {
				var addressString = paramsObject.getString("from");
				var delegate = RadixAddress.from(addressString);
				var amountBigInt = paramsObject.getBigInteger("amount");
				var subunits = TokenUnitConversions.unitsToSubunits(new BigDecimal(amountBigInt));
				return new UnstakeNativeToken(nativeToken, delegate, subunits);
			}
			case "RegisterAsValidator":
				return new RegisterAsValidator();
			case "UnregisterAsValidator":
				return new UnregisterAsValidator();
			default:
				throw new IllegalArgumentException("Bad action object: " + actionObject);
		}
	}

	void handleExecute(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBodyAsync(exchange, values -> {
			var actionsArray = values.getJSONArray("actions");
			var actions = new ArrayList<TxAction>();
			for (int i = 0; i < actionsArray.length(); i++) {
				var actionObject = actionsArray.getJSONObject(i);
				var txAction = parseAction(actionObject);
				actions.add(txAction);
			}
			actions.add(new BurnNativeToken(nativeToken, FEE));
			var request = NodeApplicationRequest.create(
				actions,
				aid -> respond(exchange, jsonObject().put("result", aid.toString())),
				(txn, error) -> respond(exchange, jsonObject().put("error", jsonObject()
					.put("message", error))
					.put("txn", txn == null ? null : Hex.toHexString(txn.getPayload()))
				)
			);

			nodeApplicationRequestEventDispatcher.dispatch(request);
		});
	}
}
